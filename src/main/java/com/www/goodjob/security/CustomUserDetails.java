package com.www.goodjob.security;

import com.www.goodjob.domain.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    /** 현재 사용자 역할을 바로 반환 (있으면 편함) */
    public com.www.goodjob.enums.UserRole getRole() {
        return user.getRole();
    }

    /** ADMIN 여부 체크 (컨트롤러에서 principal.isAdmin() 으로 사용) */
    public boolean isAdmin() {
        // getAuthorities()는 ROLE_ 접두사를 달아주고 있지만,
        // 도메인 엔티티의 role enum을 직접 확인하는 편이 더 직접적/빠릅니다.
        return user.getRole() == com.www.goodjob.enums.UserRole.ADMIN;
        // 또는 권한 기반으로 확인하고 싶다면:
        // return getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return null; // 소셜 로그인만 한다면 null 가능
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }

}
