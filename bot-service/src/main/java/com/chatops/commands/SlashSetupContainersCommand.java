package com.chatops.commands;

import com.chatops.service.ApiService;
import com.chatops.util.ChannelNames;
import com.chatops.util.CommandAuditLog;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class SlashSetupContainersCommand implements ISlashCommands, IButtonHandler {

    private static final Logger log = LoggerFactory.getLogger(SlashSetupContainersCommand.class);
    private static final String CONFIRM_BTN = "setup_containers_confirm_btn";
    private static final String CANCEL_BTN = "setup_containers_cancel_btn";

    private final ApiService apiService;

    public SlashSetupContainersCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "setup-containers";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Sets up the Managed Containers category and channels.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CommandAuditLog.logCommandRequested(getName(), event.getUser().getIdLong());
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("This command can only be used inside a server.").setEphemeral(true).queue();
            return;
        }

        Button confirmButton = Button.success(CONFIRM_BTN, "Confirm setup");
        Button cancelButton = Button.danger(CANCEL_BTN, "Cancel");

        event.reply("""
                Let's set up the **Managed Containers** category for **%s**.

                This will create a **Managed Containers** category with one text channel per managed container.

                Do you want to continue?""".formatted(event.getGuild().getName()))
                .setEphemeral(true)
                .addComponents(ActionRow.of(confirmButton, cancelButton))
                .queue();
    }

    @Override
    public List<String> getButtonIds() {
        return Arrays.asList(CONFIRM_BTN, CANCEL_BTN);
    }

    @Override
    public void executeButton(ButtonInteractionEvent event) {
        if (event.getComponentId().equals(CONFIRM_BTN)) {
            Guild guild = event.getGuild();
            if (guild == null) {
                event.editMessage("This command can only be used inside a server.")
                        .setComponents(List.of())
                        .queue();
                return;
            }
            log.info("Confirmed Managed Containers setup for guild={} requested by discordId={}", guild.getId(), event.getUser().getIdLong());

            event.editMessage("Setting up the Managed Containers category... this may take a few seconds.")
                    .setComponents(List.of())
                    .queue();

            List<String> fetchedNames;
            try {
                fetchedNames = apiService.fetchContainerNames();
            } catch (Exception e) {
                log.error("Failed to fetch container names for setup in guild={}", guild.getId(), e);
                fetchedNames = List.of();
            }
            final List<String> containerNames = fetchedNames;

            Category existingCategory = guild.getCategoriesByName("Managed Containers", true).stream().findFirst().orElse(null);
            if (existingCategory != null) {
                createMissingContainerChannels(existingCategory, containerNames);
            } else {
                guild.createCategory("Managed Containers")
                        .queue(category -> createMissingContainerChannels(category, containerNames));
            }
        } else if (event.getComponentId().equals(CANCEL_BTN)) {
            log.info("Managed Containers setup cancelled by discordId={}", event.getUser().getIdLong());
            event.editMessage("Setup cancelled.").setComponents(List.of()).queue();
        }
    }

    private void createMissingContainerChannels(Category category, List<String> containerNames) {
        List<String> existingChannelNames = category.getTextChannels().stream()
                .map(channel -> channel.getName().toLowerCase())
                .toList();

        for (String containerName : containerNames) {
            String channelName = ChannelNames.sanitize(containerName);
            if (!existingChannelNames.contains(channelName)) {
                category.createTextChannel(channelName).queue();
            }
        }
    }
}
