package com.www.goodjob.repository;

import com.www.goodjob.domain.Job;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface JobBatchRepository extends JpaRepository<Job, Long> {

    // 기본 버전
    List<Job> findByIdIn(Collection<Long> ids);

    // (선택) regions N+1 예방: 매핑이 있다면 fetch join 버전 사용
    @Query("""
      select distinct j
      from Job j
      left join fetch j.jobRegions jr
      left join fetch jr.region r
      where j.id in :ids
    """)
    List<Job> findWithRegionsByIdIn(@Param("ids") Collection<Long> ids);
}
