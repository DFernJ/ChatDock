package com.chatops.util;

public final class ChannelNames {

    private ChannelNames() {
    }

    public static String sanitize(String rawName) {
        String channelName = rawName.trim().toLowerCase().replaceAll("[^a-z0-9-_]+", "-");
        return channelName.length() > 100 ? channelName.substring(0, 100) : channelName;
    }
}
