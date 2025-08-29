package com.www.goodjob.service;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.AlarmJobRepository;
import com.www.goodjob.repository.AlarmRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    /** CV 비의존 알림(APPLY_DUE 등) 오버로드 */
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
                null, null
        );
    }

    /**
     * 유니크 키(dedupeKey) 기반 idempotent 생성.
     * 중복이면 null 반환. REQUIRES_NEW + saveAndFlush로 rollback-only 전파 차단.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Alarm createIfNotExists(Long userId, String text, AlarmType type,
                                   String dedupeKey, LocalDateTime sentAt,
                                   List<AlarmJobRequest> jobs,
                                   String titleCode, Map<String,Object> params,
                                   Long cvId, String cvTitle) {

        if (text == null || text.isBlank()) throw new IllegalArgumentException("alarmText must not be blank");
        if (type == null) throw new IllegalArgumentException("type must not be null");

        String effectiveDedupe = (dedupeKey != null && !dedupeKey.isBlank())
                ? dedupeKey
                : ("AUTO:%d:%s:%s".formatted(
                userId, type.name(),
                Optional.ofNullable(sentAt).orElse(LocalDateTime.now()).withSecond(0).withNano(0)
        ));

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
            // 여기서 즉시 제약 위반 발생 → 아래 catch로 진입
            Alarm saved = alarmRepository.saveAndFlush(toSave);
            persistJobs(saved.getId(), jobs);
            return saved;
        } catch (DataIntegrityViolationException | ConstraintViolationException dup) {
            // uk_alarm_dedupe 충돌 → 새로 만들지 않음
            return null;
            // 필요 시 기존 알림 반환:
            // return alarmRepository.findByDedupeKey(effectiveDedupe).orElse(null);
        }
    }

    /** jobs 정리 후 batch 저장 */
    private void persistJobs(Long alarmId, List<AlarmJobRequest> jobs) {
        if (jobs == null || jobs.isEmpty()) return;

        List<AlarmJobRequest> cleaned = jobs.stream()
                .filter(Objects::nonNull)
                .filter(j -> j.getJobId() != null && j.getRank() != null)
                .toList();
        if (cleaned.isEmpty()) return;

        Map<Long, AlarmJobRequest> byJob = cleaned.stream().collect(
                Collectors.toMap(
                        AlarmJobRequest::getJobId,
                        Function.identity(),
                        (a, b) -> a.getRank() <= b.getRank() ? a : b,
                        LinkedHashMap::new
                )
        );

        List<AlarmJobRequest> sorted = byJob.values().stream()
                .sorted(Comparator.comparingInt(AlarmJobRequest::getRank))
                .toList();

        List<AlarmJob> entities = sorted.stream()
                .map(j -> AlarmJob.of(alarmId, j.getJobId(), j.getRank()))
                .toList();

        alarmJobRepository.saveAll(entities);
    }
}
