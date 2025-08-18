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

    /** 관리자가 다른 사용자에게 알림 생성할 때 사용. 일반 사용자는 null 허용 → 서버가 principal로 채움 */
    private Long userId;

    @NotBlank
    private String alarmText;

    @NotNull
    private AlarmType type;

    /** 전역 dedupe. 주어지면 중복 차단 */
    private String dedupeKey;

    /** 기본값 QUEUED */
    @Builder.Default
    private AlarmStatus status = AlarmStatus.QUEUED;

    private LocalDateTime sentAt;

    @Valid
    private List<AlarmJobRequest> jobs;
}
