package com.chatops.service;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LinkAccountsProvisioner {

    private static final Logger log = LoggerFactory.getLogger(LinkAccountsProvisioner.class);
    private static final String LINK_ACCOUNTS_CATEGORY = "Link Accounts";
    private static final String LINK_ACCOUNTS_CHANNEL = "link-accounts";

    public void ensureLinkAccountsChannel(Guild guild) {
        Category existingCategory = guild.getCategoriesByName(LINK_ACCOUNTS_CATEGORY, true).stream().findFirst().orElse(null);
        if (existingCategory != null) {
            ensureTextChannel(existingCategory, guild);
        } else {
            log.info("Creating '{}' category in guild={}", LINK_ACCOUNTS_CATEGORY, guild.getId());
            guild.createCategory(LINK_ACCOUNTS_CATEGORY)
                    .queue(category -> ensureTextChannel(category, guild));
        }
    }

    private void ensureTextChannel(Category category, Guild guild) {
        boolean alreadyExists = category.getTextChannels().stream()
                .anyMatch(channel -> channel.getName().equalsIgnoreCase(LINK_ACCOUNTS_CHANNEL));
        if (!alreadyExists) {
            log.info("Creating '#{}' channel in guild={}", LINK_ACCOUNTS_CHANNEL, guild.getId());
            category.createTextChannel(LINK_ACCOUNTS_CHANNEL).queue();
        }
    }
}
