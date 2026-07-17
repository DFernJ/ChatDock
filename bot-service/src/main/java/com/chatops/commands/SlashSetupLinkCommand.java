package com.chatops.commands;

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
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class SlashSetupLinkCommand implements ISlashCommands, IButtonHandler {

    private static final String CONFIRM_BTN = "setup_link_confirm_btn";
    private static final String CANCEL_BTN = "setup_link_cancel_btn";

    @Override
    public String getName() {
        return "setup-link";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Sets up the Link Accounts category and channel.")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("This command can only be used inside a server.").setEphemeral(true).queue();
            return;
        }

        Button confirmButton = Button.success(CONFIRM_BTN, "Confirm setup");
        Button cancelButton = Button.danger(CANCEL_BTN, "Cancel");

        event.reply("""
                Let's set up the **Link Accounts** category for **%s**.

                This will create a **Link Accounts** category with a text channel to link your accounts.

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

            event.editMessage("Setting up the Link Accounts category...")
                    .setComponents(List.of())
                    .queue();

            Category existingCategory = guild.getCategoriesByName("Link Accounts", true).stream().findFirst().orElse(null);
            if (existingCategory != null) {
                ensureTextChannel(existingCategory, "link-accounts");
            } else {
                guild.createCategory("Link Accounts")
                        .queue(category -> ensureTextChannel(category, "link-accounts"));
            }
        } else if (event.getComponentId().equals(CANCEL_BTN)) {
            event.editMessage("Setup cancelled.").setComponents(List.of()).queue();
        }
    }

    private void ensureTextChannel(Category category, String channelName) {
        boolean alreadyExists = category.getTextChannels().stream()
                .anyMatch(channel -> channel.getName().equalsIgnoreCase(channelName));
        if (!alreadyExists) {
            category.createTextChannel(channelName).queue();
        }
    }
}
