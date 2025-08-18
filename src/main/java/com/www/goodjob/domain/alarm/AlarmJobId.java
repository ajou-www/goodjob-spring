package com.www.goodjob.domain.alarm;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
public class AlarmJobId implements Serializable {
    private Long alarmId;
    private Long jobId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AlarmJobId that)) return false;
        return Objects.equals(alarmId, that.alarmId) &&
                Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alarmId, jobId);
    }
}
