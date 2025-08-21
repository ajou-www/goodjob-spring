package com.www.goodjob.service;

import com.www.goodjob.dto.JobDto;
import com.www.goodjob.repository.JobBatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobBatchService {

    private final JobBatchRepository jobRepo;

    @Transactional(readOnly = true)
    public List<JobDto> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        var order = new HashMap<Long,Integer>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);

        // 필요 시 fetch-join 버전으로 교체:
        var jobs = jobRepo.findByIdIn(ids);
        return jobs.stream()
                .map(JobDto::from)
                .sorted(Comparator.comparingInt(d -> order.getOrDefault(d.getId(), Integer.MAX_VALUE)))
                .collect(Collectors.toList());
    }
}
