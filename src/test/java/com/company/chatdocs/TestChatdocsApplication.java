package com.company.chatdocs;

import org.springframework.boot.SpringApplication;

public class TestChatdocsApplication {

	public static void main(String[] args) {
		SpringApplication.from(ChatdocsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
