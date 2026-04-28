package com.gov.assistant.service.auth;

import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.repository.auth.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
/**
 * 当前登录用户解析服务。
 *
 * 调用链：
 * Controller 方法接收 Authentication
 * -> CurrentUserService.requireUser()
 * -> UserAccountRepository.findByUsername()。
 */
public class CurrentUserService {

    private final UserAccountRepository userAccountRepository;

    /**
     * 根据 Spring Security Authentication 获取当前用户实体。
     *
     * 调用链：
     * ChatController/ConversationAdminController/UserAdminController
     * -> requireUser() -> UserAccountRepository。
     */
    public UserAccount requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("请先登录");
        }

        return userAccountRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("当前登录账号不存在"));
    }
}
