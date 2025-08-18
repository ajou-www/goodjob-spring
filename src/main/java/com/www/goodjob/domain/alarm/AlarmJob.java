package com.www.goodjob.domain.alarm;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(
        name = "alarm_job",
        indexes = {
                @Index(name = "idx_alarm_job_rank", columnList = "alarm_id, `rank`"),
                @Index(name = "idx_alarm_job_job", columnList = "job_id")
        }
)
@IdClass(AlarmJobId.class)
public class AlarmJob {

    @Id
    @Column(name = "alarm_id", nullable = false)
    private Long alarmId;

    @Id
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    /** 조회 편의용(읽기 전용 연관) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_id", insertable = false, updatable = false)
    private Alarm alarm;

    public static AlarmJob of(Long alarmId, Long jobId, int rank) {
        return AlarmJob.builder()
                .alarmId(alarmId)
                .jobId(jobId)
                .rank(rank)
                .build();
    }
}
