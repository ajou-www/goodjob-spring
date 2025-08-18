// src/main/java/com/www/goodjob/repository/CvIdUserIdProjectionRepo.java
package com.www.goodjob.repository;

import com.www.goodjob.domain.Cv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CvIdUserIdProjectionRepo extends JpaRepository<Cv, Long> {

    interface CvIdUserIdProjection {
        Long getCvId();
        Long getUserId();
    }

    @Query(value = """
        SELECT c.id      AS cvId,
               c.user_id AS userId
        FROM cv c
        """, nativeQuery = true)
    List<CvIdUserIdProjection> findAllCvIdUserId();
}
