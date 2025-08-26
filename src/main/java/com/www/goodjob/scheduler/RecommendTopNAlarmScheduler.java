package com.www.goodjob.scheduler;

import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.CvRepository;
import com.www.goodjob.repository.RecommendScoreProjection;
import com.www.goodjob.repository.RecommendScoreRepositorySupport;
import com.www.goodjob.service.AlarmCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * recommend_score 테이블이 이미 업데이트되어 있다는 가정 하에,
 * 사용자별 TOP N을 골라 알림 1건 + AlarmJob N개로 생성.
 */
// RecommendTopNAlarmScheduler.java (핵심만 발췌)
@RequiredArgsConstructor
@Component
@Slf4j
public class RecommendTopNAlarmScheduler {

    private static final int    TOP_N     = 5;
    private static final double THRESHOLD = 10.0;
    private static final ZoneId ZONE      = ZoneId.of("Asia/Seoul");

    private final RecommendScoreRepositorySupport rsRepo;
    private final AlarmCommandService alarmCommandService;
    private final CvRepository cvRepository;

    @Scheduled(cron = "0 10 12 * * *", zone = "Asia/Seoul")
    public void run() {
        // (userId, cvId) 단위로 TOP N 가져오는 쿼리(지원 코드)는 프로젝트 구현에 맞춰 사용
        List<RecommendScoreProjection> list = rsRepo.findTopNPerUserAndCv(TOP_N);

        Map<UserCvKey, List<RecommendScoreProjection>> byUserCv = list.stream()
                .filter(r -> r.getScore() != null && r.getScore() >= THRESHOLD)
                .collect(Collectors.groupingBy(r -> new UserCvKey(r.getUserId(), r.getCvId())));

        LocalDate today = LocalDate.now(ZONE);
        LocalDateTime now = LocalDateTime.now(ZONE);
        int generated = 0;

        for (var entry : byUserCv.entrySet()) {
            Long userId = entry.getKey().userId();
            Long cvId   = entry.getKey().cvId();

            // CV 이름(= file_name) 조회 → 알림에 저장할 스냅샷
            String cvTitle = cvRepository.findFileNameById(cvId).orElse("내 이력서"); // 확실하지 않음: 기본값 정책은 팀 합의

            var items = entry.getValue().stream()
                    .sorted(Comparator.comparingDouble(RecommendScoreProjection::getScore).reversed())
                    .limit(TOP_N)
                    .toList();
            if (items.isEmpty()) continue;

            // dedupe 키에 cvId 포함
            String dedupeKey = "CV_MATCH_TOPN:%d:%d:%s".formatted(userId, cvId, today);

            // 제목에 CV 이름 포함
            String text = (THRESHOLD > 0)
                    ? "‘%s’에 대한 오늘의 추천 공고 TOP %d (%.0f점 이상)".formatted(cvTitle, items.size(), THRESHOLD)
                    : "‘%s’에 대한 오늘의 추천 공고 TOP %d".formatted(cvTitle, items.size());

            var jobs = new ArrayList<AlarmJobRequest>();
            int rank = 1;
            for (var r : items) jobs.add(new AlarmJobRequest(r.getJobId(), rank++));

            String titleCode = "CV_MATCH_TODAY";
            Map<String,Object> params = new HashMap<>();
            params.put("topN", items.size());
            params.put("threshold", THRESHOLD);
            params.put("cvId", cvId);
            params.put("cvTitle", cvTitle);

            var alarm = alarmCommandService.createIfNotExists(
                    userId,
                    text,
                    AlarmType.CV_MATCH,
                    dedupeKey,
                    now,
                    jobs,
                    titleCode,
                    params,
                    cvId,
                    cvTitle
            );
            if (alarm != null) generated++;
        }

        log.info("[RECO_TOPN] pairs={} generated={}", byUserCv.size(), generated);
    }

    private record UserCvKey(Long userId, Long cvId) {}
}

