package com.www.goodjob.repository;

import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.domain.alarm.AlarmJobId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional; // ← 추가

public interface AlarmJobRepository extends JpaRepository<AlarmJob, AlarmJobId> {

    List<AlarmJob> findByAlarmIdOrderByRankAsc(Long alarmId);

    void deleteByAlarmId(Long alarmId);

    // ← 추가: 알림 내 특정 공고 매핑 1건 조회
    Optional<AlarmJob> findByAlarmIdAndJobId(Long alarmId, Long jobId);
}
