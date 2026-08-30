package com.chatops.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InternalHeader {

    @Value("${security.internal-token-header}")
    private String name;

    public String getName() {
        return name;
    }

    public boolean matches(String expectedToken, String receivedToken) {
        return expectedToken.equals(receivedToken);
    }
}
