package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.validation.Valid;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;   // ⬅️ 추가

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmUpdateRequest {

    private String alarmText;
    private AlarmType type;
    private String dedupeKey;
    private AlarmStatus status;
    private LocalDateTime sentAt;

    /* ===== A안: CV 컨텍스트 스냅샷 (옵션) ===== */
    private Long cvId;       // CV_MATCH 알림일 때 권장
    private String cvTitle;  // CV 파일명 스냅샷(예: "프론트")

    /* ===== 템플릿 메타 (옵션) ===== */
    private String titleCode;               // 예: CV_MATCH_TODAY | APPLY_DUE_SUMMARY
    private Map<String, Object> params;     // 프론트 템플릿 바인딩 변수

    /** 주어지면 기존 매핑 전체 교체 */
    @Valid
    private List<AlarmJobRequest> jobs;
}
