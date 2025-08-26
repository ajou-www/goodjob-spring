package com.www.goodjob.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 사용자별 상위 N개 추천 공고를 간단 조회 (MySQL8 윈도우 함수) */
public interface RecommendScoreRepositorySupport extends Repository<com.www.goodjob.domain.alarm.Alarm, Long> {

    @Query(value = """
        WITH ranked AS (
          SELECT rs.cv_id, rs.job_id, rs.score,
                 c.user_id AS userId, j.title AS title, j.company_name AS companyName,
                 ROW_NUMBER() OVER (PARTITION BY c.user_id ORDER BY rs.score DESC) AS rn
          FROM recommend_score rs
          JOIN cv c ON c.id = rs.cv_id
          JOIN jobs j ON j.id = rs.job_id
          WHERE j.is_public = TRUE
        )
        SELECT userId AS userId, job_id AS jobId, score AS score,
               title AS title, companyName AS companyName
        FROM ranked
        WHERE rn <= :topN
    """, nativeQuery = true)
    List<RecommendScoreProjection> findTopNPerUserAndCv(@Param("topN") int topN);
}
