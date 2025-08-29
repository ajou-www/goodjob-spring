package com.www.goodjob.service;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.dto.JobDto;                         // ⬅️ 추가
import com.www.goodjob.dto.ScoredJobDto;
import com.www.goodjob.dto.alarm.*;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.AlarmJobRepository;
import com.www.goodjob.repository.AlarmRepository;
import com.www.goodjob.repository.JobBatchRepository;       // ⬅️ 추가
import com.www.goodjob.repository.RecommendScoreRepository; // ⬅️ 추가
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;                                         // ⬅️ 추가(Map, List, HashMap, ArrayList 등)
import java.util.stream.Collectors;                         // ⬅️ 추가

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmJobRepository alarmJobRepository;

    // ⬇️ 점수/공고 조회에 필요한 의존성 추가
    private final JobBatchRepository jobRepo;
    private final RecommendScoreRepository recommendScoreRepository;

    /* CREATE */
    @Transactional
    public AlarmResponse create(Long actorUserId, boolean isAdmin, AlarmCreateRequest req) {
        // ADMIN은 req.userId로 타 사용자 생성 가능, USER는 본인만
        Long targetUserId = (req.getUserId() != null ? req.getUserId() : actorUserId);
        if (!isAdmin && !targetUserId.equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자에게 알림 생성 불가");
        }

        if (req.getDedupeKey() != null && !req.getDedupeKey().isBlank()
                && alarmRepository.existsByDedupeKey(req.getDedupeKey())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 dedupe_key");
        }

        Alarm alarm = Alarm.builder()
                .userId(targetUserId)
                .alarmText(req.getAlarmText())
                .type(req.getType())
                .dedupeKey(req.getDedupeKey())
                .status(req.getStatus() == null ? AlarmStatus.QUEUED : req.getStatus())
                .sentAt(req.getSentAt())
                .read(false)
                // ✅ A안: CV 스냅샷 저장
                .cvId(req.getCvId())
                .cvTitle(req.getCvTitle())
                // ✅ 제목 코드/파라미터도 함께 저장
                .titleCode(req.getTitleCode())
                .payload(req.getParams())
                .build();

        Alarm saved = alarmRepository.save(alarm);

        if (req.getJobs() != null && !req.getJobs().isEmpty()) {
            req.getJobs().stream()
                    .sorted(Comparator.comparingInt(AlarmJobRequest::getRank))
                    .forEach(j -> alarmJobRepository.save(AlarmJob.of(saved.getId(), j.getJobId(), j.getRank())));
        }

        return toResponse(saved);
    }

    /* READ single */
    @Transactional(readOnly = true)
    public AlarmResponse getOne(Long actorUserId, boolean isAdmin, Long alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }
        return toResponse(alarm);
    }

    /* READ list (USER: 본인만 / ADMIN: 전체) */
    @Transactional(readOnly = true)
    public Page<AlarmResponse> getList(Long actorUserId, boolean isAdmin,
                                       Boolean unreadOnly, AlarmType type, Pageable pageable) {
        Page<Alarm> page;
        if (isAdmin) {
            // ADMIN: 전체 범위
            if (Boolean.TRUE.equals(unreadOnly) && type != null) {
                page = alarmRepository.findByReadAndType(false, type, pageable);
            } else if (Boolean.TRUE.equals(unreadOnly)) {
                page = alarmRepository.findByRead(false, pageable);
            } else if (type != null) {
                page = alarmRepository.findByType(type, pageable);
            } else {
                page = alarmRepository.findAll(pageable);
            }
        } else {
            // USER: 본인 소유만
            page = findPageForUser(actorUserId, unreadOnly, type, pageable);
        }
        return page.map(this::toResponse);
    }

    /* READ list (ADMIN: 특정 사용자 스코프 조회) */
    @Transactional(readOnly = true)
    public Page<AlarmResponse> getListForUser(Long targetUserId, boolean isAdmin,
                                              Boolean unreadOnly, AlarmType type, Pageable pageable) {
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자 전용 기능");
        }
        Page<Alarm> page = findPageForUser(targetUserId, unreadOnly, type, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long actorUserId) {
        return alarmRepository.countByUserIdAndReadFalse(actorUserId);
    }

    /* ADMIN: 타 사용자 읽지 않은 개수 */
    @Transactional(readOnly = true)
    public long countUnreadByAdmin(Long targetUserId) {
        return alarmRepository.countByUserIdAndReadFalse(targetUserId);
    }

    /* UPDATE (부분/전체) */
    @Transactional
    public AlarmResponse update(Long actorUserId, boolean isAdmin, Long alarmId, AlarmUpdateRequest req) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }

        if (req.getDedupeKey() != null && !req.getDedupeKey().equals(alarm.getDedupeKey())) {
            if (req.getDedupeKey().isBlank()) {
                alarm.setDedupeKey(null);
            } else if (alarmRepository.existsByDedupeKey(req.getDedupeKey())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 존재하는 dedupe_key");
            } else {
                alarm.setDedupeKey(req.getDedupeKey());
            }
        }

        if (req.getAlarmText() != null) alarm.setAlarmText(req.getAlarmText());
        if (req.getType() != null) alarm.setType(req.getType());
        if (req.getStatus() != null) alarm.setStatus(req.getStatus());
        if (req.getSentAt() != null) alarm.setSentAt(req.getSentAt());
        if (req.getTitleCode() != null) alarm.setTitleCode(req.getTitleCode()); // ⬅️ 추가
        if (req.getParams() != null) alarm.setPayload(req.getParams());         // ⬅️ 추가
        if (req.getCvId() != null)   alarm.setCvId(req.getCvId());              // ⬅️ 추가
        if (req.getCvTitle() != null) alarm.setCvTitle(req.getCvTitle());       // ⬅️ 추가

        if (req.getJobs() != null) {
            alarmJobRepository.deleteByAlarmId(alarmId);
            req.getJobs().stream()
                    .sorted(Comparator.comparingInt(AlarmJobRequest::getRank))
                    .forEach(j -> alarmJobRepository.save(AlarmJob.of(alarmId, j.getJobId(), j.getRank())));
        }

        return toResponse(alarm);
    }

    /* PATCH - single read */
    @Transactional
    public void markRead(Long actorUserId, boolean isAdmin, Long alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }
        alarm.markReadNow();
    }

    /* PATCH - read-all (my alarms) */
    @Transactional
    public long markAllRead(Long actorUserId) {
        return markAllReadInternal(actorUserId);
    }

    /* ADMIN: read-all (target user) */
    @Transactional
    public long markAllReadByAdmin(Long targetUserId) {
        return markAllReadInternal(targetUserId);
    }

    /* DELETE */
    @Transactional
    public void delete(Long actorUserId, boolean isAdmin, Long alarmId) {
        Alarm alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }
        alarmRepository.delete(alarm);
        // alarm_job은 FK on delete cascade
    }

    /* ===== Private Helpers ===== */

    private Page<Alarm> findPageForUser(Long userId, Boolean unreadOnly, AlarmType type, Pageable pageable) {
        if (Boolean.TRUE.equals(unreadOnly) && type != null) {
            return alarmRepository.findByUserIdAndReadAndType(userId, false, type, pageable);
        } else if (Boolean.TRUE.equals(unreadOnly)) {
            return alarmRepository.findByUserIdAndRead(userId, false, pageable);
        } else if (type != null) {
            return alarmRepository.findByUserIdAndType(userId, type, pageable);
        } else {
            return alarmRepository.findByUserId(userId, pageable);
        }
    }

    @Transactional
    protected long markAllReadInternal(Long userId) {
        var page = alarmRepository.findByUserIdAndRead(userId, false, Pageable.ofSize(500));
        long updated = 0;
        while (!page.isEmpty()) {
            for (Alarm a : page.getContent()) {
                a.markReadNow();
                updated++;
            }
            if (page.hasNext()) {
                page = alarmRepository.findByUserIdAndRead(userId, false, page.nextPageable());
            } else break;
        }
        return updated;
    }

    @Transactional
    public void markJobClicked(Long actorUserId, boolean isAdmin, Long alarmId, Long jobId) {
        var alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }

        var alarmJob = alarmJobRepository.findByAlarmIdAndJobId(alarmId, jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림-공고 매핑 없음"));

        if (alarmJob.getClickedAt() == null) {
            alarmJob.setClickedAt(LocalDateTime.now());
        }

        // 선택 정책: 공고 클릭하면 알림도 읽음 처리하고 싶다면 함께 처리
        if (!alarm.isRead()) {
            alarm.markReadNow();
        }
    }

    /* DTO 변환 */
    private AlarmResponse toResponse(Alarm alarm) {
        var jobs = alarmJobRepository.findByAlarmIdOrderByRankAsc(alarm.getId())
                .stream()
                .map(j -> AlarmJobDto.builder()
                        .jobId(j.getJobId())
                        .rank(j.getRank())
                        .clickedAt(j.getClickedAt())
                        .build())
                .toList();

        return AlarmResponse.builder()
                .id(alarm.getId())
                .createdAt(alarm.getCreatedAt())
                .alarmText(alarm.getAlarmText())
                .userId(alarm.getUserId())
                .read(alarm.isRead())
                .readAt(alarm.getReadAt())
                .type(alarm.getType())
                .dedupeKey(alarm.getDedupeKey())
                .status(alarm.getStatus())
                .sentAt(alarm.getSentAt())
                // ✅ CV 스냅샷
                .cvId(alarm.getCvId())
                .cvTitle(alarm.getCvTitle())
                .titleCode(alarm.getTitleCode())
                .params(alarm.getPayload())
                .jobs(jobs)
                .build();
    }

    /* CV_MATCH면 점수 포함, 그 외는 0.0 */
    @Transactional(readOnly = true)
    public List<ScoredJobDto> getJobsWithOptionalScores(Long actorUserId, boolean isAdmin, Long alarmId) {
        var alarm = alarmRepository.findById(alarmId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "알림 없음"));
        if (!isAdmin && !alarm.getUserId().equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }

        // rank 순서로 jobIds
        List<AlarmJob> rels = alarmJobRepository.findByAlarmIdOrderByRankAsc(alarmId);
        if (rels.isEmpty()) return List.of();
        List<Long> jobIds = rels.stream().map(AlarmJob::getJobId).toList();

        // 공고 조회 → DTO
        var jobs = jobRepo.findByIdIn(jobIds);
        Map<Long, JobDto> jobDtoMap = jobs.stream()
                .map(JobDto::from)
                .collect(Collectors.toMap(JobDto::getId, j -> j));

        // 점수: CV_MATCH && cvId 존재 시에만 조회
        Map<Long, Double> scoreMap = new HashMap<>();
        if (alarm.getType() == AlarmType.CV_MATCH && alarm.getCvId() != null) {
            var rows = recommendScoreRepository.findByCvIdAndJobIdIn(alarm.getCvId(), jobIds);
            for (var r : rows) scoreMap.put(r.getJob().getId(), (double) r.getScore());
        }

        // rank 순서 유지 + 점수 결합 (cosine/bm25는 0.0)
        List<ScoredJobDto> out = new ArrayList<>(jobIds.size());
        for (Long jobId : jobIds) {
            var base = jobDtoMap.get(jobId);
            if (base == null) continue; // 비공개/삭제 등 스킵
            double score = scoreMap.getOrDefault(jobId, 0.0);
            out.add(ScoredJobDto.from(base, score, 0.0, 0.0));
        }
        return out;
    }
}
