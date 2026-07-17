package com.chatops.commands;

import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

@Component
public class SlashDeleteCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashDeleteCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "delete";
    }

    @Override
    protected String getActionVerb() {
        return "delete";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        apiService.deleteContainer(containerName, discordId);
        event.getHook().editOriginal("Container **" + containerName + "** deleted.").queue();
    }
}
