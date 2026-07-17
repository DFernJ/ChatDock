package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

@Component
public class SlashStopCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashStopCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "stop";
    }

    @Override
    protected String getActionVerb() {
        return "stop";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        apiService.stopContainer(containerName, discordId);
        event.getHook().editOriginal("Container **" + containerName + "** stopped.").queue();
    }
}
