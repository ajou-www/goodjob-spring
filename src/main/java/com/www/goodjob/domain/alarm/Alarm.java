package com.www.goodjob.domain.alarm;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@Entity
@Table(
        name = "alarm",
        indexes = {
                @Index(name = "idx_alarm_user_created", columnList = "user_id, created_at"),
                @Index(name = "idx_alarm_user_unread", columnList = "user_id, is_read")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_alarm_dedupe", columnNames = "dedupe_key")
        }
)
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

    @Column(name = "cv_id")
    private Long cvId;            // FK: cv.id (nullable; APPLY_DUE 등은 null)

    @Column(name = "cv_title")
    private String cvTitle;       // 스냅샷(삭제/변경 대비)

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

    /** 읽음 처리 간단 헬퍼 */
    public void markReadNow() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    @Column(name = "title_code")
    private String titleCode;

    @Convert(converter = JsonMapConverter.class)
    @Column(name = "payload_json", columnDefinition = "TEXT")
    private Map<String, Object> payload;

    @Converter(autoApply = false)
    public static class JsonMapConverter implements AttributeConverter<Map<String,Object>, String> {
        private static final ObjectMapper OM = new ObjectMapper();

        @Override
        public String convertToDatabaseColumn(Map<String,Object> attribute) {
            try { return attribute == null ? null : OM.writeValueAsString(attribute); }
            catch (Exception e) { throw new IllegalArgumentException(e); }
        }

        @Override
        public Map<String,Object> convertToEntityAttribute(String dbData) {
            try {
                return dbData == null ? null : OM.readValue(dbData, new TypeReference<Map<String,Object>>(){});
            } catch (Exception e) { throw new IllegalArgumentException(e); }
        }
    }
}
