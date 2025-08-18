package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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
}
