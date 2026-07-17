package com.chatops.service;

import com.chatops.dto.ContainerFailureEvent;
import com.chatops.util.ChannelNames;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ContainerFailureNotifier {

    private static final String MANAGED_CONTAINERS_CATEGORY = "Managed Containers";

    private final JDA jda;

    public ContainerFailureNotifier(JDA jda) {
        this.jda = jda;
    }

    public void notifyFailure(ContainerFailureEvent event) {
        String message = buildMessage(event);
        String channelName = ChannelNames.sanitize(event.containerName());

        for (Guild guild : jda.getGuilds()) {
            for (Category category : guild.getCategoriesByName(MANAGED_CONTAINERS_CATEGORY, true)) {
                findChannel(category, channelName).ifPresent(channel -> channel.sendMessage(message).queue());
            }
        }
    }

    private Optional<TextChannel> findChannel(Category category, String channelName) {
        return category.getTextChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(channelName))
                .findFirst();
    }

    private String buildMessage(ContainerFailureEvent event) {
        StringBuilder message = new StringBuilder("**Container failure detected**\n");
        message.append("Exit code: **").append(event.exitCode() != null ? event.exitCode() : "unknown").append("**\n");
        if (event.finishedAt() != null) {
            message.append("Finished at: ").append(event.finishedAt()).append("\n");
        }
        if (event.message() != null && !event.message().isBlank()) {
            message.append(event.message());
        }
        return message.toString();
    }
}
