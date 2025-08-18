package com.www.goodjob.controller;

import com.www.goodjob.dto.alarm.*;
import com.www.goodjob.enums.AlarmType;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.service.AlarmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/alarms")
public class AlarmController {

    private final AlarmService alarmService;

    /** CREATE */
    @PostMapping
    public ResponseEntity<AlarmResponse> create(@AuthenticationPrincipal CustomUserDetails principal,
                                                @Valid @RequestBody AlarmCreateRequest req) {
        var res = alarmService.create(principal.getId(), principal.isAdmin(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    /** READ - list (me by default) */
    @GetMapping
    public Page<AlarmResponse> list(@AuthenticationPrincipal CustomUserDetails principal,
                                    @RequestParam(required = false) Long userId,
                                    @RequestParam(required = false) Boolean unreadOnly,
                                    @RequestParam(required = false) AlarmType type,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return alarmService.getList(principal.getId(), principal.isAdmin(), userId, unreadOnly, type, pageable);
    }

    /** READ - single */
    @GetMapping("/{alarmId}")
    public AlarmResponse getOne(@AuthenticationPrincipal CustomUserDetails principal,
                                @PathVariable Long alarmId) {
        return alarmService.getOne(principal.getId(), principal.isAdmin(), alarmId);
    }

    /** UNREAD COUNT (me) */
    @GetMapping("/unread-count")
    public long unreadCount(@AuthenticationPrincipal CustomUserDetails principal) {
        return alarmService.countUnread(principal.getId());
    }

    /** UPDATE (partial/full) */
    @PutMapping("/{alarmId}")
    public AlarmResponse update(@AuthenticationPrincipal CustomUserDetails principal,
                                @PathVariable Long alarmId,
                                @Valid @RequestBody AlarmUpdateRequest req) {
        return alarmService.update(principal.getId(), principal.isAdmin(), alarmId, req);
    }

    /** MARK READ (single) */
    @PatchMapping("/{alarmId}/read")
    public ResponseEntity<Void> markRead(@AuthenticationPrincipal CustomUserDetails principal,
                                         @PathVariable Long alarmId) {
        alarmService.markRead(principal.getId(), principal.isAdmin(), alarmId);
        return ResponseEntity.noContent().build();
    }

    /** MARK ALL READ (me) */
    @PatchMapping("/read-all")
    public ResponseEntity<Long> markAllRead(@AuthenticationPrincipal CustomUserDetails principal) {
        long updated = alarmService.markAllRead(principal.getId());
        return ResponseEntity.ok(updated);
    }

    /** DELETE */
    @DeleteMapping("/{alarmId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal CustomUserDetails principal,
                                       @PathVariable Long alarmId) {
        alarmService.delete(principal.getId(), principal.isAdmin(), alarmId);
        return ResponseEntity.noContent().build();
    }
}
