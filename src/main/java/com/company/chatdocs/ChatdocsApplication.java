package com.company.chatdocs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ChatdocsApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatdocsApplication.class, args);
	}

}
