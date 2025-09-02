package com.www.goodjob.repository;

import com.www.goodjob.domain.User;
import com.www.goodjob.domain.UserOAuth;
import com.www.goodjob.enums.OAuthProvider;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserOAuthRepository extends JpaRepository<UserOAuth, Long> {
    void deleteAllByUser(User user);

    // 매핑 우선 조회 + user 즉시 로딩 (프록시 방지)
    @EntityGraph(attributePaths = "user")
    Optional<UserOAuth> findByProviderAndOauthId(OAuthProvider provider, String oauthId);

    // 기존 유저에 특정 provider 매핑이 이미 있는지
    boolean existsByUserAndProvider(User user, OAuthProvider provider);
}
