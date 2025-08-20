package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmResponse {
    private Long id;
    private LocalDateTime createdAt;
    private String alarmText;
    private Long userId;
    private boolean read;
    private LocalDateTime readAt;
    private AlarmType type;
    private String dedupeKey;
    private AlarmStatus status;
    private LocalDateTime sentAt;

    private List<AlarmJobDto> jobs;

    String titleCode;                 // 예: "APPLY_DUE_SUMMARY", "CV_MATCH_TOPN", "CV_MATCH_REALTIME_90"
    Map<String, Object> params;       // 예: { "total": 1, "d0": 0, "d1": 1, "d2": 0 }
}
