package com.chatops.commands;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;

import java.util.List;

public interface IModalHandler {

    List<String> getModalIds();

    void executeModal(ModalInteractionEvent event);
}
