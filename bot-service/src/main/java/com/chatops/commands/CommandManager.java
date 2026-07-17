package com.chatops.commands;

import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommandManager extends ListenerAdapter {
    private final List<ISlashCommands> slashCommands;
    private final List<IButtonHandler> buttonHandlers;
    private final List<IModalHandler> modalHandlers;

    public CommandManager(List<ISlashCommands> slashCommands, List<IButtonHandler> buttonHandlers, List<IModalHandler> modalHandlers) {
        this.slashCommands = slashCommands;
        this.buttonHandlers = buttonHandlers;
        this.modalHandlers = modalHandlers;
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        List<SlashCommandData> dataSlashCommands = new ArrayList<>();
        for (ISlashCommands slashCommand: slashCommands) {
            dataSlashCommands.add(slashCommand.getCommandData());
        }

        event.getGuild().updateCommands().addCommands(dataSlashCommands).queue();
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        for (ISlashCommands slashCommand: slashCommands) {
            if (slashCommand.getName().equals(event.getName())) {
                slashCommand.execute(event);
                return;
            }
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        for (IButtonHandler handler : buttonHandlers) {
            if (handler.getButtonIds().contains(event.getComponentId().split("\\|")[0])) {
                handler.executeButton(event);
                return;
            }
        }
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        for (IModalHandler handler : modalHandlers) {
            if (handler.getModalIds().contains(event.getModalId())) {
                handler.executeModal(event);
                return;
            }
        }
    }
}
