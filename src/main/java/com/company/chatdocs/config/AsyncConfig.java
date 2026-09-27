package com.company.chatdocs.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Turns on {@code @Async}. With {@code spring.threads.virtual.enabled=true}, Spring Boot's default task executor
 * runs each async task on a virtual thread, so no custom executor is needed.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
