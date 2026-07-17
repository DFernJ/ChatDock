package com.chatops.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;

public interface ISlashCommands {

    String getName();

    SlashCommandData getCommandData();

    void execute(SlashCommandInteractionEvent event);

}