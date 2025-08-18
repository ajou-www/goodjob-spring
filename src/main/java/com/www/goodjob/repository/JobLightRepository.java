// src/main/java/com/www/goodjob/repository/JobLightRepository.java
package com.www.goodjob.repository;

import com.www.goodjob.domain.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobLightRepository extends JpaRepository<Job, Long> {

    @Query(value = """
        SELECT j.id
        FROM jobs j
        WHERE j.is_public = TRUE
          AND j.created_at > :since
        """, nativeQuery = true)
    List<Long> findNewJobIdsAfter(@Param("since") LocalDateTime since);
}
