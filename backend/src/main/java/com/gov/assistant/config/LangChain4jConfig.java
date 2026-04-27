package com.gov.assistant.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * LangChain4j 配置类
 * 配置通义千问大语言模型
 */
@Slf4j
@Configuration
public class LangChain4jConfig {

    @Value("${langchain4j.dashscope.api-key:}")
    private String apiKey;

    @Value("${langchain4j.dashscope.model-name:qwen-max}")
    private String modelName;

    /**
     * 配置通义千问聊天模型
     */
    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.isEmpty() || "your-api-key".equals(apiKey)) {
            log.warn("DashScope API Key 未配置，使用 Mock 模型");
            return new MockChatLanguageModel();
        }

        return QwenChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .build();
    }
}
