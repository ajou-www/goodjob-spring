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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

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

    private final PlatformTransactionManager txManager; // ⬅️ 반드시 주입

    /** REQUIRES_NEW 트랜잭션 템플릿 */
    private TransactionTemplate newTx() {
        TransactionTemplate tpl = new TransactionTemplate(txManager);
        tpl.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tpl;
    }

    /** APPLY_DUE 등 CV 비의존 알림 생성 (중복 dedupeKey → 409) */
    public Alarm createOrThrowDuplicateSimple(
            Long userId,
            String text,
            AlarmType type,
            String dedupeKey,
            LocalDateTime sentAt,
            List<AlarmJobRequest> jobs,
            String titleCode,
            Map<String, Object> params
    ) {
        return newTx().execute(status -> {
            try {
                Alarm toSave = baseAlarm(userId, text, type, dedupeKey, sentAt, titleCode, params, null, null);
                Alarm saved = alarmRepository.saveAndFlush(toSave); // 제약 즉시 검사
                persistJobs(saved.getId(), jobs);
                return saved;
            } catch (DataIntegrityViolationException e) {
                status.setRollbackOnly(); // 내부 tx만 롤백
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate dedupeKey: " + dedupeKey);
            }
        });
    }

    /** CV_MATCH 등 CV 의존 알림 생성 (중복 dedupeKey → 409) */
    public Alarm createOrThrowDuplicateWithCv(
            Long userId,
            String text,
            AlarmType type,
            String dedupeKey,
            LocalDateTime sentAt,
            List<AlarmJobRequest> jobs,
            String titleCode,
            Map<String, Object> params,
            Long cvId,
            String cvTitle
    ) {
        return newTx().execute(status -> {
            try {
                Alarm toSave = baseAlarm(userId, text, type, dedupeKey, sentAt, titleCode, params, cvId, cvTitle);
                Alarm saved = alarmRepository.saveAndFlush(toSave);
                persistJobs(saved.getId(), jobs);
                return saved;
            } catch (DataIntegrityViolationException e) {
                status.setRollbackOnly();
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Duplicate dedupeKey: " + dedupeKey);
            }
        });
    }

    /* ================= Helpers ================= */

    private static Alarm baseAlarm(
            Long userId,
            String text,
            AlarmType type,
            String dedupeKey,
            LocalDateTime sentAt,
            String titleCode,
            Map<String, Object> params,
            Long cvId,
            String cvTitle
    ) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("alarmText must not be blank");
        if (type == null) throw new IllegalArgumentException("type must not be null");
        if (dedupeKey == null || dedupeKey.isBlank()) throw new IllegalArgumentException("dedupeKey must not be blank");

        return Alarm.builder()
                .userId(userId)
                .alarmText(text)
                .type(type)
                .dedupeKey(dedupeKey)
                .status(AlarmStatus.QUEUED)
                .sentAt(sentAt != null ? sentAt : LocalDateTime.now())
                .read(false)
                .titleCode(titleCode)
                .payload(params)
                .cvId(cvId)
                .cvTitle(cvTitle)
                .build();
    }

}
