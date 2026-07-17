package com.chatops.commands;

import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import java.util.List;

public interface IButtonHandler {

    List<String> getButtonIds();

    void executeButton(ButtonInteractionEvent event);
}
