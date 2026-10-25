package com.printguard.auth;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String username;
    private final String password;
    private final String email;
    private final boolean isStaff;
    private final boolean isSuperuser;
    private final boolean isActive;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(UserEntity user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.email = user.getEmail();
        this.isStaff = Boolean.TRUE.equals(user.getIsStaff());
        this.isSuperuser = Boolean.TRUE.equals(user.getIsSuperuser());
        this.isActive = Boolean.TRUE.equals(user.getIsActive());

        List<GrantedAuthority> auths = new ArrayList<>();
        auths.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (this.isStaff || this.isSuperuser) {
            auths.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        }
        this.authorities = auths;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public boolean isStaff() { return isStaff; }
    public boolean isSuperuser() { return isSuperuser; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return password; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return isActive; }
}
