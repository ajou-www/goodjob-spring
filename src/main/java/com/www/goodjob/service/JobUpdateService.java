package com.www.goodjob.service;

import com.www.goodjob.domain.JobUpdateStatus;
import com.www.goodjob.repository.JobUpdateStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;

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
                RestTemplate restTemplate = new RestTemplate();
                String url = fastapiHost + "/save-es-jobs";
                restTemplate.getForEntity(url, String.class);

                // ES 저장 끝난 뒤 전체 CV에 대해 topK=10으로 재계산
                recommendService.recomputeAllRecommendationsForAllCvs(10);

                status.setStatus("COMPLETED");
            } catch (Exception e) {
                status.setStatus("FAILED");
                status.setErrorMessage(e.getMessage());
            }
            jobUpdateStatusRepository.save(status);
        }).start();
    }

    public JobUpdateStatus getLatestStatus() {
        return jobUpdateStatusRepository.findTopByOrderByRequestedAtDesc()
                .orElseThrow(() -> new RuntimeException("No job update has been requested yet."));
    }
}
