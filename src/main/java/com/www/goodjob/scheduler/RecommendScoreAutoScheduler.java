package com.www.goodjob.scheduler;

import com.www.goodjob.dto.ScoredJobDto;
import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.CvIdUserIdProjectionRepo;
import com.www.goodjob.repository.JobLightRepository;
import com.www.goodjob.service.AlarmCommandService;
import com.www.goodjob.service.RecommendService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Objects;

/**
 * 신규 공고만 대상으로, CV별 추천을 즉시 조회(캐시/FASTAPI),
 * 90점 이상인 것만 알림으로 생성.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendScoreAutoScheduler {

    private static final String CURSOR_KEY = "cursor:recommend:lastRunAt"; // Redis 키
    private static final ZoneId  KST       = ZoneId.of("Asia/Seoul");

    private static final int    TOP_K     = 50;    // CV별 상위 K개만 조회하여 필터
    private static final double THRESHOLD = 90.0;  // 알림 임계치

    private final StringRedisTemplate redis;
    private final JobLightRepository jobRepo;
    private final CvIdUserIdProjectionRepo cvRepo;
    private final RecommendService recommendService;
    private final AlarmCommandService alarmCommandService;

    /**
     * 매 15분마다 실행 (초 분 시 일 월 요일)
     */
    @Scheduled(cron = "0 */15 * * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDateTime now   = LocalDateTime.now(KST);
        LocalDateTime since = readCursorOrDefault(now.minusHours(1)); // 기본 1시간 전

        // 1) 신규 공고 id 수집
        List<Long> newJobIds = jobRepo.findNewJobIdsAfter(since);
        if (newJobIds.isEmpty()) {
            writeCursor(now);
            log.info("[AUTO-RECO-90] no new jobs since {}", since);
            return;
        }
        var newJobIdSet = new HashSet<>(newJobIds);

        // 2) 모든 CV(id->userId) 목록
        var cvPairs = cvRepo.findAllCvIdUserId();
        if (cvPairs.isEmpty()) {
            writeCursor(now);
            log.info("[AUTO-RECO-90] no CVs to evaluate");
            return;
        }

        int usersNotified = 0;
        // 3) 각 CV → 추천 가져와 신규 공고 & 90점↑만 필터
        for (var cv : cvPairs) {
            Long cvId   = cv.getCvId();
            Long userId = cv.getUserId();

            List<ScoredJobDto> recs;
            try {
                recs = recommendService.requestRecommendation(cvId, TOP_K); // 캐시→FASTAPI
            } catch (Exception e) {
                log.warn("[AUTO-RECO-90] recommendation failed for cvId={}", cvId, e);
                continue;
            }

            var hits = recs.stream()
                    .filter((ScoredJobDto r) -> newJobIdSet.contains(r.getId()))
                    .filter((ScoredJobDto r) -> Double.compare(
                            Objects.requireNonNullElse(r.getScore(), Double.NEGATIVE_INFINITY),
                            THRESHOLD
                    ) >= 0)
                    .sorted(Comparator.comparingDouble(
                            (ScoredJobDto r) -> Objects.requireNonNullElse(r.getScore(), Double.NEGATIVE_INFINITY)
                    ).reversed())
                    .limit(10)
                    .toList();

            if (hits.isEmpty()) continue;

            // dedupe: 사용자+분단위로 1회
            String dedupeKey = "CV_MATCH_SCORE90_NEW:%d:%s"
                    .formatted(userId, now.withSecond(0).withNano(0));

            String text = "새로 등록된 추천 공고 %d건 (90점↑)".formatted(hits.size());

            var jobs = new ArrayList<AlarmJobRequest>();
            int rank = 1;
            for (var h : hits) {
                jobs.add(new AlarmJobRequest(h.getId(), rank++));
            }

            var alarm = alarmCommandService.createIfNotExists(
                    userId, text, AlarmType.CV_MATCH, dedupeKey, now, jobs
            );
            if (alarm != null) usersNotified++;
        }

        writeCursor(now);
        log.info("[AUTO-RECO-90] newJobs={} usersNotified={} window=[{} ~ {}]",
                newJobIds.size(), usersNotified, since, now);
    }

    private LocalDateTime readCursorOrDefault(LocalDateTime fallback) {
        try {
            String v = redis.opsForValue().get(CURSOR_KEY);
            if (v == null || v.isBlank()) return fallback;
            return LocalDateTime.parse(v);
        } catch (Exception e) {
            return fallback;
        }
    }

    private void writeCursor(LocalDateTime ts) {
        try {
            redis.opsForValue().set(CURSOR_KEY, ts.toString());
        } catch (Exception ignored) {}
    }
}
