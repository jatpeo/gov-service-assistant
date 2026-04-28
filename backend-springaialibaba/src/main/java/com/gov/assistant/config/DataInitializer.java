package com.gov.assistant.config;

import com.gov.assistant.entity.auth.UserAccount;
import com.gov.assistant.entity.admin.QuickService;
import com.gov.assistant.repository.admin.QuickServiceRepository;
import com.gov.assistant.repository.auth.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserAccountRepository userAccountRepository;
    private final QuickServiceRepository quickServiceRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.security.default-admin.username:admin}")
    private String defaultAdminUsername;

    @Value("${app.security.default-admin.password:admin123}")
    private String defaultAdminPassword;

    @Bean
    public CommandLineRunner seedDefaultUsers() {
        return args -> {
            LocalDateTime now = LocalDateTime.now();
            if (!userAccountRepository.existsByUsername(defaultAdminUsername)) {
                UserAccount admin = UserAccount.builder()
                        .username(defaultAdminUsername)
                        .passwordHash(passwordEncoder.encode(defaultAdminPassword))
                        .displayName("系统管理员")
                        .role(UserAccount.UserRole.ADMIN)
                        .status(UserAccount.Status.ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                userAccountRepository.save(admin);
            }

            if (!userAccountRepository.existsByUsername("user")) {
                UserAccount user = UserAccount.builder()
                        .username("user")
                        .passwordHash(passwordEncoder.encode("user123"))
                        .displayName("普通用户")
                        .role(UserAccount.UserRole.USER)
                        .status(UserAccount.Status.ACTIVE)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                userAccountRepository.save(user);
            }

            seedQuickService("policy", "政策咨询", "Document", "我想咨询相关监管政策，请帮我说明适用范围、办理依据和注意事项。", 10);
            seedQuickService("business", "业务办理", "OfficeBuilding", "我想办理相关业务，请告诉我办理流程、所需材料和办理入口。", 20);
            seedQuickService("progress", "进度查询", "Search", "我想查询业务办理进度，请告诉我需要提供哪些信息以及如何查询。", 30);
            seedQuickService("support", "技术支持", "Tools", "我在系统使用中遇到问题，请帮我排查可能原因和处理步骤。", 40);
            seedQuickService("account", "账号权限", "User", "我想咨询账号权限问题，请说明账号开通、角色分配或权限异常的处理方式。", 50);
            seedQuickService("feedback", "投诉建议", "MessageBox", "我想反馈投诉或建议，请告诉我受理方式、所需信息和后续处理流程。", 60);
        };
    }

    private void seedQuickService(String key, String label, String iconKey, String promptText, int sortOrder) {
        if (quickServiceRepository.existsByServiceKey(key)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        quickServiceRepository.save(QuickService.builder()
                .serviceKey(key)
                .label(label)
                .iconKey(iconKey)
                .promptText(promptText)
                .sortOrder(sortOrder)
                .status(QuickService.Status.ENABLED)
                .createdAt(now)
                .updatedAt(now)
                .build());
    }
}
