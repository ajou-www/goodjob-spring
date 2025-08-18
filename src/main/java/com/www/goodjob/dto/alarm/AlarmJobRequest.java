package com.www.goodjob.dto.alarm;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class AlarmJobRequest {
    @NotNull
    private Long jobId;

    @NotNull @Min(1)
    private Integer rank;
}
