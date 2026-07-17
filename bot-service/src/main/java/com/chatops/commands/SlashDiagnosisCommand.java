package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class SlashDiagnosisCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashDiagnosisCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "diagnosis";
    }

    @Override
    protected String getActionVerb() {
        return "diagnose";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        String diagnosis = apiService.fetchDiagnosis(containerName, discordId);
        if (diagnosis == null || diagnosis.isBlank()) {
            event.getHook().editOriginal("No diagnosis available for **" + containerName + "**.").queue();
            return;
        }

        sendAsFiles(event, "AI diagnosis for **" + containerName + "**:", diagnosis.getBytes(StandardCharsets.UTF_8), containerName + "-diagnosis");
    }
}
