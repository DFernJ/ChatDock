package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SlashLogsCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashLogsCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "logs";
    }

    @Override
    protected String getActionVerb() {
        return "fetch logs for";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        String logs = apiService.fetchLogs(containerName, discordId);
        if (logs == null || logs.isBlank()) {
            event.getHook().editOriginal("No logs available for **" + containerName + "**.").queue();
            return;
        }

        sendAsFiles(event, "Logs for **" + containerName + "**:", logs.getBytes(StandardCharsets.UTF_8), containerName + "-logs");
    }
}
