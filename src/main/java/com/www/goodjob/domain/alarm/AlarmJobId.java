package com.www.goodjob.domain.alarm;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Embeddable
public class AlarmJobId implements Serializable {
    private Long alarmId;
    private Long jobId;
}
