package com.www.goodjob.service;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.dto.alarm.*;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.AlarmJobRepository;
import com.www.goodjob.repository.AlarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final AlarmJobRepository alarmJobRepository;

    /* CREATE */
    @Transactional
    public AlarmResponse create(Long actorUserId, boolean isAdmin, AlarmCreateRequest req) {
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

    /* READ list */
    @Transactional(readOnly = true)
    public Page<AlarmResponse> getList(Long actorUserId, boolean isAdmin, Long userId,
                                       Boolean unreadOnly, AlarmType type, Pageable pageable) {
        Long targetUserId = (userId != null ? userId : actorUserId);
        if (!isAdmin && !targetUserId.equals(actorUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "권한 없음");
        }

        Page<Alarm> page;
        if (unreadOnly != null && type != null) {
            page = alarmRepository.findByUserIdAndReadAndType(targetUserId, false, type, pageable);
        } else if (unreadOnly != null) {
            page = alarmRepository.findByUserIdAndRead(targetUserId, false, pageable);
        } else if (type != null) {
            page = alarmRepository.findByUserIdAndType(targetUserId, type, pageable);
        } else {
            page = alarmRepository.findByUserId(targetUserId, pageable);
        }
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long actorUserId) {
        return alarmRepository.countByUserIdAndReadFalse(actorUserId);
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
        var page = alarmRepository.findByUserIdAndRead(actorUserId, false, Pageable.ofSize(500));
        long updated = 0;
        while (!page.isEmpty()) {
            for (Alarm a : page.getContent()) {
                a.markReadNow();
                updated++;
            }
            if (page.hasNext()) {
                page = alarmRepository.findByUserIdAndRead(actorUserId, false, page.nextPageable());
            } else break;
        }
        return updated;
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
        // alarm_job은 FK on delete cascade 이므로 자동 정리
    }

    /* DTO 변환: N+1 회피 위해 job은 Repo로 한 번에 로딩 */
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
                .jobs(jobs)
                .build();
    }
}
