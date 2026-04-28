package com.gov.assistant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 配置属性
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "spring.ai.dashscope.chat.options")
public class AiProperties {

    /**
     * 模型名称
     */
    private String model = "qwen-plus";

    /**
     * 温度参数，控制随机性
     */
    private double temperature = 0.7;

    /**
     * 最大 token 数
     */
    private int maxTokens = 2048;
}
