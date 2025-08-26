package com.www.goodjob.service;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.AlarmJobRepository;
import com.www.goodjob.repository.AlarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AlarmCommandService {

    private final AlarmRepository alarmRepository;
    private final AlarmJobRepository alarmJobRepository;

    /**
     * CV 비의존 알림(APPLY_DUE 등)에서 사용하는 오버로드.
     * 내부 확장 버전으로 위임하여 로직을 한곳에 유지.
     */
    @Transactional
    public Alarm createIfNotExists(Long userId,
                                   String text,
                                   AlarmType type,
                                   String dedupeKey,
                                   LocalDateTime sentAt,
                                   List<AlarmJobRequest> jobs,
                                   String titleCode,
                                   Map<String, Object> params) {
        return createIfNotExists(
                userId, text, type, dedupeKey, sentAt, jobs, titleCode, params,
                null, // cvId 없음
                null  // cvTitle 없음
        );
    }

    /**
     * dedupeKey 유니크 제약을 활용해 중복을 원자적으로 방지하며 알림을 생성.
     * - 중복이면 null 반환(현 정책 유지)
     * - jobs는 rank 오름차순/중복 제거 후 batch 저장
     */
    @Transactional
    public Alarm createIfNotExists(Long userId, String text, AlarmType type,
                                   String dedupeKey, LocalDateTime sentAt,
                                   List<AlarmJobRequest> jobs,
                                   String titleCode, Map<String,Object> params, Long cvId, String cvTitle) {

        // 기본값 방어
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("alarmText must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        // dedupeKey가 비어있으면 스케줄러단에서 생성해 오는 게 베스트.
        // 그래도 안전망으로 비어있을 시 유저/타입/시각 기반 임시 키를 생성해 충돌 최소화.
        String effectiveDedupe = (dedupeKey != null && !dedupeKey.isBlank())
                ? dedupeKey
                : ("AUTO:%d:%s:%s".formatted(userId, type.name(),
                Optional.ofNullable(sentAt).orElse(LocalDateTime.now()).withSecond(0).withNano(0)));

        Alarm toSave = Alarm.builder()
                .userId(userId)
                .alarmText(text)
                .type(type)
                .dedupeKey(effectiveDedupe)
                .status(AlarmStatus.QUEUED)
                .sentAt(sentAt != null ? sentAt : LocalDateTime.now())
                .read(false)
                .titleCode(titleCode)
                .payload(params)
                .cvId(cvId)
                .cvTitle(cvTitle)
                .build();

        try {
            Alarm saved = alarmRepository.save(toSave);
            persistJobs(saved.getId(), jobs);
            return saved;
        } catch (DataIntegrityViolationException dup) {
            // 다른 트랜잭션에서 먼저 삽입됨(uk_alarm_dedupe 충돌)
            // 정책: "중복이면 스킵" → null 반환 (스케줄러가 generated++ 하지 않도록)
            // 만약 기존 엔티티를 반환하고 싶다면 아래를 사용:
            // return alarmRepository.findByDedupeKey(effectiveDedupe).orElse(null);
            return null;
        }
    }

    /** jobs 정리(중복 jobId/랭크, null 제거) 후 batch 저장 */
    private void persistJobs(Long alarmId, List<AlarmJobRequest> jobs) {
        if (jobs == null || jobs.isEmpty()) return;

        // 1) null 제거
        List<AlarmJobRequest> cleaned = jobs.stream()
                .filter(Objects::nonNull)
                .filter(j -> j.getJobId() != null && j.getRank() != null)
                .toList();

        if (cleaned.isEmpty()) return;

        // 2) 같은 jobId가 여러 번 온 경우: 더 낮은 rank(우선순위 높은)만 남김
        Map<Long, AlarmJobRequest> byJob =
                cleaned.stream()
                        .collect(Collectors.toMap(
                                AlarmJobRequest::getJobId,
                                Function.identity(),
                                (a, b) -> a.getRank() <= b.getRank() ? a : b,
                                LinkedHashMap::new));

        // 3) rank 기준 정렬
        List<AlarmJobRequest> sorted = byJob.values().stream()
                .sorted(Comparator.comparingInt(AlarmJobRequest::getRank))
                .toList();

        // 4) 엔티티 변환 후 batch 저장
        List<AlarmJob> entities = sorted.stream()
                .map(j -> AlarmJob.of(alarmId, j.getJobId(), j.getRank()))
                .toList();

        alarmJobRepository.saveAll(entities);
    }
}
