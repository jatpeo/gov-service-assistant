package com.gov.assistant.service.auth;

import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.auth.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
/**
 * 登录认证业务服务。
 *
 * 调用链：
 * AuthController.login() -> AuthService.login()
 * -> UserAccountRepository 查询账号 -> PasswordEncoder 校验密码
 * -> TokenService.createToken() 生成访问令牌。
 */
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * 校验用户名、密码和账号状态，成功后更新最近登录时间并返回令牌。
     *
     * 调用链：
     * AuthController.login() -> login()
     * -> UserAccountRepository.findByUsername()
     * -> PasswordEncoder.matches()
     * -> UserAccountRepository.save()
     * -> TokenService.createToken()。
     */
    @Transactional
    public LoginResponse login(String username, String password) {
        UserAccount user = userAccountRepository.findByUsername(username)
                .orElseThrow(() -> new BadCredentialsException("用户名或密码错误"));

        if (user.getStatus() != UserAccount.Status.ACTIVE
                || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BadCredentialsException("用户名或密码错误");
        }

        user.setLastLoginAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userAccountRepository.save(user);

        return new LoginResponse(tokenService.createToken(user), UserProfile.from(user));
    }

    public record LoginResponse(String token, UserProfile user) {
    }

    public record UserProfile(Long id, String username, String displayName, UserAccount.UserRole role) {
        public static UserProfile from(UserAccount user) {
            return new UserProfile(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole());
        }
    }
}
