package com.www.goodjob.controller;

import com.www.goodjob.domain.User;
import com.www.goodjob.repository.UserRepository;
import com.www.goodjob.security.CustomUserDetails;
import com.www.goodjob.security.JwtTokenProvider;
import com.www.goodjob.service.AuthService;
import com.www.goodjob.service.RefreshTokenRedisService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    private final RefreshTokenRedisService refreshTokenRedisService;

    @Operation(summary = "OAuth 로그인 URL 요청", description = """
            provider 파라미터로 소셜 로그인 방식 선택 (예: google, kakao) /
            프론트는 `/auth/login?provider=kakao` 호출 후 302 리다이렉트된 URL로 이동하면 됨 /
            (예: window.location.href = 해당 주소)
            """)
    // 커스텀 로그인 페이지 (provider 파라미터 옵션 처리)
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
        if (refreshToken == null || !jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or missing refresh token"));
        }

        String email = jwtTokenProvider.getEmail(refreshToken);

        // Redis에 저장된 refresh_token과 비교하여 일치하는지 확인
        if (!refreshTokenRedisService.isTokenValid(email, refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "서버에 저장된 토큰과 일치하지 않음"));
        }

        // accessToken, refreshToken 새로 발급
        String newAccessToken = jwtTokenProvider.generateAccessToken(email);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(email);

        // Redis에 refreshToken 갱신 저장 (TTL: 14일)
        refreshTokenRedisService.saveToken(email, newRefreshToken, 14);

        // 쿠키 갱신
        ResponseCookie cookie = ResponseCookie.from("refresh_token", newRefreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(14))
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

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
    // 로그아웃 (refresh_token 쿠키 제거)
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response,
                                    @AuthenticationPrincipal CustomUserDetails userDetails) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        logger.info("현재 인증 객체: {}", auth);

        User user = userDetails.getUser();

        // 쿠키 제거
        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

        refreshTokenRedisService.deleteToken(user.getEmail());

        return ResponseEntity.ok(Map.of("message", "로그아웃 되었습니다."));
    }


    @Operation(
            summary = "회원 탈퇴 (refresh_token + 사용자 정보 삭제)",
            description = """
            사용자 계정을 삭제하고 refresh_token 쿠키도 제거함
            프론트는 localStorage의 accessToken도 함께 제거해야 하며, 이후 로그인 페이지나 메인 페이지로 강제 이동 처리 권장
                        
            🔐 Authorization: Bearer <accessToken> 헤더 필요
                        
            🔁 프론트 처리 예시:
              1. 응답에서 `loggedOut: true` 확인
              2. localStorage.clear() 또는 accessToken 제거
              3. 로그인 페이지나 메인 페이지 등으로 이동
            """
    )
    @DeleteMapping("/withdraw")
    public ResponseEntity<?> withdraw(HttpServletResponse response,
                                      @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();

        authService.withdraw(user); // 서비스 계층에서 트랜잭션 내 삭제

        // 쿠키 제거
        ResponseCookie deleteCookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, deleteCookie.toString());

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

        // 관리자로 간주될 마스터 유저 이메일
        String email = "testadmin@goodjob.com";

        // AccessToken만 발급
        String accessToken = jwtTokenProvider.generateAccessToken(email);
        return ResponseEntity.ok(Map.of("accessToken", accessToken));
    }

}
