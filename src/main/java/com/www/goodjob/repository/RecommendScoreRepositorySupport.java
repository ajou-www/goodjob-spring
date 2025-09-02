package com.www.goodjob.repository;

import com.www.goodjob.domain.RecommendScore;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** 사용자×CV별 상위 N개 추천 공고 조회 (MySQL 8 윈도우 함수) */
public interface RecommendScoreRepositorySupport extends Repository<RecommendScore, Long> {

    @Query(value = """
        WITH ranked AS (
          SELECT
            c.user_id    AS userId,
            rs.cv_id     AS cvId,
            rs.job_id    AS jobId,
            rs.score     AS score,
            j.title      AS title,
            j.company_name AS companyName,
            ROW_NUMBER() OVER (
              PARTITION BY c.user_id, rs.cv_id
              ORDER BY rs.score DESC, rs.created_at DESC, rs.job_id DESC
            ) AS rn
          FROM recommend_score rs
          JOIN cv   c ON c.id = rs.cv_id
          JOIN jobs j ON j.id = rs.job_id
          WHERE j.is_public = TRUE
        )
        SELECT userId, cvId, jobId, score, title, companyName
        FROM ranked
        WHERE rn <= :topN
        """, nativeQuery = true)
    List<RecommendScoreProjection> findTopNPerUserAndCv(@Param("topN") int topN);

    @Query(value = """
    WITH ranked AS (
      SELECT
        c.user_id       AS userId,
        rs.cv_id        AS cvId,
        rs.job_id       AS jobId,
        rs.score        AS score,
        j.title         AS title,
        j.company_name  AS companyName,
        ROW_NUMBER() OVER (
          PARTITION BY c.user_id, rs.cv_id
          ORDER BY rs.score DESC, rs.created_at DESC, rs.job_id DESC
        ) AS rn
      FROM recommend_score rs
      JOIN cv   c ON c.id = rs.cv_id
      JOIN jobs j ON j.id = rs.job_id
      WHERE j.is_public = TRUE
        AND j.created_at >= :since   -- ⬅️ 오늘 7시 이후 등록된 job만
    )
    SELECT userId, cvId, jobId, score, title, companyName
    FROM ranked
    WHERE rn <= :topN
    """, nativeQuery = true)
    List<RecommendScoreProjection> findTopNPerUserAndCvSince(
            @Param("topN") int topN,
            @Param("since") java.time.LocalDateTime since
    );

}
