package com.www.goodjob.domain.alarm;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "alarm_job",
        indexes = {
                @Index(name = "idx_alarm_job_rank", columnList = "alarm_id, `rank`"),
                @Index(name = "idx_alarm_job_job", columnList = "job_id")
        })
public class AlarmJob {

    @EmbeddedId
    private AlarmJobId id;

    @MapsId("alarmId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alarm_id", nullable = false)
    private Alarm alarm;

    /** Job 엔터티는 의존하지 않고 ID만 보관합니다 */
    @Column(name = "job_id", nullable = false, insertable = false, updatable = false)
    private Long jobId;

    /** MySQL에서 예약어 가능성 → 백틱 이스케이프 */
    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(name = "clicked_at")
    private LocalDateTime clickedAt;

    public static AlarmJob of(Long jobId, int rank) {
        AlarmJob aj = new AlarmJob();
        aj.setId(new AlarmJobId(null, jobId)); // alarmId는 persist 시점에 주입
        aj.setJobId(jobId);
        aj.setRank(rank);
        return aj;
    }
}
