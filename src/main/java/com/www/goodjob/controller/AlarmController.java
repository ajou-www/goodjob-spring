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
            description = "- 본인 소유만 조회, 필터: unreadOnly, type"
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
    - 접근: USER/ADMIN 모두 호출 가능하지만 **본인 소유만** 조회됩니다.
      (관리자가 타 사용자 알림을 조회하려면 /admin/alarms/{alarmId} 사용 권장)
    - 반환: AlarmResponse (알림 본문 + 관련 Job 목록)
    - 필드 설명:
      • id: 알림 ID
      • createdAt: 알림 레코드 생성 시각 (DB 기준) (ex. 매일 오전 10시)
      • alarmText: 알림 타이틀/메시지
      • userId: 수신 사용자 ID (USER 화면에선 숨겨도 무방)
      • read: 읽음 여부
      • readAt: 읽음 처리 시각
      • type: 알림 타입 (CV_MATCH | APPLY_DUE | JOB_POPULAR)
      • dedupeKey: 중복 방지 키 (동일 일자/타입 중복 발송 방지, 내부용)
      • status: 전송 상태(예: QUEUED) - 내부 모니터링용
      • sentAt: 알림 발송 기준 시각
      • jobs[]: 알림과 연계된 공고 목록 (rank 오름차순)
        - jobId: 공고 ID
        - rank: 노출 순서(1부터)
        - clickedAt: 사용자가 알림에서 해당 공고를 클릭한 시각 (없으면 null)
    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "성공",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = AlarmResponse.class),
                                    examples = @ExampleObject(
                                            name = "CV_MATCH 예시",
                                            value = """
                    {
                      "id": 5,
                      "createdAt": "2025-08-19T01:00:08",
                      "alarmText": "오늘의 추천 공고 TOP 5",
                      "userId": 140,
                      "read": false,
                      "readAt": null,
                      "type": "CV_MATCH",
                      "dedupeKey": "CV_MATCH_TOPN:140:2025-08-19",
                      "status": "QUEUED",
                      "sentAt": "2025-08-19T10:00:03",
                      "jobs": [
                        { "jobId": 11460, "rank": 1, "clickedAt": null },
                        { "jobId": 7426,  "rank": 2, "clickedAt": null },
                        { "jobId": 3795,  "rank": 3, "clickedAt": null },
                        { "jobId": 6149,  "rank": 4, "clickedAt": null },
                        { "jobId": 5641,  "rank": 5, "clickedAt": null }
                      ]
                    }
                    """
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "403", description = "권한 없음(본인 소유가 아님)"),
                    @ApiResponse(responseCode = "404", description = "해당 ID의 알림 없음"),
                    @ApiResponse(responseCode = "401", description = "인증 실패(토큰 누락/만료)")
            }
    )
    @GetMapping("/{alarmId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AlarmResponse getOne(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        return alarmService.getOne(principal.getId(), false, alarmId);
    }

    @Operation(summary = "[USER] 읽지 않은 알림 개수(본인)")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public long unreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        // 본인 기준 카운트
        return alarmService.countUnread(principal.getId());
    }

    @Operation(summary = "[USER] 알림 읽음 처리(단건, 본인)")
    @PatchMapping("/{alarmId}/read")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> markRead(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.markRead(principal.getId(), false, alarmId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "[USER] 모든 알림 읽음 처리(본인)")
    @PatchMapping("/read-all")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Long> markAllRead(@AuthenticationPrincipal CustomUserDetails principal) {
        long updated = alarmService.markAllRead(principal.getId());
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "[USER] 알림 삭제(본인)")
    @DeleteMapping("/{alarmId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.delete(principal.getId(), false, alarmId);
        return ResponseEntity.noContent().build();
    }
}
