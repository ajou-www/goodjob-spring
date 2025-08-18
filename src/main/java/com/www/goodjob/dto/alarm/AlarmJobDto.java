package com.www.goodjob.dto.alarm;

import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmJobDto {
    private Long jobId;
    private Integer rank;
    private LocalDateTime clickedAt;
}
