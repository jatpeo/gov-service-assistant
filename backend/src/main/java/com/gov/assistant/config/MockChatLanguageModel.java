package com.gov.assistant.config;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Mock 聊天模型
 * 当未配置 API Key 时使用，返回预设回复
 */
@Slf4j
public class MockChatLanguageModel implements ChatLanguageModel {

    @Override
    public Response<AiMessage> generate(ChatMessage... messages) {
        return generate(List.of(messages));
    }

    @Override
    public Response<AiMessage> generate(List<ChatMessage> messages) {
        String userMessage = extractLastUserMessage(messages);
        log.info("Mock模型接收消息: {}", userMessage);

        String response = generateMockResponse(userMessage);
        return Response.from(AiMessage.from(response));
    }

    private String extractLastUserMessage(List<ChatMessage> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return ((UserMessage) msg).singleText();
            }
        }
        return "";
    }

    private String generateMockResponse(String userMessage) {
        String lowerMsg = userMessage.toLowerCase();

        if (lowerMsg.contains("政策") || lowerMsg.contains("法规")) {
            return "根据最新政策，国有企业需要严格遵守《企业国有资产法》相关规定。具体政策文件可在官网查询。";
        }
        if (lowerMsg.contains("办理") || lowerMsg.contains("申请")) {
            return "业务办理流程如下：\n1. 登录平台\n2. 进入对应业务模块\n3. 填写申请表单\n4. 上传相关材料\n5. 提交审核\n\n如有疑问，可拨打客服热线 400-xxx-xxxx。";
        }
        if (lowerMsg.contains("进度") || lowerMsg.contains("查询")) {
            return "您可以通过以下方式查询办理进度：\n1. 登录平台查看[我的业务]\n2. 使用业务编号在查询页面检索\n3. 联系专属客服经理\n\n一般业务审核周期为 5-15 个工作日。";
        }
        if (lowerMsg.contains("登录") || lowerMsg.contains("密码") || lowerMsg.contains("账号")) {
            return "如遇登录问题，请尝试：\n1. 检查用户名和密码是否正确\n2. 清除浏览器缓存后重试\n3. 使用[忘记密码]功能重置\n4. 联系系统管理员协助处理\n\n注意：连续 5 次输错密码将锁定账号 30 分钟。";
        }
        if (lowerMsg.contains("投诉") || lowerMsg.contains("建议")) {
            return "感谢您的反馈！我们非常重视您的意见。\n\n投诉建议渠道：\n1. 在线提交工单\n2. 拨打投诉热线 400-xxx-xxxx\n3. 发送邮件至 feedback@gov-assistant.com\n\n我们会在 3 个工作日内给予回复。";
        }

        return "您好！我已收到您的问题。作为客服智能助手，我可以为您解答政策咨询、业务办理、进度查询、技术支持等相关问题。\n\n请问有什么具体可以帮助您的吗？";
    }
}
