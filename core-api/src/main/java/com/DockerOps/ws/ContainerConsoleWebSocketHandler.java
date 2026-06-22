package com.DockerOps.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ContainerConsoleWebSocketHandler extends TextWebSocketHandler {

    private record ConsoleSession(PipedOutputStream stdin) {}

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private RoleHierarchy roleHierarchy;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, ConsoleSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!hasAuthority(session, "PERM_EDITOR")) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Editor permission required to use the console"));
            return;
        }

        String containerId = extractContainerId(session);
        PipedOutputStream stdinOut = new PipedOutputStream();
        PipedInputStream stdinIn = new PipedInputStream(stdinOut, 4096);
        sessions.put(session.getId(), new ConsoleSession(stdinOut));

        try {
            dockerClient.attachContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withStdIn(stdinIn)
                    .withFollowStream(true)
                    .exec(new ResultCallback.Adapter<>() {
                        @Override
                        public void onNext(Frame frame) {
                            sendQuietly(session, new String(frame.getPayload(), StandardCharsets.UTF_8));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            sendQuietly(session, "\r\n[31mStream ended: " + throwable.getMessage() + "[0m\r\n");
                            closeQuietly(session);
                        }

                        @Override
                        public void onComplete() {
                            closeQuietly(session);
                        }
                    });
        } catch (RuntimeException e) {
            sendQuietly(session, "Could not attach to the container: " + e.getMessage() + "\r\n");
            closeQuietly(session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        ConsoleSession consoleSession = sessions.get(session.getId());
        if (consoleSession == null) return;

        JsonNode node = objectMapper.readTree(message.getPayload());
        if ("input".equals(node.path("type").asText(""))) {
            consoleSession.stdin().write(node.path("data").asText("").getBytes(StandardCharsets.UTF_8));
            consoleSession.stdin().flush();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        ConsoleSession consoleSession = sessions.remove(session.getId());
        if (consoleSession != null) {
            try {
                consoleSession.stdin().close();
            } catch (IOException ignored) {
            }
        }
    }

    private void sendQuietly(WebSocketSession session, String text) {
        try {
            if (session.isOpen()) session.sendMessage(new TextMessage(text));
        } catch (IOException ignored) {
        }
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            if (session.isOpen()) session.close();
        } catch (IOException ignored) {
        }
    }

    private boolean hasAuthority(WebSocketSession session, String authority) {
        if (!(session.getPrincipal() instanceof Authentication authentication)) return false;
        Collection<? extends GrantedAuthority> reachable = roleHierarchy.getReachableGrantedAuthorities(authentication.getAuthorities());
        return reachable.stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    private String extractContainerId(WebSocketSession session) {
        String[] parts = session.getUri().getPath().split("/");
        return parts[3];
    }
}
