package com.DockerOps.util;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class InternalHeader {

    @Value("${app.security.internal-token-header}")
    private String name;

    public boolean matches(String expectedToken, String receivedToken) {
        return expectedToken.equals(receivedToken);
    }
}
