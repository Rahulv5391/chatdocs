package com.company.chatdocs.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

	/** The one ChatClient the app uses. Model and temperature come from spring.ai.google.genai.chat.*. */
	@Bean
	ChatClient chatClient(ChatClient.Builder builder) {
		return builder.build();
	}

}
