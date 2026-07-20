package com.xuan.auth.security;

import com.xuan.entity.SysUser;
import com.xuan.mapper.SysUserMapper;
import com.xuan.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 加载用户信息的 UserDetailsService 实现
 */
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;
    private final SysUserRoleMapper sysUserRoleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectByUsername(username);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw new UsernameNotFoundException("用户不存在或已禁用");
        }

        String password = user.getPassword();
        // 如果密码不是以 {bcrypt} 开头，自动添加前缀以兼容 DelegatingPasswordEncoder
        if (password != null && !password.startsWith("{bcrypt}")) {
            password = "{bcrypt}" + password;
        }

        List<String> roles = sysUserRoleMapper.selectRoleCodesByUserId(user.getId());
        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());

        return new SecurityUser(user.getId(), user.getUsername(), password,
                user.getUserType(), user.getNickname(),
                user.getEmail(), user.getAvatar(), authorities);
    }
}
