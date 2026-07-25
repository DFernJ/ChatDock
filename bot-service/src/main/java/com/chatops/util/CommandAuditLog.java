package com.chatops.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CommandAuditLog {

    private static final Logger log = LoggerFactory.getLogger(CommandAuditLog.class);

    private CommandAuditLog() {
    }

    public static void logCommandRequested(String commandName, long discordId) {
        log.info("/{} requested by discordId={}", commandName, discordId);
    }
}
