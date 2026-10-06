package com.Let.s_Code.nexus.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatConfig {

    // Spring AI auto-configures a ChatClient.Builder bean, not a ChatClient bean directly.
    // We build the actual ChatClient once here so it can be injected elsewhere.
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}