from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


plugin_path = Path("src/main/java/dev/minecraft/warzoneduels/WarzoneDuelsPlugin.java")
plugin = plugin_path.read_text()
plugin = replace_once(
    plugin,
    "import dev.minecraft.warzoneduels.adapter.bukkit.listener.DefeatedRespawnGuard;\n",
    "",
    "remove guard import",
)
plugin = replace_once(
    plugin,
    "        getServer().getPluginManager().registerEvents(new DefeatedRespawnGuard(this, activeDuelService), this);\n",
    "",
    "remove guard registration",
)
plugin_path.write_text(plugin)

service_path = Path("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")
service = service_path.read_text()

service = replace_once(
    service,
    """        if (duelEnding && activeDuel != null) {
            teleportOnlineParticipantsToExit(activeDuel);
            activeDuel = null;
""",
    """        if (duelEnding && activeDuel != null) {
            ActiveDuel endingDuel = activeDuel;
            teleportOnlineParticipantsToExit(endingDuel);
            clearRespawnMarkerIfLiving(endingDuel.participantOne().playerId());
            clearRespawnMarkerIfLiving(endingDuel.participantTwo().playerId());
            activeDuel = null;
""",
    "clean conclusion markers on disable",
)

service = replace_once(
    service,
    """    public boolean isInActiveDuel(UUID playerId) {
        return activeDuel != null && activeDuel.contains(playerId);
    }
""",
    """    public boolean isInActiveDuel(UUID playerId) {
        return activeDuel != null
            && activeDuel.contains(playerId)
            && !isEliminatedDuringConclusion(playerId);
    }

    public boolean shouldCancelVictoryMomentDamage(Player player) {
        return player != null
            && duelEnding
            && activeDuel != null
            && activeDuel.contains(player.getUniqueId())
            && !respawnToSpawn.contains(player.getUniqueId());
    }

    public boolean handleVictoryMomentDeath(Player player) {
        requirePrimaryThread();
        if (player == null || !duelEnding || activeDuel == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!activeDuel.contains(playerId) || respawnToSpawn.contains(playerId)) {
            return false;
        }
        respawnToSpawn.add(playerId);
        activeParticipantIndex.remove(playerId);
        return true;
    }

    private boolean isEliminatedDuringConclusion(UUID playerId) {
        return duelEnding && respawnToSpawn.contains(playerId);
    }
""",
    "active participant semantics",
)

service = replace_once(
    service,
    """        if (activeDuel == null) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
""",
    """        if (activeDuel == null || duelEnding) {
            sendMessage(player, "messages.not-in-duel");
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
""",
    "reject draw during conclusion",
)

service = replace_once(
    service,
    """        if (isInActiveDuel(player.getUniqueId())) {
            sendMessageOrFallback(player, "messages.duel-watch-participant", ChatColor.RED + "You are already participating in this duel.");
            return;
        }
""",
    """        if (activeDuel.contains(player.getUniqueId())) {
            sendMessageOrFallback(player, "messages.duel-watch-participant", ChatColor.RED + "You are already participating in this duel.");
            return;
        }
""",
    "block defeated participant from watch mode",
)

service = replace_once(
    service,
    """        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        disconnectSnapshots.put(player.getUniqueId(), loadoutArchiveStore.capture(player));
""",
    """        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null || duelEnding) {
            return;
        }
        disconnectSnapshots.put(player.getUniqueId(), loadoutArchiveStore.capture(player));
""",
    "ignore conclusion quits",
)

service = replace_once(
    service,
    """        if (activeDuel == null) {
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        participant.setDisconnectDeadlineEpochMs(null);
""",
    """        if (activeDuel == null) {
            clearCompletedRespawnMarker(player);
            return;
        }
        MatchParticipant participant = activeDuel.participant(player.getUniqueId());
        if (participant == null) {
            return;
        }
        if (duelEnding) {
            if (respawnToSpawn.contains(player.getUniqueId())) {
                teleportToExit(player);
            }
            return;
        }
        participant.setDisconnectDeadlineEpochMs(null);
""",
    "prevent conclusion rejoin teleport",
)

service = replace_once(
    service,
    """        if (respawnToSpawn.remove(playerId) || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
""",
    """        boolean duelRespawn = respawnToSpawn.contains(playerId);
        if (duelRespawn || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
        if (duelRespawn && !isEliminatedDuringConclusion(playerId)) {
            respawnToSpawn.remove(playerId);
        }
""",
    "retain conclusion respawn marker",
)

service = replace_once(
    service,
    """        if (activeDuel == null) {
            return;
        }
        MatchParticipant dead = activeDuel.participant(player.getUniqueId());
""",
    """        if (activeDuel == null || duelEnding) {
            return;
        }
        MatchParticipant dead = activeDuel.participant(player.getUniqueId());
""",
    "reject duplicate conclusion death",
)

service = replace_once(
    service,
    """        respawnToSpawn.add(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
""",
    """        respawnToSpawn.add(dead.playerId());
        activeParticipantIndex.remove(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
""",
    "remove loser restrictions",
)

service = replace_once(
    service,
    """        if (winner != null) {
            healAfterDuel(winner);
            teleportToExit(winner);
        }
        Player first = Bukkit.getPlayer(participantOne);
        Player second = Bukkit.getPlayer(participantTwo);
        if (first != null && first.isOnline() && !respawnToSpawn.contains(participantOne)) {
            teleportToExit(first);
        }
        if (second != null && second.isOnline() && !respawnToSpawn.contains(participantTwo)) {
            teleportToExit(second);
        }

        activeDuel = null;
""",
    """        if (winner != null) {
            healAfterDuel(winner);
        }
        finishParticipantExit(participantOne);
        finishParticipantExit(participantTwo);

        activeDuel = null;
""",
    "finish participant exit",
)

service = replace_once(
    service,
    """    private void teleportOnlineParticipantsToExit(ActiveDuel duel) {
""",
    """    private void finishParticipantExit(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }
        teleportToExit(player);
        respawnToSpawn.remove(playerId);
    }

    private void clearCompletedRespawnMarker(Player player) {
        if (player == null || player.isDead() || !respawnToSpawn.remove(player.getUniqueId())) {
            return;
        }
        teleportToExit(player);
    }

    private void clearRespawnMarkerIfLiving(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline() && !player.isDead()) {
            respawnToSpawn.remove(playerId);
        }
    }

    private void teleportOnlineParticipantsToExit(ActiveDuel duel) {
""",
    "participant exit helpers",
)

service = replace_once(
    service,
    """    private void enforceParticipantContainment(UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
""",
    """    private void enforceParticipantContainment(UUID playerId) {
        if (!isInActiveDuel(playerId)) {
            return;
        }
        Player player = Bukkit.getPlayer(playerId);
""",
    "skip eliminated containment",
)

service = replace_once(
    service,
    """    private void rebuildParticipantIndex() {
        activeParticipantIndex.clear();
        if (activeDuel != null) {
            activeParticipantIndex.add(activeDuel.participantOne().playerId());
            activeParticipantIndex.add(activeDuel.participantTwo().playerId());
        }
    }
""",
    """    private void rebuildParticipantIndex() {
        activeParticipantIndex.clear();
        if (activeDuel == null) {
            return;
        }
        for (UUID playerId : List.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId())) {
            if (!isEliminatedDuringConclusion(playerId)) {
                activeParticipantIndex.add(playerId);
            }
        }
    }
""",
    "rebuild active participant index",
)

service = replace_once(
    service,
    """    private Set<UUID> activeParticipantIds() {
        if (activeDuel == null) {
            return Set.of();
        }
        return Set.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId());
    }
""",
    """    private Set<UUID> activeParticipantIds() {
        return Set.copyOf(activeParticipantIndex);
    }
""",
    "spectator participant snapshot",
)

service_path.write_text(service)

listener_path = Path("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/listener/DuelListener.java")
listener = listener_path.read_text()
listener = replace_once(
    listener,
    """        if (duelService.shouldBlockWatcherAction(player) || duelService.shouldCancelArenaSpectatorDamage(player)) {
            event.setCancelled(true);
        }
""",
    """        if (duelService.shouldCancelVictoryMomentDamage(player)
            || duelService.shouldBlockWatcherAction(player)
            || duelService.shouldCancelArenaSpectatorDamage(player)) {
            event.setCancelled(true);
        }
""",
    "protect victory participant",
)
listener = replace_once(
    listener,
    """        boolean duelDeath = duelService.isInActiveDuel(event.getEntity().getUniqueId());
        boolean forcedDeath = duelService.consumePendingForcedDeath(event.getEntity().getUniqueId());
        if (!duelDeath && !forcedDeath) {
            return;
        }
""",
    """        boolean victoryMomentDeath = duelService.handleVictoryMomentDeath(event.getEntity());
        boolean duelDeath = duelService.isInActiveDuel(event.getEntity().getUniqueId());
        boolean forcedDeath = duelService.consumePendingForcedDeath(event.getEntity().getUniqueId());
        if (!duelDeath && !forcedDeath && !victoryMomentDeath) {
            return;
        }
""",
    "victory death fallback",
)
listener_path.write_text(listener)

guard_path = Path("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/listener/DefeatedRespawnGuard.java")
if not guard_path.exists():
    raise SystemExit("guard source is missing")
guard_path.unlink()

Path(".github/workflows/validate-respawn-fix.yml").unlink()
Path("scripts/apply_respawn_lifecycle_fix.py").unlink()
