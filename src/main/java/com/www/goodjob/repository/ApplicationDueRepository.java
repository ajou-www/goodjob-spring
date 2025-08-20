package com.www.goodjob.repository;

import com.www.goodjob.domain.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ApplicationDueRepository extends JpaRepository<Application, Long> {

    @Query(value = """
        SELECT 
            a.user_id        AS userId,
            a.job_id         AS jobId,
            a.apply_due_date AS applyEndDate,
            j.title          AS title,
            j.company_name   AS companyName
        FROM applications a
        JOIN jobs j ON j.id = a.job_id
        WHERE a.apply_due_date IS NOT NULL
          AND a.apply_due_date BETWEEN :start AND :end
          /* 필요시 상태 필터:
             AND a.apply_status IN ('준비중','지원','서류전형','코테','면접')
          */
        """, nativeQuery = true)
    List<ApplicationDueProjection> findApplicationDuesBetween(
            @Param("start") LocalDate start,
            @Param("end")   LocalDate end
    );
}