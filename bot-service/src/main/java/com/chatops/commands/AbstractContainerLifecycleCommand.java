package com.chatops.commands;

import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractContainerLifecycleCommand implements ISlashCommands, IModalHandler {

    private static final Logger log = LoggerFactory.getLogger(AbstractContainerLifecycleCommand.class);

    private static final String MANAGED_CONTAINERS_CATEGORY = "Managed Containers";
    private static final String CONFIRM_INPUT = "confirm";
    protected static final int MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final int MAX_FILES_PER_MESSAGE = 10;

    protected abstract String getActionVerb();

    protected abstract void performAction(ModalInteractionEvent event, String containerName, long discordId) throws Exception;

    private String modalId() {
        return "container_" + getName() + "_modal";
    }

    @Override
    public SlashCommandData getCommandData() {
        return Commands.slash(getName(), "Runs " + getActionVerb() + " on the container matching this channel.");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        log.info("/{} requested by discordId={}", getName(), event.getUser().getIdLong());
        String containerName = resolveContainerName(event.getChannel());
        if (containerName == null) {
            event.reply("This command can only be used in a channel under the **" + MANAGED_CONTAINERS_CATEGORY + "** category.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        TextInput confirmInput = TextInput.create(CONFIRM_INPUT, TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder(truncate("Type \"" + containerName + "\" to confirm", TextInput.MAX_PLACEHOLDER_LENGTH))
                .build();

        Modal modal = Modal.create(modalId(), truncate("Confirm " + getActionVerb() + ": " + containerName, Modal.MAX_TITLE_LENGTH))
                .addComponents(Label.of("Container name", confirmInput))
                .build();

        event.replyModal(modal).queue();
    }

    @Override
    public List<String> getModalIds() {
        return List.of(modalId());
    }

    @Override
    public void executeModal(ModalInteractionEvent event) {
        String containerName = resolveContainerName(event.getChannel());
        if (containerName == null) {
            event.reply("This command can only be used in a channel under the **" + MANAGED_CONTAINERS_CATEGORY + "** category.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        String typed = event.getValue(CONFIRM_INPUT).getAsString().trim();
        if (!typed.equalsIgnoreCase(containerName)) {
            event.reply("Confirmation text didn't match **" + containerName + "**. Action cancelled.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        event.deferReply(true).queue();
        long discordId = event.getUser().getIdLong();
        log.info("Confirmed {} on container '{}' requested by discordId={}", getActionVerb(), containerName, discordId);
        try {
            performAction(event, containerName, discordId);
        } catch (HttpClientErrorException e) {
            String body = e.getResponseBodyAsString();
            log.warn("{} on container '{}' failed for discordId={}: {}", getActionVerb(), containerName, discordId, e.getStatusCode());
            event.getHook().editOriginal(body.isBlank() ? "Something went wrong while running this action. Please try again." : body).queue();
        } catch (Exception e) {
            log.error("{} on container '{}' failed for discordId={}", getActionVerb(), containerName, discordId, e);
            event.getHook().editOriginal("Something went wrong while running this action. Please try again.").queue();
        }
    }

    protected void sendAsFiles(ModalInteractionEvent event, String introMessage, byte[] content, String filenamePrefix) {
        List<FileUpload> files = splitIntoFiles(content, filenamePrefix);

        event.getHook().editOriginal(introMessage).setFiles(files.subList(0, Math.min(MAX_FILES_PER_MESSAGE, files.size()))).queue();
        for (int offset = MAX_FILES_PER_MESSAGE; offset < files.size(); offset += MAX_FILES_PER_MESSAGE) {
            List<FileUpload> batch = files.subList(offset, Math.min(offset + MAX_FILES_PER_MESSAGE, files.size()));
            event.getHook().sendFiles(batch).setEphemeral(true).queue();
        }
    }

    private List<FileUpload> splitIntoFiles(byte[] content, String filenamePrefix) {
        int totalParts = Math.max(1, (int) Math.ceil(content.length / (double) MAX_FILE_SIZE));
        List<FileUpload> files = new ArrayList<>();
        int offset = 0;
        int part = 1;
        do {
            int end = Math.min(offset + MAX_FILE_SIZE, content.length);
            byte[] chunk = Arrays.copyOfRange(content, offset, end);
            String filename = totalParts > 1 ? filenamePrefix + "-part" + part + ".txt" : filenamePrefix + ".txt";
            files.add(FileUpload.fromData(chunk, filename));
            offset = end;
            part++;
        } while (offset < content.length);
        return files;
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) : text;
    }

    private String resolveContainerName(MessageChannelUnion channel) {
        if (channel == null || channel.getType() != ChannelType.TEXT) {
            return null;
        }
        TextChannel textChannel = channel.asTextChannel();
        Category parent = textChannel.getParentCategory();
        if (parent == null || !MANAGED_CONTAINERS_CATEGORY.equalsIgnoreCase(parent.getName())) {
            return null;
        }
        return textChannel.getName();
    }
}
