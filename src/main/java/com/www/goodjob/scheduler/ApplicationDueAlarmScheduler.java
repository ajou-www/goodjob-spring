package com.www.goodjob.scheduler;

import com.www.goodjob.dto.alarm.AlarmJobRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.repository.ApplicationDueProjection;
import com.www.goodjob.repository.ApplicationDueRepository;
import com.www.goodjob.service.AlarmCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationDueAlarmScheduler {

    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    // 검색 윈도우: 오늘(D0) ~ D+2
    private static final int DUE_START_OFFSET = 0;
    private static final int DUE_END_OFFSET   = 2;

    // 사용자별 알림에 담을 최대 개수
    private static final int MAX_ITEMS_PER_USER = 10;

    private final ApplicationDueRepository repo;
    private final AlarmCommandService alarmCommandService;

    /** 매일 10:00 KST */
    @Scheduled(cron = "0 0 18 * * *", zone = "Asia/Seoul")
    public void run() {
        LocalDate today = LocalDate.now(ZONE);
        LocalDate start = today.plusDays(DUE_START_OFFSET);
        LocalDate end   = today.plusDays(DUE_END_OFFSET);

        List<ApplicationDueProjection> rows = repo.findApplicationDuesBetween(start, end);
        if (rows.isEmpty()) {
            log.info("[APPLY_DUE] no application deadlines in [{} ~ {}]", start, end);
            return;
        }

        Map<Long, List<ApplicationDueProjection>> byUser =
                rows.stream().collect(Collectors.groupingBy(ApplicationDueProjection::getUserId));

        int generated = 0;
        LocalDateTime now = LocalDateTime.now(ZONE);

        for (var entry : byUser.entrySet()) {
            Long userId = entry.getKey();

            // 정렬: D0 → D1 → D2, 동일일자면 회사명/제목 보조정렬
            List<ApplicationDueProjection> sorted = entry.getValue().stream()
                    .sorted(Comparator
                            .comparingInt((ApplicationDueProjection p) -> dday(today, p.getApplyEndDate()))
                            .thenComparing(ApplicationDueProjection::getApplyEndDate)
                            .thenComparing(ApplicationDueProjection::getCompanyName, Comparator.nullsLast(String::compareTo))
                            .thenComparing(ApplicationDueProjection::getTitle, Comparator.nullsLast(String::compareTo)))
                    .limit(MAX_ITEMS_PER_USER)
                    .toList();

            if (sorted.isEmpty()) continue;

            long d0 = sorted.stream().filter(p -> dday(today, p.getApplyEndDate()) == 0).count();
            long d1 = sorted.stream().filter(p -> dday(today, p.getApplyEndDate()) == 1).count();
            long d2 = sorted.stream().filter(p -> dday(today, p.getApplyEndDate()) == 2).count();

            String text = buildTitle(d0, d1, d2); // 예: "지원 마감 임박 6건 (D0:2, D1:3, D2:1)"

            List<AlarmJobRequest> jobs = new ArrayList<>();
            int rank = 1;
            for (var p : sorted) {
                jobs.add(new AlarmJobRequest(p.getJobId(), rank++));
            }

            String dedupeKey = "APPLY_DUE:%d:%s".formatted(userId, today);

            long total = d0 + d1 + d2;
            String titleCode = "APPLY_DUE_SUMMARY";
            Map<String,Object> params = new HashMap<>();
            params.put("total", total);
            params.put("d0", d0);
            params.put("d1", d1);
            params.put("d2", d2);
            params.put("windowDays", DUE_END_OFFSET); // 선택

            var alarm = alarmCommandService.createIfNotExists(
                    userId,
                    /* alarmText(백업용) */ "지원 마감 임박 %d건 (D0:%d, D1:%d, D2:%d)".formatted(total,d0,d1,d2),
                    AlarmType.APPLY_DUE,
                    dedupeKey, now, jobs,
                    titleCode, params
            );

            if (alarm != null) generated++;
        }

        log.info("[APPLY_DUE] users={} generated={} window=[{} ~ {}]",
                byUser.size(), generated, start, end);
    }

    private static int dday(LocalDate today, LocalDate due) {
        return (int) Duration.between(today.atStartOfDay(), due.atStartOfDay()).toDays();
    }

    private static String buildTitle(long d0, long d1, long d2) {
        long total = d0 + d1 + d2;
        return "지원 마감 임박 %d건 (D0:%d, D1:%d, D2:%d)".formatted(total, d0, d1, d2);
    }
}
