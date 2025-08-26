package com.www.goodjob.service;

import com.www.goodjob.dto.JobDto;
import com.www.goodjob.dto.ScoredJobDto;
import com.www.goodjob.repository.JobBatchRepository;
import com.www.goodjob.repository.RecommendScoreRepository;   // ⬅️ 추가
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobBatchService {

    private final JobBatchRepository jobRepo;
    private final RecommendScoreRepository recommendScoreRepository; // ⬅️ 추가

    @Transactional(readOnly = true)
    public List<ScoredJobDto> getScoredByIds(Long cvId, List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        // 요청 순서 보존용 인덱스 맵
        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);

        // 공고 조회 → 기본 JobDto 매핑
        var jobs = jobRepo.findByIdIn(ids);
        Map<Long, JobDto> jobDtoMap = jobs.stream()
                .map(JobDto::from)
                .collect(Collectors.toMap(JobDto::getId, j -> j));

        // 점수 조회 (recommend_score)
        var rows = recommendScoreRepository.findByCvIdAndJobIdIn(cvId, ids);
        Map<Long, Double> scoreMap = new HashMap<>();
        for (var r : rows) {
            scoreMap.put(r.getJob().getId(), (double) r.getScore());
        }

        // 전달 순서 유지하여 ScoredJobDto 생성 (cosine/bm25는 없으니 0.0)
        return ids.stream()
                .map(jobDtoMap::get)
                .filter(Objects::nonNull) // 삭제/비공개 등으로 누락된 공고 스킵
                .map(base -> ScoredJobDto.from(
                        base,
                        scoreMap.getOrDefault(base.getId(), 0.0),
                        0.0,
                        0.0
                ))
                .sorted(Comparator.comparingInt(d -> order.getOrDefault(d.getId(), Integer.MAX_VALUE)))
                .toList();
    }

    // (선택) 기존 JobDto 반환 메서드는 필요 없으면 정리
    @Transactional(readOnly = true)
    public List<JobDto> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();

        Map<Long, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) order.put(ids.get(i), i);

        var jobs = jobRepo.findByIdIn(ids);
        return jobs.stream()
                .map(JobDto::from)
                .sorted(Comparator.comparingInt(d -> order.getOrDefault(d.getId(), Integer.MAX_VALUE)))
                .toList();
    }
}
