package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.validation.Valid;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmUpdateRequest {
    private String alarmText;
    private AlarmType type;
    private String dedupeKey;
    private AlarmStatus status;
    private LocalDateTime sentAt;

    /** 주어지면 기존 매핑 전체 교체 */
    @Valid
    private List<AlarmJobRequest> jobs;
}
