package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

@Component
public class SlashRestartCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashRestartCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "restart";
    }

    @Override
    protected String getActionVerb() {
        return "restart";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        apiService.restartContainer(containerName, discordId);
        event.getHook().editOriginal("Container **" + containerName + "** restarted.").queue();
    }
}
