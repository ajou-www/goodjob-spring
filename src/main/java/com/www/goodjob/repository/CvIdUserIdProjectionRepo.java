package com.www.goodjob.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

/**
 * CV id와 user id를 한 번에 뽑아오는 투영용 레포.
 * 엔티티 매핑과 무관하게 네이티브로 가져와 안전하게 사용.
 */
public interface CvIdUserIdProjectionRepo extends Repository<Object, Long> {

    interface CvIdUserIdProjection {
        Long getCvId();
        Long getUserId();
    }

    @Query(value = """
        SELECT c.id       AS cvId,
               c.user_id  AS userId
        FROM cv c
    """, nativeQuery = true)
    List<CvIdUserIdProjection> findAllCvIdUserId();
}
