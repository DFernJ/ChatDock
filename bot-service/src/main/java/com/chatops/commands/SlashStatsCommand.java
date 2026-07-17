package com.chatops.commands;

import com.chatops.dto.ContainerStatsResponse;
import com.chatops.service.ApiService;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.springframework.stereotype.Component;

@Component
public class SlashStatsCommand extends AbstractContainerLifecycleCommand {

    private final ApiService apiService;

    public SlashStatsCommand(ApiService apiService) {
        this.apiService = apiService;
    }

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    protected String getActionVerb() {
        return "show stats for";
    }

    @Override
    protected void performAction(ModalInteractionEvent event, String containerName, long discordId) {
        ContainerStatsResponse stats = apiService.fetchStats(containerName, discordId);
        if (stats == null) {
            event.getHook().editOriginal("No stats available for **" + containerName + "**.").queue();
            return;
        }

        String message = """
                **Stats for %s**
                CPU: **%.0f%%**
                Memory: **%.0f%%** (%s / %s)
                Disk read: **%s**
                Disk write: **%s**""".formatted(
                containerName,
                stats.cpuPercent(),
                stats.memPercent(),
                formatBytes(stats.memUsedBytes()),
                formatBytes(stats.memLimitBytes()),
                formatBytes(stats.diskReadBytes()),
                formatBytes(stats.diskWriteBytes()));

        event.getHook().editOriginal(message).queue();
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unitIndex = Math.min(units.length - 1, (int) (Math.log(bytes) / Math.log(1024)));
        double value = bytes / Math.pow(1024, unitIndex);
        return unitIndex == 0 ? "%.0f %s".formatted(value, units[unitIndex]) : "%.1f %s".formatted(value, units[unitIndex]);
    }
}
