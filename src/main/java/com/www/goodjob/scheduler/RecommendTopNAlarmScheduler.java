package com.www.goodjob.scheduler;

import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmType;
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
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendTopNAlarmScheduler {

    private static final int    TOP_N     = 5;     // 사용자별 알림에 담을 개수
    private static final double THRESHOLD = 0.0;   // 하한선 (필요시 90.0 등으로)

    private final RecommendScoreRepositorySupport rsRepo;
    private final AlarmCommandService alarmCommandService;

    /** 매일 10:00 KST */
    @Scheduled(cron = "0 30 10 * * *", zone = "Asia/Seoul")
    public void run() {
        List<RecommendScoreProjection> list = rsRepo.findTopNPerUser(TOP_N);

        Map<Long, List<RecommendScoreProjection>> byUser =
                list.stream()
                        .filter(r -> r.getScore() != null && r.getScore() >= THRESHOLD)
                        .collect(Collectors.groupingBy(RecommendScoreProjection::getUserId));

        int generated = 0;
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

        for (var e : byUser.entrySet()) {
            Long userId = e.getKey();
            var items = e.getValue()
                    .stream()
                    .sorted(Comparator.comparingDouble(RecommendScoreProjection::getScore).reversed())
                    .limit(TOP_N)
                    .toList();

            // dedupe: 날짜 단위 1회
            String key = "CV_MATCH_TOPN:%d:%s".formatted(userId, now.toLocalDate());
            String text = (THRESHOLD > 0)
                    ? "오늘의 추천 공고 TOP %d (%.0f점 이상)".formatted(items.size(), THRESHOLD)
                    : "오늘의 추천 공고 TOP %d".formatted(items.size());

            var jobs = new ArrayList<AlarmJobRequest>();
            int rank = 1;
            for (var r : items) {
                jobs.add(new AlarmJobRequest(r.getJobId(), rank++));
            }

            String titleCode = "CV_MATCH_TODAY";
            Map<String,Object> params = new HashMap<>();
            params.put("topN", items.size());
            params.put("threshold", THRESHOLD);

            var alarm = alarmCommandService.createIfNotExists(
                    userId,
                    text,                   // "오늘의 추천 공고 TOP "
                    AlarmType.CV_MATCH,
                    key, now, jobs,
                    titleCode, params
            );

            if (alarm != null) generated++;
        }

        log.info("[RECO_TOPN] users={} generated={}", byUser.size(), generated);
    }
}
