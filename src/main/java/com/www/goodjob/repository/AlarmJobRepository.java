package com.www.goodjob.repository;

import com.www.goodjob.domain.alarm.AlarmJob;
import com.www.goodjob.domain.alarm.AlarmJobId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmJobRepository extends JpaRepository<AlarmJob, AlarmJobId> {
    List<AlarmJob> findByAlarmIdOrderByRankAsc(Long alarmId);
    void deleteByAlarmId(Long alarmId);
}
