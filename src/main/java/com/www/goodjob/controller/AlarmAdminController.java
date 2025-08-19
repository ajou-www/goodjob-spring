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
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "[ADMIN] Alarms", description = "관리자 전용 알림 API (타 사용자 대상)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/alarms")
public class AlarmAdminController {

    private final AlarmService alarmService;

    @Operation(
            summary = "[ADMIN] 알림 생성(타 사용자)",
            description = "- 요청 본문에 포함된 userId 기준으로 생성"
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlarmResponse> adminCreate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody AlarmCreateRequest req
    ) {
        var res = alarmService.create(principal.getId(), true, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @Operation(
            summary = "[ADMIN] 알림 목록 조회(타 사용자)",
            description = "- userId로 타 사용자 대상 조회\n- 필터: unreadOnly, type"
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AlarmResponse> adminList(
            @Parameter(description = "대상 사용자 ID (필수)")
            @RequestParam Long userId,
            @Parameter(description = "읽지 않은 알림만")
            @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "알림 타입")
            @RequestParam(required = false) AlarmType type,
            @Parameter(description = "페이지 번호(0-base)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        // admin은 타 사용자로 scope 전환
        return alarmService.getListForUser(userId, true, unreadOnly, type, pageable);
    }

    @Operation(summary = "[ADMIN] 알림 단건 조회(타 사용자)")
    @GetMapping("/{alarmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AlarmResponse adminGetOne(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        return alarmService.getOne(principal.getId(), true, alarmId);
    }

    @Operation(summary = "[ADMIN] 읽지 않은 알림 개수(타 사용자)")
    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public long adminUnreadCount(
            @Parameter(description = "대상 사용자 ID (필수)") @RequestParam Long userId
    ) {
        return alarmService.countUnreadByAdmin(userId);
    }

    @Operation(summary = "[ADMIN] 알림 수정(타 사용자)")
    @PutMapping("/{alarmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AlarmResponse adminUpdate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId,
            @Valid @RequestBody AlarmUpdateRequest req
    ) {
        return alarmService.update(principal.getId(), true, alarmId, req);
    }

    @Operation(summary = "[ADMIN] 알림 읽음 처리(단건, 타 사용자)")
    @PatchMapping("/{alarmId}/read")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminMarkRead(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.markRead(principal.getId(), true, alarmId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "[ADMIN] 모든 알림 읽음 처리(타 사용자)")
    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> adminMarkAllRead(
            @Parameter(description = "대상 사용자 ID (필수)") @RequestParam Long userId
    ) {
        long updated = alarmService.markAllReadByAdmin(userId);
        return ResponseEntity.ok(updated);
    }

    @Operation(summary = "[ADMIN] 알림 삭제(타 사용자)")
    @DeleteMapping("/{alarmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> adminDelete(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        alarmService.delete(principal.getId(), true, alarmId);
        return ResponseEntity.noContent().build();
    }
}
