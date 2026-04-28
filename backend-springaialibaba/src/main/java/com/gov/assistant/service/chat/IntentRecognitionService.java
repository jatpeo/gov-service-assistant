package com.gov.assistant.service.chat;

import com.gov.assistant.entity.chat.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class IntentRecognitionService {

    // 政策咨询关键词
    private static final List<Pattern> POLICY_PATTERNS = Arrays.asList(
            Pattern.compile(".*政策.*"),
            Pattern.compile(".*法规.*"),
            Pattern.compile(".*规定.*"),
            Pattern.compile(".*制度.*"),
            Pattern.compile(".*办法.*"),
            Pattern.compile(".*通知.*"),
            Pattern.compile(".*文件.*"),
            Pattern.compile(".*解读.*")
    );

    // 业务办理关键词
    private static final List<Pattern> BUSINESS_PATTERNS = Arrays.asList(
            Pattern.compile(".*办理.*"),
            Pattern.compile(".*申请.*"),
            Pattern.compile(".*提交.*"),
            Pattern.compile(".*审批.*"),
            Pattern.compile(".*备案.*"),
            Pattern.compile(".*登记.*"),
            Pattern.compile(".*注册.*"),
            Pattern.compile(".*变更.*")
    );

    // 进度查询关键词
    private static final List<Pattern> PROGRESS_PATTERNS = Arrays.asList(
            Pattern.compile(".*进度.*"),
            Pattern.compile(".*状态.*"),
            Pattern.compile(".*查询.*"),
            Pattern.compile(".*结果.*"),
            Pattern.compile(".*审核.*"),
            Pattern.compile(".*处理.*"),
            Pattern.compile(".*完成.*"),
            Pattern.compile(".*怎么样.*")
    );

    // 技术支持关键词
    private static final List<Pattern> TECHNICAL_PATTERNS = Arrays.asList(
            Pattern.compile(".*技术.*"),
            Pattern.compile(".*系统.*"),
            Pattern.compile(".*登录.*"),
            Pattern.compile(".*密码.*"),
            Pattern.compile(".*错误.*"),
            Pattern.compile(".*故障.*"),
            Pattern.compile(".*bug.*"),
            Pattern.compile(".*问题.*")
    );

    // 账号权限关键词
    private static final List<Pattern> ACCOUNT_PATTERNS = Arrays.asList(
            Pattern.compile(".*账号.*"),
            Pattern.compile(".*账户.*"),
            Pattern.compile(".*权限.*"),
            Pattern.compile(".*角色.*"),
            Pattern.compile(".*管理员.*"),
            Pattern.compile(".*访问.*"),
            Pattern.compile(".*授权.*"),
            Pattern.compile(".*开通.*")
    );

    // 投诉建议关键词
    private static final List<Pattern> COMPLAINT_PATTERNS = Arrays.asList(
            Pattern.compile(".*投诉.*"),
            Pattern.compile(".*建议.*"),
            Pattern.compile(".*意见.*"),
            Pattern.compile(".*反馈.*"),
            Pattern.compile(".*举报.*"),
            Pattern.compile(".*不满.*"),
            Pattern.compile(".*改进.*"),
            Pattern.compile(".*优化.*")
    );

    // 显式转人工关键词
    private static final List<Pattern> HUMAN_HANDOFF_PATTERNS = Arrays.asList(
            Pattern.compile(".*人工客服.*"),
            Pattern.compile(".*转人工.*"),
            Pattern.compile(".*转接人工.*"),
            Pattern.compile(".*接入人工.*"),
            Pattern.compile(".*人工服务.*"),
            Pattern.compile(".*人工坐席.*"),
            Pattern.compile(".*找人工.*"),
            Pattern.compile(".*联系客服.*")
    );

    /**
     * 识别用户意图
     * @param message 用户消息
     * @return 意图识别结果
     */
    public IntentResult recognizeIntent(String message) {
        if (message == null || message.trim().isEmpty()) {
            return new IntentResult(Conversation.IntentType.OTHER, 0.0);
        }

        String lowerMessage = message.toLowerCase();

        // 计算各类意图的匹配分数
        double policyScore = calculateScore(lowerMessage, POLICY_PATTERNS);
        double businessScore = calculateScore(lowerMessage, BUSINESS_PATTERNS);
        double progressScore = calculateScore(lowerMessage, PROGRESS_PATTERNS);
        double technicalScore = calculateScore(lowerMessage, TECHNICAL_PATTERNS);
        double accountScore = calculateScore(lowerMessage, ACCOUNT_PATTERNS);
        double complaintScore = calculateScore(lowerMessage, COMPLAINT_PATTERNS);

        // 找出最高分数
        double maxScore = Math.max(Math.max(Math.max(policyScore, businessScore),
                Math.max(progressScore, technicalScore)),
                Math.max(accountScore, complaintScore));

        // 如果最高分数低于阈值，返回OTHER
        if (maxScore < 0.3) {
            return new IntentResult(Conversation.IntentType.OTHER, maxScore);
        }

        // 返回最高分数对应的意图
        Conversation.IntentType intentType;
        if (maxScore == policyScore) {
            intentType = Conversation.IntentType.POLICY_CONSULTATION;
        } else if (maxScore == businessScore) {
            intentType = Conversation.IntentType.BUSINESS_PROCESSING;
        } else if (maxScore == progressScore) {
            intentType = Conversation.IntentType.PROGRESS_QUERY;
        } else if (maxScore == technicalScore) {
            intentType = Conversation.IntentType.TECHNICAL_SUPPORT;
        } else if (maxScore == accountScore) {
            intentType = Conversation.IntentType.ACCOUNT_PERMISSION;
        } else {
            intentType = Conversation.IntentType.COMPLAINT_SUGGESTION;
        }

        log.debug("意图识别结果: message={}, intent={}, confidence={}",
                message, intentType, maxScore);

        return new IntentResult(intentType, maxScore);
    }

    /**
     * 计算匹配分数
     */
    private double calculateScore(String message, List<Pattern> patterns) {
        int matchCount = 0;
        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).find()) {
                matchCount++;
            }
        }
        return (double) matchCount / patterns.size();
    }

    /**
     * 判断是否需要转人工
     */
    public boolean needHumanHandoff(IntentResult intentResult, boolean knowledgeHit) {
        // 投诉建议类优先转人工
        if (intentResult.intentType() == Conversation.IntentType.COMPLAINT_SUGGESTION) {
            return intentResult.confidence() > 0.5;
        }

        // 账号权限问题转人工
        if (intentResult.intentType() == Conversation.IntentType.ACCOUNT_PERMISSION) {
            return intentResult.confidence() > 0.7;
        }

        // 知识库未命中且置信度低
        if (!knowledgeHit && intentResult.confidence() < 0.5) {
            return true;
        }

        return false;
    }

    /**
     * 是否包含显式转人工诉求
     */
    public boolean containsHumanHandoffKeyword(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }

        String normalized = message.trim().toLowerCase();
        return HUMAN_HANDOFF_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(normalized).find());
    }

    /**
     * 是否是在系统给出人工选项后的简短回复
     */
    public boolean isHandoffSelectionReply(String message) {
        if (message == null) {
            return false;
        }

        String normalized = message.trim();
        return "2".equals(normalized)
                || "2.".equals(normalized)
                || "2、".equals(normalized)
                || "2)".equals(normalized);
    }

    /**
     * 意图识别结果
     */
    public record IntentResult(Conversation.IntentType intentType, Double confidence) {
    }
}
