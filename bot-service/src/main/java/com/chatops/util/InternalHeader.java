package com.chatops.util;

public class InternalHeader {

    public static final String NAME = "X-Internal-Token";

    private InternalHeader() {
    }

    public static boolean matches(String expectedToken, String receivedToken) {
        return expectedToken.equals(receivedToken);
    }
}
