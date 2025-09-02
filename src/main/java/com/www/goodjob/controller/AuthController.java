package com.www.goodjob.controller;

import com.www.goodjob.domain.User;
import com.www.goodjob.repository.UserRepository;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.security.JwtTokenProvider;
import com.www.goodjob.service.AuthService;
import com.www.goodjob.service.RefreshTokenRedisService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth API", description = "OAuth 로그인 및 인증 관련 API")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final RefreshTokenRedisService refreshTokenRedisService;

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    // 환경별 쿠키 속성 주입 (운영: .goodjob.ai.kr / None / Secure=true)
    @Value("${app.cookie.domain:}")
    private String cookieDomain;

    @Value("${app.cookie.secure:true}")
    private boolean cookieSecure;

    @Value("${app.cookie.sameSite:None}")
    private String cookieSameSite;

    /** 공통: RT 쿠키 생성(갱신/삭제 겸용) */
    private ResponseCookie buildRefreshCookie(String value, long days) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from("refresh_token", value)
                .httpOnly(true)
                .secure(cookieSecure)
                .path("/")
                .sameSite(cookieSameSite);

        if (days > 0) b.maxAge(Duration.ofDays(days)); else b.maxAge(0);

        if (cookieDomain != null && !cookieDomain.isBlank()) {
            b.domain(cookieDomain); // 예: .goodjob.ai.kr
        }
        return b.build();
    }

    private void addCookie(HttpServletResponse response, ResponseCookie cookie) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseEntity<?> unauthorizedAndClearCookie(HttpServletResponse response, String code, String message) {
        addCookie(response, buildRefreshCookie("", 0));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("code", code, "message", message));
    }

    @Operation(summary = "OAuth 로그인 URL 요청", description = """
            provider 파라미터로 소셜 로그인 방식 선택 (예: google, kakao) /
            프론트는 `/auth/login?provider=kakao` 호출 후 302 리다이렉트된 URL로 이동하면 됨 /
            (예: window.location.href = 해당 주소)
            """)
    @GetMapping("/login")
    public void loginPage(@RequestParam(value = "provider", required = false) String provider,
                          HttpServletResponse response) throws Exception {
        if (provider == null || provider.isEmpty()) {
            response.getWriter().write("로그인 페이지입니다. 사용 가능한 provider: google, kakao. 예: /auth/login?provider=kakao");
        } else {
            response.sendRedirect("/oauth2/authorization/" + provider.toLowerCase());
        }
    }

    @Operation(summary = "accessToken 재발급 요청", description = """
        ✅ 쿠키에 저장된 refresh_token 기반으로 accessToken을 재발급하고,
        refresh_token도 새로 생성하여 Redis와 쿠키 모두 갱신

        🔁 프론트는 응답의 accessToken을 localStorage에 저장하면 되고,
        refresh_token은 쿠키에 자동 포함됨
        """)
    @PostMapping("/token/refresh")
    public ResponseEntity<?> refreshAccessToken(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            return unauthorizedAndClearCookie(response, "REFRESH_MISSING", "refresh token 누락");
        }

        JwtTokenProvider.TokenValidationResult vr = jwtTokenProvider.validateTokenDetailed(refreshToken);
        if (vr == JwtTokenProvider.TokenValidationResult.EXPIRED) {
            return unauthorizedAndClearCookie(response, "REFRESH_EXPIRED", "refresh token 만료");
        }
        if (vr != JwtTokenProvider.TokenValidationResult.VALID) {
            return unauthorizedAndClearCookie(response, "REFRESH_INVALID", "refresh token 무효");
        }

        String email = jwtTokenProvider.getEmail(refreshToken);

        if (!refreshTokenRedisService.isTokenValid(email, refreshToken)) {
            // 회전되었는데 브라우저 쿠키가 옛 토큰을 보낸 상황 등
            return unauthorizedAndClearCookie(response, "REFRESH_MISMATCH", "서버 저장 토큰과 일치하지 않음");
        }

        // 새 토큰 발급 + 저장 + 쿠키 갱신
        String newAccessToken = jwtTokenProvider.generateAccessToken(email);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);
        refreshTokenRedisService.saveToken(email, newRefreshToken, 14);
        addCookie(response, buildRefreshCookie(newRefreshToken, 14));

        return ResponseEntity.ok(Map.of("accessToken", newAccessToken));
    }

    @Operation(
            summary = "accessToken + firstLogin 여부 반환",
            description = """
        ✅ 소셜 로그인 완료 후 프론트가 호출하는 엔드포인트

        - 쿠키에 저장된 refresh_token에서 email, accessToken, firstLogin 값을 파싱하여 반환함
        - firstLogin은 OAuth2SuccessHandler에서 발급한 refresh_token 내부의 클레임으로 판단
        - refresh_token이 유효하지 않으면 401 반환

        🔁 프론트 처리 예시:
          1. firstLogin = true → /signUp 페이지로 이동
          2. firstLogin = false → /main 페이지로 이동
        """
    )
    @GetMapping("/callback-endpoint")
    public ResponseEntity<?> handleCallback(
            @CookieValue(value = "refresh_token", required = false) String refreshToken,
            HttpServletResponse response
    ) {
        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "refresh token 누락"));
        }

        String email;
        Boolean firstLogin;
        try {
            email = jwtTokenProvider.getEmail(refreshToken);
            firstLogin = jwtTokenProvider.getFirstLoginClaim(refreshToken);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "유효하지 않은 refresh token"));
        }

        logger.info("[LOGIN] firstLogin = {} for email={}", firstLogin, email);

        return ResponseEntity.ok(Map.of(
                "email", email,
                "accessToken", jwtTokenProvider.generateAccessToken(email),
                "firstLogin", firstLogin
        ));
    }

    @Operation(summary = "로그아웃 (refresh_token 쿠키 제거)", description = """
            refresh_token 삭제하여 로그아웃 처리함 /
            프론트는 localStorage에 있는 accessToken도 함께 제거해야 함
            """)
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("현재 인증 객체: {}", auth);

        User user = userDetails.getUser();

        // 쿠키 제거(공통)
        addCookie(response, buildRefreshCookie("", 0));
        refreshTokenRedisService.deleteToken(user.getEmail());

        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }

    @Operation(
            summary = "회원 탈퇴 (refresh_token + 사용자 정보 삭제)",
            description = """
            사용자 계정을 삭제하고 refresh_token 쿠키도 제거함
            프론트는 localStorage의 accessToken도 함께 제거해야 하며, 이후 로그인 페이지나 메인 페이지로 강제 이동 처리 권장

            🔐 Authorization: Bearer <accessToken> 헤더 필요
            """
    )
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(HttpServletResponse response,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();

        authService.withdraw(user); // 서비스 계층에서 트랜잭션 내 삭제

        // 쿠키 제거(공통)
        addCookie(response, buildRefreshCookie("", 0));
        refreshTokenRedisService.deleteToken(user.getEmail());

        return ResponseEntity.ok(Map.of(
                "message", "회원 탈퇴가 완료되었고, 로그아웃 처리되었습니다.",
                "loggedOut", true
        ));
    }

    @Operation(summary = "마스터 accessToken 발급 (관리자용)", description = """
            테스트용 masterKey 입력 시 관리자용 accessToken을 반환함 /
            Swagger Authorize에 넣고 인증된 API 테스트 가능
            """)
    @PostMapping("/master-token")
    public ResponseEntity<?> issueMasterToken(@RequestParam String key) {
        if (!"masterKey".equals(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid master key");
        }
        String email = "testadmin@goodjob.com";
        String accessToken = jwtTokenProvider.generateAccessToken(email);
        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }
}
