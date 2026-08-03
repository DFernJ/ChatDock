package com.chatops.service;

import com.chatops.dto.ContainerLifecycleEvent;
import com.chatops.util.ChannelNames;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Service
public class ContainerLifecycleNotifier {

    private static final Logger log = LoggerFactory.getLogger(ContainerLifecycleNotifier.class);
    private static final String MANAGED_CONTAINERS_CATEGORY = "Managed Containers";

    private final JDA jda;

    public ContainerLifecycleNotifier(@Lazy JDA jda) {
        this.jda = jda;
    }

    public void notifyCreated(ContainerLifecycleEvent event) {
        String channelName = ChannelNames.sanitize(event.containerName());
        for (Guild guild : jda.getGuilds()) {
            ensureCategoryThen(guild, category -> createChannelIfMissing(category, channelName));
        }
    }

    public void reconcileManagedContainers(Guild guild, List<String> containerNames) {
        if (containerNames.isEmpty()) return;
        List<String> channelNames = containerNames.stream().map(ChannelNames::sanitize).toList();
        ensureCategoryThen(guild, category -> {
            for (String channelName : channelNames) {
                createChannelIfMissing(category, channelName);
            }
        });
    }

    private void ensureCategoryThen(Guild guild, Consumer<Category> action) {
        Category category = guild.getCategoriesByName(MANAGED_CONTAINERS_CATEGORY, true).stream().findFirst().orElse(null);
        if (category != null) {
            action.accept(category);
        } else {
            log.info("Creating '{}' category in guild={}", MANAGED_CONTAINERS_CATEGORY, guild.getId());
            guild.createCategory(MANAGED_CONTAINERS_CATEGORY).queue(action);
        }
    }

    public void notifyDeleted(ContainerLifecycleEvent event) {
        String channelName = ChannelNames.sanitize(event.containerName());
        for (Guild guild : jda.getGuilds()) {
            for (Category category : guild.getCategoriesByName(MANAGED_CONTAINERS_CATEGORY, true)) {
                findChannel(category, channelName).ifPresent(channel -> {
                    log.info("Deleting channel '{}' in guild={} for removed container '{}'", channel.getName(), guild.getId(), event.containerName());
                    channel.delete().queue();
                });
            }
        }
    }

    private void createChannelIfMissing(Category category, String channelName) {
        if (findChannel(category, channelName).isEmpty()) {
            category.createTextChannel(channelName).queue();
        }
    }

    private Optional<TextChannel> findChannel(Category category, String channelName) {
        return category.getTextChannels().stream()
                .filter(channel -> channel.getName().equalsIgnoreCase(channelName))
                .findFirst();
    }
}
