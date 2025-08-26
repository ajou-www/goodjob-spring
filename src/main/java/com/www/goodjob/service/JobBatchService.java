package com.www.goodjob.service;

import com.www.goodjob.dto.JobDto;
import com.www.goodjob.dto.ScoredJobDto;
import com.www.goodjob.repository.JobBatchRepository;
import com.www.goodjob.repository.RecommendScoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobBatchService {

    private final JobBatchRepository jobRepo;
    private final RecommendScoreRepository recommendScoreRepository;

    @Transactional(readOnly = true)
    public List<ScoredJobDto> getScoredByIds(Long cvId, List<Long> ids) { // cvId nullable
        if (ids == null || ids.isEmpty()) return List.of();

        // 요청 순서 보존
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);

        // 공고 조회 → 기본 DTO 맵
        var jobs = jobRepo.findByIdIn(ids);
        Map<Long, JobDto> jobDtoMap = jobs.stream()
                .map(JobDto::from)
                .collect(Collectors.toMap(JobDto::getId, j -> j));

        // 점수 조회: cvId가 있을 때만 recommend_score 조회
        Map<Long, Double> scoreMap = new HashMap<>();
        if (cvId != null) {
            var rows = recommendScoreRepository.findByCvIdAndJobIdIn(cvId, ids);
            for (var r : rows) {
                scoreMap.put(r.getJob().getId(), (double) r.getScore());
            }
        }

        // 순서 유지하여 ScoredJobDto 생성 (cvId 없거나 점수 없으면 0.0)
        return ids.stream()
                .map(jobDtoMap::get)
                .filter(Objects::nonNull)
                .map(base -> ScoredJobDto.from(
                        base,
                        scoreMap.getOrDefault(base.getId(), 0.0),
                        0.0,
                        0.0
                ))
                .sorted(Comparator.comparingInt(d -> order.getOrDefault(d.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    // 필요 시: 기존 JobDto 반환 메서드는 유지/정리 선택
}
