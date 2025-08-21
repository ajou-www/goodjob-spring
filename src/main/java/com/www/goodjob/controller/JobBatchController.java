package com.www.goodjob.controller;

import com.www.goodjob.dto.JobDto;
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

    // 예) GET /jobs/_batch?ids=11460,7426,3795
    @Operation(
            summary = "[PUBLIC] 공고 배치 조회",
            description = """
                    ids로 전달된 jobId 목록을 JobDto로 일괄 반환. 전달 순서 유지.
                    
                    호출 흐름 (프론트)
                    - GET /alarms → 리스트 표시
                    - 항목 클릭 → GET /alarms/{alarmId} → jobs[].jobId 수집
                    - 배치: GET /jobs/_batch?ids=11460,7426,3795,6149,5641 → JobDto[] 렌더
                    """
    )
    @GetMapping("/_batch")
    public List<JobDto> batch(@RequestParam List<Long> ids) {
        return jobBatchService.getByIds(ids);
    }

//    // IDs가 길어질 가능성이 있으면 POST도 같이 열어두면 좋아요.
//    @Operation(summary = "[PUBLIC] 공고 배치 조회(POST)", description = "Body에 jobId 배열을 전달.")
//    @PostMapping("/_batch")
//    public List<JobDto> batchPost(@RequestBody List<Long> ids) {
//        return jobBatchService.getByIds(ids);
//    }
}
