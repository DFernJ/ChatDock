package com.chatops.commands;

import com.chatops.service.ApiService;
import com.chatops.service.ContainerLifecycleNotifier;
import com.chatops.service.LinkAccountsProvisioner;
import net.dv8tion.jda.api.events.guild.GuildReadyEvent;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class CommandManager extends ListenerAdapter {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);

    private final List<ISlashCommands> slashCommands;
    private final List<IButtonHandler> buttonHandlers;
    private final List<IModalHandler> modalHandlers;
    private final LinkAccountsProvisioner linkAccountsProvisioner;
    private final ContainerLifecycleNotifier containerLifecycleNotifier;
    private final ApiService apiService;

    public CommandManager(List<ISlashCommands> slashCommands, List<IButtonHandler> buttonHandlers,
                           List<IModalHandler> modalHandlers, LinkAccountsProvisioner linkAccountsProvisioner,
                           ContainerLifecycleNotifier containerLifecycleNotifier, ApiService apiService) {
        this.slashCommands = slashCommands;
        this.buttonHandlers = buttonHandlers;
        this.modalHandlers = modalHandlers;
        this.linkAccountsProvisioner = linkAccountsProvisioner;
        this.containerLifecycleNotifier = containerLifecycleNotifier;
        this.apiService = apiService;
    }

    @Override
    public void onGuildReady(GuildReadyEvent event) {
        List<SlashCommandData> dataSlashCommands = new ArrayList<>();
        for (ISlashCommands slashCommand: slashCommands) {
            dataSlashCommands.add(slashCommand.getCommandData());
        }

        event.getGuild().updateCommands().addCommands(dataSlashCommands).queue();
        linkAccountsProvisioner.ensureLinkAccountsChannel(event.getGuild());

        try {
            containerLifecycleNotifier.reconcileManagedContainers(event.getGuild(), apiService.fetchManagedContainerNames());
        } catch (Exception e) {
            log.warn("Could not reconcile Managed Containers channels for guild={}: {}", event.getGuild().getId(), e.getMessage());
        }
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
