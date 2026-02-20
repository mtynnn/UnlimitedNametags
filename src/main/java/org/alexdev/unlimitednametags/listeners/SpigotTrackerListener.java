package org.alexdev.unlimitednametags.listeners;

import com.google.common.collect.Sets;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class SpigotTrackerListener implements Listener {

    private final UnlimitedNameTags plugin;

    public SpigotTrackerListener(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        startCheckTask();
    }

    private void startCheckTask() {
        plugin.getTaskScheduler().runTaskTimer(() -> {
            for (Player target : Bukkit.getOnlinePlayers()) {
                final Set<UUID> trackedByCache = plugin.getTrackerManager().getTrackers(target.getUniqueId());
                final Set<UUID> current = target.getTrackedBy().stream()
                        .map(Player::getUniqueId)
                        .collect(java.util.stream.Collectors.toSet());

                final Set<UUID> toRemove = Sets.difference(trackedByCache, current);
                final Set<UUID> toAdd = Sets.difference(current, trackedByCache);

                toRemove.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(watcher -> plugin.getTrackerManager().handleRemove(watcher, target));

                toAdd.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .forEach(watcher -> plugin.getTrackerManager().handleAdd(watcher, target));
            }
        }, 0, 5);
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        plugin.getTrackerManager().handleQuit(player);
    }

}
