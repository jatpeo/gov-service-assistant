package com.gov.assistant.controller.auth;

import com.gov.assistant.repository.auth.UserAccountRepository;
import com.gov.assistant.service.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
/**
 * 认证接口入口。
 *
 * 调用链：
 * 前端登录页 -> AuthController -> AuthService/TokenService -> UserAccountRepository。
 */
public class AuthController {

    private final AuthService authService;
    private final UserAccountRepository userAccountRepository;

    /**
     * 用户登录并签发前端访问令牌。
     *
     * 调用链：
     * LoginView -> POST /api/auth/login -> AuthService.login()
     * -> UserAccountRepository 校验用户 -> TokenService.createToken()。
     */
    @PostMapping("/login")
    public ResponseEntity<AuthService.LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request.username(), request.password()));
    }

    /**
     * 查询当前登录用户资料，用于前端刷新后恢复用户状态。
     *
     * 调用链：
     * 路由守卫/页面初始化 -> GET /api/auth/me -> Spring Security Authentication
     * -> UserAccountRepository.findByUsername()。
     */
    @GetMapping("/me")
    public ResponseEntity<AuthService.UserProfile> me(Authentication authentication) {
        String username = authentication.getName();
        return userAccountRepository.findByUsername(username)
                .map(AuthService.UserProfile::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record LoginRequest(
            @NotBlank(message = "请输入用户名") String username,
            @NotBlank(message = "请输入密码") String password
    ) {
    }
}
