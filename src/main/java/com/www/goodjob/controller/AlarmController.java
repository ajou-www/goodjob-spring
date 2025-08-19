package com.www.goodjob.controller;

import com.www.goodjob.dto.alarm.AlarmCreateRequest;
import com.www.goodjob.dto.alarm.AlarmResponse;
import com.www.goodjob.dto.alarm.AlarmUpdateRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.service.AlarmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
            summary = "[USER] 알림 생성(본인)",
            description = """
            - 본인 소유 알림만 생성
            - ADMIN은 관리자 전용 API(/admin/alarms)를 사용하세요
            """
    )
    @PostMapping
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public ResponseEntity<AlarmResponse> create(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody AlarmCreateRequest req
    ) {
        // 서비스단에서 req.userId가 있어도 무시하고 principal 기준으로 강제
        var res = alarmService.create(principal.getId(), false, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

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

    @Operation(summary = "[USER] 알림 단건 조회(본인)")
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

    @Operation(summary = "[USER] 알림 수정(본인)")
    @PutMapping("/{alarmId}")
    @PreAuthorize("hasAnyRole('USER','ADMIN')")
    public AlarmResponse update(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId,
            @Valid @RequestBody AlarmUpdateRequest req
    ) {
        return alarmService.update(principal.getId(), false, alarmId, req);
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
