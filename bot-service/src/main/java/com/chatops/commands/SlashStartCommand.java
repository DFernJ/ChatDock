package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

@Component
public class SlashStartCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashStartCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "start";
    }

    @Override
    protected String getActionVerb() {
        return "start";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        apiService.startContainer(containerName, discordId);
        event.getHook().editOriginal("Container **" + containerName + "** started.").queue();
    }
}
