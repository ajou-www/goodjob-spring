package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmCreateRequest {

    /** 비관리자는 null 허용. 서버가 principal로 채움 */
    private Long userId;

    @NotBlank
    private String alarmText;

    @NotNull
    private AlarmType type;

    private String dedupeKey;

    @Builder.Default
    private AlarmStatus status = AlarmStatus.QUEUED;

    private LocalDateTime sentAt;

    @Valid
    private List<AlarmJobRequest> jobs;
}
