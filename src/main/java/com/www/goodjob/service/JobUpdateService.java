package com.www.goodjob.service;

import com.www.goodjob.domain.JobUpdateStatus;
import com.www.goodjob.repository.JobUpdateStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobUpdateService {

    private final JobUpdateStatusRepository jobUpdateStatusRepository;
    private final RecommendService recommendService;

    @Value("${FASTAPI_HOST}")
    private String fastapiHost;

    public void requestJobUpdate() {
        JobUpdateStatus status = new JobUpdateStatus();
        status.setRequestedAt(LocalDateTime.now());
        status.setStatus("IN_PROGRESS");
        jobUpdateStatusRepository.save(status);

        new Thread(() -> {
            try {
                log.info("[JobUpdate] >>> 첫 번째 recomputeAllRecommendationsForAllCvs() 시작");
                recommendService.recomputeAllRecommendationsForAllCvs();
                log.info("[JobUpdate] <<< 첫 번째 recomputeAllRecommendationsForAllCvs() 완료");

                RestTemplate restTemplate = new RestTemplate();
                String url = fastapiHost + "/save-es-jobs";
                log.info("[JobUpdate] ES 저장 요청 시작: {}", url);
                restTemplate.getForEntity(url, String.class);
                log.info("[JobUpdate] ES 저장 요청 완료");

                log.info("[JobUpdate] >>> 두 번째 recomputeAllRecommendationsForAllCvs() 시작");
                recommendService.recomputeAllRecommendationsForAllCvs();
                log.info("[JobUpdate] <<< 두 번째 recomputeAllRecommendationsForAllCvs() 완료");

                status.setStatus("COMPLETED");
            } catch (Exception e) {
                status.setStatus("FAILED");
                status.setErrorMessage(e.getMessage());
                log.error("[JobUpdate] 작업 실패: {}", e.getMessage(), e);
            }
            jobUpdateStatusRepository.save(status);
        }).start();
    }

    public JobUpdateStatus getLatestStatus() {
        return jobUpdateStatusRepository.findTopByOrderByRequestedAtDesc()
                .orElseThrow(() -> new RuntimeException("No job update has been requested yet."));
    }
}
