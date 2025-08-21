package com.www.goodjob.controller;

import com.www.goodjob.dto.alarm.AlarmResponse;
import com.www.goodjob.dto.alarm.AlarmUpdateRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[USER] Alarms", description = "사용자 전용 알림 API (본인 소유만 접근)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    @Operation(
            summary = "[USER] 알림 목록 조회(본인)",
            description = "본인 소유만 조회, 필터: unreadOnly, type; unreadOnly, type 없이 page, size만 넣고 조회 가능"
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public Page<AlarmResponse> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "읽지 않은 알림만 조회")
            @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "알림 타입 필터")
            @RequestParam(required = false) AlarmType type,
            @Parameter(description = "페이지 번호(0-base)")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기")
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return alarmService.getList(principal.getId(), false, unreadOnly, type, pageable);
    }

    @Operation(
            summary = "[USER] 알림 단건 조회(본인)",
            description = """
                    접근: USER/ADMIN 모두 가능하나 본인 소유만 조회됩니다.
                    
                    응답 필드 요약
                    - id: 알림 ID
                    - createdAt: 생성 시각(예: 2025-08-19T01:00:08)
                    - alarmText: 알림 메시지(백업용 타이틀) (ex. 오늘의 추천 공고 TOP 5 (90점 이상), 지원 마감 임박 1건 (D0:0, D1:1, D2:0) )
                    - read : 읽음 여부 (true/false)
                    - readAt : 읽은 시각
                    - type: CV_MATCH | APPLY_DUE | JOB_POPULAR
                    - sentAt: 발송 기준 시각
                    - userId / dedupeKey / status: 내부 확인용
                    - jobs[]: 관련 공고 목록 (rank 오름차순; jobId, rank, clickedAt 포함)
                    - titleCode: 예) CV_MATCH_TODAY | APPLY_DUE_SUMMARY | CV_MATCH_REALTIME
                    - params: 프론트 템플릿 바인딩용 변수 맵
                    
                    200 응답 예시 (CV_MATCH)
                    ```json
                    {
                      "id": 40,
                      "createdAt": "2025-08-20T16:00:15",
                      "alarmText": "오늘의 추천 공고 TOP 5 (90점 이상)",
                      "userId": 140,
                      "read": true,
                      "readAt": 2025-08-20 08:25:56,
                      "type": "CV_MATCH",
                      "dedupeKey": "CV_MATCH_TOPN:140:2025-08-21",
                      "status": "QUEUED",
                      "sentAt": "2025-08-21T01:00:06",
                      "jobs": [
                        { "jobId": 11460, "rank": 1, "clickedAt": null },
                        { "jobId": 7426,  "rank": 2, "clickedAt": null },
                        { "jobId": 3795,  "rank": 3, "clickedAt": null },
                        { "jobId": 6149,  "rank": 4, "clickedAt": null },
                        { "jobId": 5641,  "rank": 5, "clickedAt": null }
                      ],
                      "titleCode": "CV_MATCH_TODAY",
                      "params": { "topN": 5, "threshold": 90.0 }
                    }
                    ```
                    
                    200 응답 예시 (APPLY_DUE)
                    ```json
                    {
                      "id": 36,
                      "createdAt": "2025-08-20T16:00:08",
                      "alarmText": "지원 마감 임박 1건 (D0:0, D1:1, D2:0)",
                      "userId": 140,
                      "read": false,
                      "readAt": null,
                      "type": "APPLY_DUE",
                      "dedupeKey": "APPLY_DUE:140:2025-08-21",
                      "status": "QUEUED",
                      "sentAt": "2025-08-21T01:00:02",
                      "jobs": [
                        { "jobId": 17530, "rank": 1, "clickedAt": null }
                      ],
                      "titleCode": "APPLY_DUE_SUMMARY",
                      "params": { "total": 1, "d0": 0, "d1": 1, "d2": 0, "windowDays": 2 } // windowDays : 오늘(D0)부터 +windowDays까지를 스캔
                    }
                    ```
                    
                    에러
                    - 401: 인증 실패(토큰 누락/만료)
                    - 403: 권한 없음(본인 소유 아님)
                    - 404: 알림 없음
                    """
    )
    @GetMapping("/{alarmId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AlarmResponse getOne(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        return alarmService.getOne(principal.getId(), false, alarmId);
    }


    @Operation(
            summary = "[USER] 읽지 않은 알림 개수(본인)",
            description = "본인 소유 알림 중 읽지 않은 개수(Long) 반환"
    )
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public long unreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        return alarmService.countUnread(principal.getId());
    }

    @Operation(
            summary = "[USER] 알림 읽음 처리(단건, 본인)",
            description = "지정 알림을 읽음 처리(read=true, readAt 기록); 이미 읽음이어도 204 반환(멱등)"
    )
    @PatchMapping("/{alarmId}/read")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.markRead(principal.getId(), false, alarmId);
        return ResponseEntity.noContent().build();
    }

    @Operation(
            summary = "[USER] 모든 알림 읽음 처리(본인)",
            description = "본인 소유의 미읽음 알림을 일괄 읽음 처리하고 갱신된 개수(Long) 반환"
    )
    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Long> markAllRead(@AuthenticationPrincipal CustomUserDetails principal) {
        long updated = alarmService.markAllRead(principal.getId());
        return ResponseEntity.ok(updated);
    }

    @Operation(
            summary = "[USER] 알림 삭제(본인)",
            description = "지정 알림을 삭제; 본인 소유만 가능하며 성공 시 204 반환"
    )
    @DeleteMapping("/{alarmId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.delete(principal.getId(), false, alarmId);
        return ResponseEntity.noContent().build();
    }


    @Operation(summary = "[USER] 알림 내 공고 클릭 기록", description = "알림 상세의 특정 공고를 클릭하면 clickedAt을 기록합니다(멱등).")
    @PatchMapping("/{alarmId}/jobs/{jobId}/click")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> clickJob(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId,
            @Parameter(description = "공고 ID") @PathVariable Long jobId
    ) {
        alarmService.markJobClicked(principal.getId(), false, alarmId, jobId);
        return ResponseEntity.noContent().build();
    }

}
