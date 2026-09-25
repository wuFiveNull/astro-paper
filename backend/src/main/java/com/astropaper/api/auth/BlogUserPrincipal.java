package com.astropaper.api.auth;

import com.astropaper.api.domain.entity.RoleEntity;
import com.astropaper.api.domain.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

public final class BlogUserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String passwordHash;
    private final String email;
    private final String displayName;
    private final String status;
    private final List<String> roleCodes;
    private final List<String> permissionCodes;
    private final List<GrantedAuthority> authorities;

    public BlogUserPrincipal(UserEntity user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.passwordHash = user.getPasswordHash();
        this.email = user.getEmail();
        this.displayName = user.getDisplayName();
        this.status = user.getStatus();

        TreeSet<String> roles = new TreeSet<>();
        TreeSet<String> permissions = new TreeSet<>();
        for (RoleEntity role : user.getRoles()) {
            roles.add(role.getCode());
            role.getPermissions().forEach(permission -> permissions.add(permission.getCode()));
        }
        this.roleCodes = List.copyOf(roles);
        this.permissionCodes = List.copyOf(permissions);

        List<GrantedAuthority> granted = new ArrayList<>();
        roles.forEach(code -> granted.add(new SimpleGrantedAuthority("ROLE_" + code)));
        permissions.forEach(code -> granted.add(new SimpleGrantedAuthority(code)));
        this.authorities = List.copyOf(granted);
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public List<String> getRoleCodes() { return roleCodes; }
    public List<String> getPermissionCodes() { return permissionCodes; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }

    @Override
    public String getPassword() { return passwordHash; }

    @Override
    public String getUsername() { return username; }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return !"LOCKED".equals(status); }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return "ACTIVE".equals(status); }
}
