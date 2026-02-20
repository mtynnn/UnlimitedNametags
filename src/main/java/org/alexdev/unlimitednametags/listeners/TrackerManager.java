package org.alexdev.unlimitednametags.listeners;

import lombok.Getter;
import org.alexdev.unlimitednametags.UnlimitedNameTags;
import org.alexdev.unlimitednametags.data.ConcurrentSetMultimap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TrackerManager {

    private final UnlimitedNameTags plugin;

    /**
     * Map associating a player (key) with the players they are tracking (value).
     * A -> [B, C] means A sees B and C.
     */
    @Getter
    private final ConcurrentSetMultimap<UUID, UUID> trackedPlayers;

    /**
     * Reverse map associating a player (key) with those who are tracking them
     * (value).
     * B -> [A] means B is seen by A.
     * Used to optimize reverse lookups.
     */
    private final ConcurrentSetMultimap<UUID, UUID> trackedBy;

    public TrackerManager(UnlimitedNameTags plugin) {
        this.plugin = plugin;
        this.trackedPlayers = new ConcurrentSetMultimap<>();
        this.trackedBy = new ConcurrentSetMultimap<>();
        loadTracker();
    }

    private void loadTracker() {
        Bukkit.getOnlinePlayers().forEach(player -> {
            // Unsafe call, but run only on startup
            List<Entity> nearbyEntities = player.getNearbyEntities(
                    Bukkit.getViewDistance() * 16,
                    256,
                    Bukkit.getViewDistance() * 16);

            for (Entity entity : nearbyEntities) {
                if (entity instanceof Player target) {
                    trackedPlayers.put(player.getUniqueId(), target.getUniqueId());
                    trackedBy.put(target.getUniqueId(), player.getUniqueId());
                }
            }
        });
    }

    public void onDisable() {
        trackedPlayers.clear();
        trackedBy.clear();
    }

    /**
     * Handles adding a target to a player's tracking list.
     * Updates both direct and reverse maps asynchronously.
     *
     * @param player The observer player.
     * @param target The target player being observed.
     */
    public void handleAdd(@NotNull Player player, @NotNull Player target) {
        if (player.hasMetadata("NPC") || target.hasMetadata("NPC"))
            return;
        if (target.hasPotionEffect(org.bukkit.potion.PotionEffectType.INVISIBILITY))
            return;

        if (trackedPlayers.containsEntry(player.getUniqueId(), target.getUniqueId())) {
            return;
        }

        trackedPlayers.put(player.getUniqueId(), target.getUniqueId());
        trackedBy.put(target.getUniqueId(), player.getUniqueId());

        plugin.getNametagManager().updateDisplay(player, target);
    }

    public void handleRemove(@NotNull Player player, @NotNull Player target) {
        removePlayerInternal(player, target);
    }

    private void removePlayerInternal(@NotNull Player player, @NotNull Player target) {
        trackedPlayers.remove(player.getUniqueId(), target.getUniqueId());
        trackedBy.remove(target.getUniqueId(), player.getUniqueId());
        plugin.getNametagManager().removeDisplay(player, target);
    }

    public void handleQuit(@NotNull Player player) {
        UUID quittingUuid = player.getUniqueId();

        final Set<UUID> watchers = trackedBy.removeAll(quittingUuid);
        for (UUID watcherUuid : watchers) {
            trackedPlayers.remove(watcherUuid, quittingUuid);
        }

        final Set<UUID> targets = trackedPlayers.removeAll(quittingUuid);
        for (UUID targetUuid : targets) {
            trackedBy.remove(targetUuid, quittingUuid);
        }
    }

    /**
     * Forces removal of all targets tracked by a specific player.
     * Useful during world changes or distant teleports.
     *
     * @param player The player to reset.
     */
    public void forceUntrack(@NotNull Player player) {
        Set<UUID> tracked = trackedPlayers.get(player.getUniqueId());
        if (tracked == null || tracked.isEmpty())
            return;

        // Copy to avoid ConcurrentModification during iteration if necessary,
        // although removePlayerInternal handles concurrency.
        Set<UUID> targetsSnapshot = new HashSet<>(tracked);

        Map<UUID, Player> onlinePlayers = plugin.getPlayerListener().getOnlinePlayers();

        for (UUID targetUuid : targetsSnapshot) {
            Player target = onlinePlayers.get(targetUuid);
            if (target != null) {
                removePlayerInternal(player, target);
            }
        }
    }

    /**
     * Retrieves the list of players currently tracking the specified target.
     * Uses the reverse map for O(1) performance.
     *
     * @param target The target player.
     * @return List of players seeing the target.
     */
    @NotNull
    public List<Player> getWhoTracks(@NotNull Player target) {
        final List<Player> trackers = new ArrayList<>();
        final Map<UUID, Player> onlinePlayers = plugin.getPlayerListener().getOnlinePlayers();

        for (UUID uuid : trackedBy.get(target.getUniqueId())) {
            Player p = onlinePlayers.get(uuid);
            if (p != null) {
                trackers.add(p);
            }
        }
        return trackers;
    }

    /**
     * Retrieves the UUIDs of players that the specified tracker is observing.
     *
     * @param uuid UUID of the tracker.
     * @return Set of UUIDs of the targets.
     */
    public Set<UUID> getTrackedPlayers(@NotNull UUID uuid) {
        return trackedPlayers.get(uuid);
    }

    @NotNull
    public Set<UUID> getTrackers(@NotNull UUID uuid) {
        return trackedBy.get(uuid);
    }
}
