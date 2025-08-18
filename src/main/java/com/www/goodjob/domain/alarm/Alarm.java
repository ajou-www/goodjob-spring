package com.www.goodjob.domain.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter @Setter
@Builder
@NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "alarm",
        indexes = {
                @Index(name = "idx_alarm_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_alarm_user_unread", columnList = "user_id, is_read")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alarm_dedupe", columnNames = "dedupe_key")
        })
public class Alarm {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "alarm_text", nullable = false, columnDefinition = "TEXT")
    private String alarmText;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AlarmType type;

    @Column(name = "dedupe_key", length = 255)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AlarmStatus status;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "alarm", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rank ASC")
    private List<AlarmJob> jobs = new ArrayList<>();

    /** 편의 메서드 */
    public void replaceJobs(List<AlarmJob> newJobs) {
        this.jobs.clear();
        if (newJobs != null) {
            newJobs.forEach(j -> j.setAlarm(this));
            this.jobs.addAll(newJobs);
        }
    }

    public void markReadNow() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }
}
