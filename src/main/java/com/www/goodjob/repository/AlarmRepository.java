package com.www.goodjob.repository;

import com.www.goodjob.domain.alarm.Alarm;
import com.www.goodjob.enums.AlarmType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AlarmRepository extends JpaRepository<Alarm, Long> {

    Page<Alarm> findByUserId(Long userId, Pageable pageable);

    Page<Alarm> findByUserIdAndRead(Long userId, boolean read, Pageable pageable);

    Page<Alarm> findByUserIdAndType(Long userId, AlarmType type, Pageable pageable);

    Page<Alarm> findByUserIdAndReadAndType(Long userId, boolean read, AlarmType type, Pageable pageable);

    Optional<Alarm> findByIdAndUserId(Long id, Long userId);

    boolean existsByDedupeKey(String dedupeKey);

    long countByUserIdAndReadFalse(Long userId);
}
