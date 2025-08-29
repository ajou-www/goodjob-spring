package com.www.goodjob.controller;

import com.www.goodjob.dto.ScoredJobDto;
import com.www.goodjob.dto.alarm.AlarmCreateRequest;
import com.www.goodjob.dto.alarm.AlarmResponse;
import com.www.goodjob.dto.alarm.AlarmUpdateRequest;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.service.AlarmCommandService;
import com.www.goodjob.service.AlarmService;
import com.www.goodjob.repository.CvRepository; // cvTitle 자동 보완용 (선택)
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Tag(name = "[ADMIN] Alarms", description = "관리자 전용 알림 API (타 사용자 대상)")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/alarms")
public class AlarmAdminController {

    private final AlarmService alarmService;
    private final AlarmCommandService alarmCommandService;
    private final CvRepository cvRepository; // req.cvTitle 비었을 때 file_name 보완

    /** [ADMIN] 알림 생성(타 사용자) - idempotent */
    @Operation(
            summary = "[ADMIN] 알림 생성(타 사용자, idempotent)",
            description = """
                - 요청 본문의 userId 기준으로 생성
                - dedupeKey가 비어있으면 타입/날짜(/cvId) 기반으로 자동 생성
                - CV_MATCH의 경우 cvId가 있으면 cvTitle이 비어도 CV.file_name으로 보완
                - 이미 동일 dedupeKey가 있으면 새로 만들지 않고 409를 반환(정책). 필요시 '기존 알림 반환'으로 바꿀 수 있음.
                """
    )
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AlarmResponse> adminCreate(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @RequestBody AlarmCreateRequest req
    ) {
        if (req.getUserId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        // dedupeKey 자동 생성 (없을 때)
        String dedupeKey = Optional.ofNullable(req.getDedupeKey())
                .filter(s -> !s.isBlank())
                .orElseGet(() -> buildDedupe(req.getType(), req.getUserId(), req.getCvId(),
                        Optional.ofNullable(req.getSentAt()).orElse(LocalDateTime.now())));

        // CV title 자동 보완 (CV_MATCH + cvId 존재 + cvTitle 비었을 때)
        String cvTitle = req.getCvTitle();
        if (req.getType() == AlarmType.CV_MATCH && req.getCvId() != null &&
                (cvTitle == null || cvTitle.isBlank())) {
            cvTitle = cvRepository.findFileNameById(req.getCvId()).orElse(null);
        }

        var saved = alarmCommandService.createIfNotExists(
                req.getUserId(),
                req.getAlarmText(),
                req.getType(),
                dedupeKey,
                req.getSentAt(),
                req.getJobs(),
                req.getTitleCode(),
                req.getParams(),
                req.getCvId(),
                cvTitle
        );

        if (saved == null) {
            // 중복 키로 이미 존재
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
            // 또는 기존 반환 정책으로 바꾸려면:
            // var existing = alarmService.getByDedupeKey(dedupeKey); return ResponseEntity.status(CONFLICT).body(existing);
        }

        // 공용 변환 로직 사용(관리자 권한)
        AlarmResponse res = alarmService.getOne(principal.getId(), true, saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** [ADMIN] 알림 목록(타 사용자) - userId 없으면 전체 조회 */
    @Operation(
            summary = "[ADMIN] 알림 목록 조회",
            description = "- userId 없으면 전체 조회, 있으면 해당 사용자 스코프 조회\n- 필터: unreadOnly, type"
    )
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Page<AlarmResponse> adminList(
            @Parameter(description = "대상 사용자 ID (옵션: 미지정 시 전체)") @RequestParam(required = false) Long userId,
            @Parameter(description = "읽지 않은 알림만") @RequestParam(required = false) Boolean unreadOnly,
            @Parameter(description = "알림 타입") @RequestParam(required = false) AlarmType type,
            @Parameter(description = "페이지 번호(0-base)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        if (userId == null) {
            // 전체 범위(관리자)
            return alarmService.getList(principalIdOrNull(), true, unreadOnly, type, pageable);
        }
        // 특정 사용자 스코프
        return alarmService.getListForUser(userId, true, unreadOnly, type, pageable);
    }

    /** [ADMIN] 알림 단건 조회 */
    @Operation(summary = "[ADMIN] 알림 단건 조회(타 사용자)")
    @GetMapping("/{alarmId}")
    @PreAuthorize("hasRole('ADMIN')")
    public AlarmResponse adminGetOne(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Parameter(description = "알림 ID") @PathVariable Long alarmId
    ) {
        return alarmService.getOne(principal.getId(), true, alarmId);
    }

    /** [ADMIN] 읽지 않은 알림 개수 */
    @Operation(summary = "[ADMIN] 읽지 않은 알림 개수(타 사용자 또는 전체)")
    @GetMapping("/unread-count")
    @PreAuthorize("hasRole('ADMIN')")
    public long adminUnreadCount(
            @Parameter(description = "대상 사용자 ID (옵션)") @RequestParam(required = false) Long userId
    ) {
        if (userId != null) return alarmService.countUnreadByAdmin(userId);
        // 전체 미읽음 카운트가 필요하면 별도 repo 메서드가 있어야 함(확실하지 않음) → 임시로 0 반환
        return 0L;
    }

    /** [ADMIN] 알림 수정 */
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

    /** [ADMIN] 알림 읽음 처리 */
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

    /** [ADMIN] 모든 알림 읽음 처리(타 사용자) */
    @Operation(summary = "[ADMIN] 모든 알림 읽음 처리(타 사용자)")
    @PatchMapping("/read-all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Long> adminMarkAllRead(
            @Parameter(description = "대상 사용자 ID (필수)") @RequestParam Long userId
    ) {
        long updated = alarmService.markAllReadByAdmin(userId);
        return ResponseEntity.ok(updated);
    }

    /** [ADMIN] 알림 삭제 */
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

    /** [ADMIN] 알림 내 공고 상세(점수 포함 가능) — 유저 버전과 동일 스키마 */
    @Operation(
            summary = "[ADMIN] 알림 내 공고 상세(점수 포함 가능)",
            description = """
              - CV_MATCH: 알림의 cvId로 recommend_score에서 점수 포함
              - 그 외(APPLY_DUE 등): score=0.0
              - rank 순서 유지, 응답: ScoredJobDto[]
            """
    )
    @GetMapping("/{alarmId}/jobs")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ScoredJobDto>> adminGetAlarmJobs(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long alarmId
    ) {
        return ResponseEntity.ok(alarmService.getJobsWithOptionalScores(principal.getId(), true, alarmId));
    }

    /** dedupeKey 생성 규칙 */
    private static String buildDedupe(AlarmType type, Long userId, Long cvId, LocalDateTime when) {
        LocalDate d = when.toLocalDate();
        return switch (type) {
            case CV_MATCH -> "CV_MATCH_TOPN:%d:%d:%s".formatted(userId, cvId, d); // cvId 포함
            case APPLY_DUE -> "APPLY_DUE:%d:%s".formatted(userId, d);
            default -> "ALARM:%s:%d:%s".formatted(type.name(), userId, d);
        };
    }

    // 일부 프레임워크에서 principal이 null일 수 있어 서명만 맞추는 더미 (전체 조회시 사용)
    private Long principalIdOrNull() { return null; }
}
