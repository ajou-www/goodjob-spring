package com.www.goodjob.controller;

import com.www.goodjob.dto.ScoredJobDto;           // ⬅️ 추가
import com.www.goodjob.service.JobBatchService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/jobs")
public class JobBatchController {

    private final JobBatchService jobBatchService;

    // 예) GET /jobs/_batch?ids=11460,7426,3795&cvId=123
    @Operation(
            summary = "[PUBLIC] 공고 배치 조회 (Scored)",
            description = """
                    ids로 전달된 jobId 목록을 ScoredJobDto로 일괄 반환. 전달 순서 유지.
                    점수는 recommend_score(cvId, jobId)의 score를 사용하며, 없으면 0.0으로 반환.
                    
                    - CV_MATCH : cvId가 있으면 recommend_score(cvId, jobId)의 score를 사용
                    - APPLY_DUE : cvId가 없으면 score=0.0으로 반환 (cvId = required false)
                    
                    호출 흐름 (프론트)
                    - GET /alarms → 리스트 표시
                    - 항목 클릭 → GET /alarms/{alarmId} → jobs[].jobId, cvId 수집
                    - 배치: GET /jobs/_batch?ids=11460,7426,3795,6149,5641&cvId=123 → ScoredJobDto[] 렌더
                    """
    )
    @GetMapping("/_batch")
    public List<ScoredJobDto> batch(
            @RequestParam List<Long> ids,
            @RequestParam(required = false) Long cvId // 옵션
    ) {
        return jobBatchService.getScoredByIds(cvId, ids);
    }

}
