package com.www.goodjob.config;

import com.www.goodjob.domain.User;
import com.www.goodjob.domain.UserOAuth;
import com.www.goodjob.enums.OAuthProvider;
import com.www.goodjob.enums.UserRole;
import com.www.goodjob.repository.UserOAuthRepository;
import com.www.goodjob.repository.UserRepository;
import com.www.goodjob.security.CustomOAuth2User;
import com.www.goodjob.security.JwtTokenProvider;
import com.www.goodjob.service.RefreshTokenRedisService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@RequiredArgsConstructor
@Component
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final RefreshTokenRedisService refreshTokenRedisService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        CustomOAuth2User customUser = (oAuth2User instanceof CustomOAuth2User)
                ? (CustomOAuth2User) oAuth2User
                : new CustomOAuth2User(oAuth2User);

        String email = customUser.getEmail();
        String name = customUser.getName();
        OAuthProvider provider = extractProvider(customUser);
        String oauthId = customUser.getOauthId(provider);

        // 회원이 없다면 저장 (최초 로그인)
        boolean isFirstLogin = !userRepository.existsByEmail(email);

        if (isFirstLogin) {
            User newUser = userRepository.save(User.builder()
                    .email(email)
                    .name(name)
                    .role(UserRole.USER)
                    .build());

            userOAuthRepository.save(UserOAuth.builder()
                    .user(newUser)
                    .provider(provider)
                    .oauthId(oauthId)
                    .build());

            log.info("[OAUTH] firstLogin = true for email={}", email);
        } else {
            log.info("[OAUTH] firstLogin = false for email={}", email);
        }

        // refreshToken 생성 및 Redis 저장
        String refreshToken = jwtTokenProvider.generateRefreshToken(email, isFirstLogin);
        refreshTokenRedisService.saveToken(email, refreshToken, 14); // TTL 14일로 통일

        ResponseCookie refreshTokenCookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(Duration.ofDays(14)) // 쿠키 TTL 맞춤
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        // 리디렉션 처리
        String encodedState = request.getParameter("state");
        String redirectUri = "https://www.goodjob.ai.kr/auth/callback";

        try {
            if (encodedState != null) {
                byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedState);
                String decoded = new String(decodedBytes, StandardCharsets.UTF_8).replaceAll("[\\r\\n]", "");
                if (isValidRedirectUri(decoded)) {
                    redirectUri = decoded;
                    log.info("[OAUTH] decoded redirect_uri from state: {}", redirectUri);
                }
            }
        } catch (IllegalArgumentException e) {
            log.warn("[OAUTH] failed to decode or validate state, fallback to default. state: {}", encodedState);
        }

        if (!response.isCommitted()) {
            response.sendRedirect(redirectUri);
        }
    }

    private boolean isValidRedirectUri(String uri) {
        return uri != null &&
                !uri.contains("\r") &&
                !uri.contains("\n") &&
                (uri.startsWith("https://localhost:5173") || uri.startsWith("https://www.goodjob.ai.kr"));
    }

    private OAuthProvider extractProvider(CustomOAuth2User user) {
        if (user.getAttributes().containsKey("provider")) {
            return OAuthProvider.valueOf(user.getAttributes().get("provider").toString().toUpperCase());
        }
        throw new IllegalStateException("OAuth provider not found in user attributes");
    }
}
