package com.gov.assistant.config;

import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.auth.UserAccountRepository;
import com.gov.assistant.service.auth.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
/**
 * Spring Security 配置。
 *
 * 调用链：
 * 所有 HTTP 请求 -> TokenAuthenticationFilter -> TokenService.parseToken()
 * -> UserAccountRepository -> SecurityContext -> Controller 权限匹配。
 */
public class SecurityConfig {

    private final TokenAuthenticationFilter tokenAuthenticationFilter;
    private final ObjectMapper objectMapper;

    /**
     * 配置无状态 Token 鉴权和接口角色权限。
     *
     * 调用链：
     * Spring Boot 启动 -> securityFilterChain()
     * -> 注册 TokenAuthenticationFilter
     * -> 请求进入各 Controller 前完成认证授权。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll() // Servlet 的异步派发 DispatcherType.ASYNC。第一次请求已经鉴权通过并开始写 SSE
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/chat/quick-services").authenticated()
                        .requestMatchers("/api/chat/**").hasAnyRole("ADMIN", "AGENT", "VIEWER", "USER")
                        .requestMatchers(HttpMethod.POST, "/api/admin/conversations/*/accept").hasAnyRole("ADMIN", "AGENT")
                        .requestMatchers(HttpMethod.POST, "/api/admin/conversations/*/reply").hasAnyRole("ADMIN", "AGENT")
                        .requestMatchers(HttpMethod.POST, "/api/admin/conversations/*/complete").hasAnyRole("ADMIN", "AGENT")
                        .requestMatchers(HttpMethod.POST, "/api/admin/conversations/*/close").hasAnyRole("ADMIN", "AGENT")
                        .requestMatchers("/api/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/quick-services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/knowledge/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/knowledge/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/admin/knowledge/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/knowledge/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/admin/documents/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/admin/**").hasAnyRole("ADMIN", "AGENT", "VIEWER")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().permitAll()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, ex) -> writeError(response, 401, "请先登录"))
                        .accessDeniedHandler((request, response, ex) -> writeError(response, 403, "当前账号无权访问"))
                )
                .addFilterBefore(tokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码哈希器，供登录校验和人员管理重置密码使用。
     *
     * 调用链：
     * AuthService.login()/UserAdminController.createUser()/resetPassword()
     * -> PasswordEncoder。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("message", message));
    }

    @Configuration
    @RequiredArgsConstructor
    /**
     * Bearer Token 认证过滤器。
     *
     * 调用链：
     * HTTP 请求 -> doFilterInternal()
     * -> TokenService.parseToken()
     * -> UserAccountRepository.findByUsername()
     * -> SecurityContextHolder。
     */
    static class TokenAuthenticationFilter extends OncePerRequestFilter {

        private final TokenService tokenService;
        private final UserAccountRepository userAccountRepository;

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization != null && authorization.startsWith("Bearer ")) {
                String token = authorization.substring(7);
                tokenService.parseToken(token)
                        .flatMap(claims -> userAccountRepository.findByUsername(claims.username()))
                        .filter(user -> user.getStatus() == UserAccount.Status.ACTIVE)
                        .ifPresent(user -> {
                            String role = "ROLE_" + user.getRole().name();
                            var authentication = new UsernamePasswordAuthenticationToken(
                                    user.getUsername(),
                                    null,
                                    List.of(new SimpleGrantedAuthority(role))
                            );
                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        });
            }

            filterChain.doFilter(request, response);
        }
    }
}
