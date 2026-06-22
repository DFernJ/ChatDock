package com.DockerOps.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
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
public class ContainerTerminalWebSocketHandler extends TextWebSocketHandler {

    private record TerminalSession(String execId, PipedOutputStream stdin) {}

    @Autowired
    private DockerClient dockerClient;
    @Autowired
    private RoleHierarchy roleHierarchy;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, TerminalSession> sessions = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        if (!hasAuthority(session, "PERM_EDITOR")) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Editor permission required to use the terminal"));
            return;
        }

        String containerId = extractContainerId(session);
        ExecCreateCmdResponse exec;
        try {
            exec = dockerClient.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .withCmd("/bin/sh")
                    .exec();
        } catch (RuntimeException e) {
            session.sendMessage(new TextMessage("[31mCould not start a shell in this container: " + e.getMessage() + "[0m\r\n"));
            session.close(CloseStatus.SERVER_ERROR);
            return;
        }

        PipedOutputStream stdinOut = new PipedOutputStream();
        PipedInputStream stdinIn = new PipedInputStream(stdinOut, 4096);
        sessions.put(session.getId(), new TerminalSession(exec.getId(), stdinOut));

        dockerClient.execStartCmd(exec.getId())
                .withTty(true)
                .withStdIn(stdinIn)
                .exec(new ResultCallback.Adapter<>() {
                    @Override
                    public void onNext(Frame frame) {
                        sendQuietly(session, new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        sendQuietly(session, "\r\n[31mSession ended: " + throwable.getMessage() + "[0m\r\n");
                        closeQuietly(session);
                    }

                    @Override
                    public void onComplete() {
                        closeQuietly(session);
                    }
                });
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        TerminalSession terminalSession = sessions.get(session.getId());
        if (terminalSession == null) return;

        JsonNode node = objectMapper.readTree(message.getPayload());
        String type = node.path("type").asText("");
        if ("input".equals(type)) {
            terminalSession.stdin().write(node.path("data").asText("").getBytes(StandardCharsets.UTF_8));
            terminalSession.stdin().flush();
        } else if ("resize".equals(type)) {
            int cols = node.path("cols").asInt(80);
            int rows = node.path("rows").asInt(24);
            dockerClient.resizeExecCmd(terminalSession.execId()).withSize(rows, cols).exec();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        TerminalSession terminalSession = sessions.remove(session.getId());
        if (terminalSession != null) {
            try {
                terminalSession.stdin().close();
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
