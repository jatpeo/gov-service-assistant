package com.gov.assistant.service;

import com.gov.assistant.entity.Conversation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Slf4j
@Service
public class StandardAnswerService {

    private static final List<StandardAnswerRule> RULES = List.of(
            new StandardAnswerRule(
                    "LOGIN_PASSWORD",
                    Conversation.IntentType.TECHNICAL_SUPPORT,
                    0.55,
                    Arrays.asList(
                            Pattern.compile(".*登录.*"),
                            Pattern.compile(".*登陆.*"),
                            Pattern.compile(".*密码.*"),
                            Pattern.compile(".*忘记密码.*"),
                            Pattern.compile(".*重置密码.*"),
                            Pattern.compile(".*账号锁定.*"),
                            Pattern.compile(".*无法登录.*"),
                            Pattern.compile(".*进不去.*")
                    ),
                    """
                            您好！关于您反映的“%s”问题，建议您先按以下步骤处理：

                            简要结论：
                            请先使用平台登录页的“忘记密码”功能自助重置；如果自助重置不可用或失败，请联系系统管理员协助重置。

                            详细说明：
                            1. 请在登录页点击“忘记密码”，按照页面提示完成实名核验并设置新密码。
                            2. 如账号被锁定、未绑定手机号，或实名信息不一致，可能导致无法自助重置。
                            3. 若仍无法处理，请准备以下信息以便快速协助：
                               - 用户姓名及所属企业全称
                               - 登录账号（如统一社会信用代码或注册手机号）
                               - 问题发生时间及具体提示内容

                            后续处理：
                            若您希望，我们也可以继续为您转接人工客服。
                            """
            ),
            new StandardAnswerRule(
                    "ACCOUNT_PERMISSION",
                    Conversation.IntentType.ACCOUNT_PERMISSION,
                    0.6,
                    Arrays.asList(
                            Pattern.compile(".*权限.*"),
                            Pattern.compile(".*授权.*"),
                            Pattern.compile(".*开通.*"),
                            Pattern.compile(".*角色.*"),
                            Pattern.compile(".*账号.*"),
                            Pattern.compile(".*账户.*")
                    ),
                    """
                            您好！关于账号权限问题，建议您先确认：

                            1. 当前账号是否已完成实名认证。
                            2. 所属企业是否已为您分配相应角色。
                            3. 是否需要由管理员在后台重新开通权限。

                            如果您方便，可以直接提供账号名称、所属企业和需要开通的功能模块，我们将进一步协助您处理。
                            """
            )
    );

    /**
     * 匹配标准答案
     */
    public Optional<StandardAnswerResult> match(String message, Conversation.IntentType intentType) {
        if (message == null || message.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalized = message.trim().toLowerCase();
        for (StandardAnswerRule rule : RULES) {
            if (intentType != null && rule.intentType() != intentType && rule.priority() < 0.6) {
                continue;
            }

            double score = calculateScore(normalized, rule.patterns());
            if (score >= rule.threshold()) {
                String summary = summarizeMessage(message);
                String response = String.format(rule.template(), summary);
                log.debug("命中标准答案规则: ruleCode={}, score={}, message={}", rule.ruleCode(), score, message);
                return Optional.of(new StandardAnswerResult(rule.ruleCode(), rule.intentType(), score, response));
            }
        }

        return Optional.empty();
    }

    private double calculateScore(String message, List<Pattern> patterns) {
        int matchCount = 0;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).find()) {
                matchCount++;
            }
        }
        return (double) matchCount / patterns.size();
    }

    private String summarizeMessage(String message) {
        String trimmed = message.trim();
        if (trimmed.length() <= 30) {
            return trimmed;
        }
        return trimmed.substring(0, 30) + "…";
    }

    private record StandardAnswerRule(
            String ruleCode,
            Conversation.IntentType intentType,
            double threshold,
            List<Pattern> patterns,
            String template,
            double priority
    ) {
        private StandardAnswerRule(String ruleCode,
                                   Conversation.IntentType intentType,
                                   double threshold,
                                   List<Pattern> patterns,
                                   String template) {
            this(ruleCode, intentType, threshold, patterns, template, 1.0);
        }
    }

    public record StandardAnswerResult(
            String ruleCode,
            Conversation.IntentType intentType,
            double confidence,
            String response
    ) {
    }
}
