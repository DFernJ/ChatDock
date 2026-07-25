package com.chatops.commands;

import com.chatops.service.ApiService;
import com.chatops.util.CommandAuditLog;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.modals.Modal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Arrays;
import java.util.List;

@Component
public class SlashLinkCommand implements ISlashCommands, IButtonHandler, IModalHandler {

    private static final Logger log = LoggerFactory.getLogger(SlashLinkCommand.class);
    private static final String LINK_ACCOUNTS_CHANNEL = "link-accounts";
    private static final String CODE_MODAL = "link_code_modal";
    private static final String CODE_INPUT = "code";
    private static final String CONFIRM_BTN = "link_confirm_btn";
    private static final String CANCEL_BTN = "link_cancel_btn";

    private final ApiService apiService;

    public SlashLinkCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "link";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Links your Discord account to your ChatOps account using a link code.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        CommandAuditLog.logCommandRequested(getName(), event.getUser().getIdLong());
        if (!event.isFromGuild() || event.getChannel() == null || !LINK_ACCOUNTS_CHANNEL.equals(event.getChannel().getName())) {
            event.reply("This command can only be used in the #" + LINK_ACCOUNTS_CHANNEL + " channel.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TextInput codeInput = TextInput.create(CODE_INPUT, TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("Enter your link code")
                .build();

        Modal modal = Modal.create(CODE_MODAL, "Link your account")
                .addComponents(Label.of("Link code", codeInput))
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public List<String> getModalIds() {
        return List.of(CODE_MODAL);
    }

    @Override
    public void executeModal(ModalInteractionEvent event) {
        String code = event.getValue(CODE_INPUT).getAsString();
        log.info("Link code submitted by discordId={}", event.getUser().getIdLong());

        Button confirmButton = Button.success(CONFIRM_BTN + "|" + code, "Link");
        Button cancelButton = Button.danger(CANCEL_BTN, "Cancel");

        event.reply("Link this Discord account using code **" + code + "**?")
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
        long discordId = event.getUser().getIdLong();
        if (event.getComponentId().startsWith(CONFIRM_BTN)) {
            String code = event.getComponentId().split("\\|")[1];
            event.deferEdit().queue();
            try {
                String username = apiService.linkDiscordAccount(code, discordId, event.getUser().getName());
                log.info("Linked discordId={} to username={}", discordId, username);
                event.getHook().editOriginal("Your Discord account is now linked to **" + username + "**.")
                        .setComponents(List.of())
                        .queue();
            } catch (HttpClientErrorException e) {
                log.warn("Link confirmation failed for discordId={}: {}", discordId, e.getStatusCode());
                String body = e.getResponseBodyAsString();
                String message = body.isBlank() ? "Something went wrong while linking your account. Please try again." : body;
                event.getHook().editOriginal(message).setComponents(List.of()).queue();
            } catch (Exception e) {
                log.error("Link confirmation failed for discordId={}", discordId, e);
                event.getHook().editOriginal("Something went wrong while linking your account. Please try again.")
                        .setComponents(List.of())
                        .queue();
            }
        } else if (event.getComponentId().equals(CANCEL_BTN)) {
            log.info("Link cancelled by discordId={}", discordId);
            event.editMessage("Link cancelled.").setComponents(List.of()).queue();
        }
    }
}
