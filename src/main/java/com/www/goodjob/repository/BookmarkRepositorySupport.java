package com.www.goodjob.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/** 북마크-잡 조인 결과 투영 */
public interface BookmarkRepositorySupport extends Repository<Object, Long> {

    @Query(value = """
        SELECT b.user_id        AS userId,
               b.job_id         AS jobId,
               j.apply_end_date AS applyEndDate,
               j.title          AS title,
               j.company_name   AS companyName
        FROM bookmarks b
        JOIN jobs j ON j.id = b.job_id
        WHERE j.is_public = TRUE
          AND j.apply_end_date BETWEEN :fromDate AND :toDate
        """,
            nativeQuery = true)
    List<BookmarkDeadlineProjection> findBookmarkJobsClosingBetween(
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate")   LocalDate toDate
    );
}
