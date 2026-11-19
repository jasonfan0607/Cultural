package com.cultural.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AgentSettings.class)
public class AgentApplication {
    public static void main(String[] args) { SpringApplication.run(AgentApplication.class, args); }
}
