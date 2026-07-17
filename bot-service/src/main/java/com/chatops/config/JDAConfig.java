package com.chatops.config;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class JDAConfig {

    @Value("${discord.token}")
    private String token;

    @Bean
    public JDA jda(List<ListenerAdapter> listeners) {
        JDABuilder builder = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.MESSAGE_CONTENT);

        for (ListenerAdapter listener : listeners) {
            builder.addEventListeners(listener);
        }

        return builder.build();
    }
}
