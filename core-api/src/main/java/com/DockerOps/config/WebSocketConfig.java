package com.DockerOps.config;

import com.DockerOps.ws.ContainerConsoleWebSocketHandler;
import com.DockerOps.ws.ContainerTerminalWebSocketHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private ContainerTerminalWebSocketHandler terminalWebSocketHandler;
    @Autowired
    private ContainerConsoleWebSocketHandler consoleWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(terminalWebSocketHandler, "/ws/containers/{containerId}/terminal")
                .setAllowedOriginPatterns("*");
        registry.addHandler(consoleWebSocketHandler, "/ws/containers/{containerId}/console")
                .setAllowedOriginPatterns("*");
    }
}
