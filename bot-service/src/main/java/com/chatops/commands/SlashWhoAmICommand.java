package com.chatops.commands;

import com.chatops.dto.WhoAmIResponse;
import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

@Component
public class SlashWhoAmICommand implements ISlashCommands {

    private final ApiService apiService;

    public SlashWhoAmICommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "whoami";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Shows your Discord username and your linked ChatOps roles.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String discordUsername = event.getUser().getName();
        long discordId = event.getUser().getIdLong();

        try {
            WhoAmIResponse whoAmI = apiService.fetchWhoAmI(discordId);
            event.reply("""
                    Discord username: **%s**
                    Auth role: **%s**
                    Permission role: **%s**""".formatted(discordUsername, whoAmI.authRole(), whoAmI.permissionRole()))
                    .setEphemeral(true)
                    .queue();
        } catch (HttpClientErrorException.NotFound e) {
            event.reply("Discord username: **" + discordUsername + "**\nYour Discord account isn't linked to a ChatOps account yet. Use /link to link it.")
                    .setEphemeral(true)
                    .queue();
        } catch (Exception e) {
            e.printStackTrace();
            event.reply("Something went wrong while fetching your roles. Please try again.")
                    .setEphemeral(true)
                    .queue();
        }
    }
}
