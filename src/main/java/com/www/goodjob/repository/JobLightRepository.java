package com.www.goodjob.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 엔티티 매핑을 타지 않는 경량 네이티브 쿼리 레포.
 * created_at 이후 생성된 공개 공고의 id만 조회.
 */
public interface JobLightRepository extends Repository<Object, Long> {

    @Query(value = """
        SELECT j.id
        FROM jobs j
        WHERE j.is_public = TRUE
          AND j.created_at > :since
    """, nativeQuery = true)
    List<Long> findNewJobIdsAfter(@Param("since") LocalDateTime since);
}
