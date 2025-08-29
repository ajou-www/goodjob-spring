package com.www.goodjob.dto.alarm;

import com.www.goodjob.enums.AlarmStatus;
import com.www.goodjob.enums.AlarmType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlarmCreateRequest {

    /** 비관리자는 null 허용. 서버가 principal로 채움 */
    private Long userId;

    @NotBlank
    private String alarmText;

    @NotNull
    private AlarmType type;

    /** 중복 방지 키(없으면 서버에서 생성 가능) */
    private String dedupeKey;

    @Builder.Default
    private AlarmStatus status = AlarmStatus.QUEUED;

    /** 발송 기준 시각(없으면 now) */
    private LocalDateTime sentAt;

    /** 알림 내 공고 목록 (rank 오름차순 렌더) */
    @Valid
    private List<AlarmJobRequest> jobs;

    /* ===== A안: CV 컨텍스트 스냅샷 (옵션) ===== */
    /** CV_MATCH일 때 권장: 매칭 기준 CV ID */
    private Long cvId;

    /** CV 파일명 스냅샷(예: "프론트"); 비었으면 서버에서 CV.file_name으로 보완 가능 */
    private String cvTitle;

    /* ===== 템플릿 메타 ===== */
    /** 예: CV_MATCH_TODAY | APPLY_DUE_SUMMARY | CV_MATCH_REALTIME */
    private String titleCode;

    /** 프론트 템플릿 바인딩용 변수 맵 */
    private Map<String, Object> params;
}
