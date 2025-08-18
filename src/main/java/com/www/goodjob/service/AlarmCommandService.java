package com.www.goodjob.service;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.AlarmJobRepository;
import com.www.goodjob.repository.AlarmRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlarmCommandService {

    private final AlarmRepository alarmRepository;
    private final AlarmJobRepository alarmJobRepository;

    /**
     * dedupeKey로 중복 방지하며 알림을 생성하고 관련 job 매핑을 저장한다.
     * jobs는 rank 오름차순으로 저장됨.
     */
    @Transactional
    public Alarm createIfNotExists(Long userId, String text, AlarmType type,
                                   String dedupeKey, LocalDateTime sentAt,
                                   List<AlarmJobRequest> jobs) {
        if (dedupeKey != null && !dedupeKey.isBlank()
                && alarmRepository.existsByDedupeKey(dedupeKey)) {
            return null; // 이미 같은 알림이 있다면 생성 스킵
        }

        Alarm alarm = Alarm.builder()
                .userId(userId)
                .alarmText(text)
                .type(type)
                .dedupeKey(dedupeKey)
                .status(AlarmStatus.QUEUED)
                .sentAt(sentAt)
                .read(false)
                .build();
        Alarm saved = alarmRepository.save(alarm);

        if (jobs != null && !jobs.isEmpty()) {
            jobs.stream()
                    .sorted(Comparator.comparingInt(AlarmJobRequest::getRank))
                    .forEach(j -> alarmJobRepository.save(
                            AlarmJob.of(saved.getId(), j.getJobId(), j.getRank())
                    ));
        }
        return saved;
    }
}
