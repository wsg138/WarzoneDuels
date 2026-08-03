from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


service_path = Path("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")
service = service_path.read_text()

service = replace_once(
    service,
    """    private final Set<UUID> respawnToSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recoveryTeleportIds = ConcurrentHashMap.newKeySet();
""",
    """    private final Set<UUID> respawnToSpawn = ConcurrentHashMap.newKeySet();
    private final Set<UUID> eliminatedParticipantIds = ConcurrentHashMap.newKeySet();
    private final Set<UUID> recoveryTeleportIds = ConcurrentHashMap.newKeySet();
""",
    "add eliminated participant state",
)

service = replace_once(
    service,
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
    """    public boolean isInActiveDuel(UUID playerId) {
        return activeDuel != null
            && activeDuel.contains(playerId)
            && !eliminatedParticipantIds.contains(playerId);
    }

    public boolean shouldCancelVictoryMomentDamage(Player player) {
        return player != null
            && duelEnding
            && activeDuel != null
            && activeDuel.contains(player.getUniqueId())
            && !eliminatedParticipantIds.contains(player.getUniqueId());
    }

    public boolean handleVictoryMomentDeath(Player player) {
        requirePrimaryThread();
        if (player == null || !duelEnding || activeDuel == null) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        if (!activeDuel.contains(playerId) || !eliminatedParticipantIds.add(playerId)) {
            return false;
        }
        respawnToSpawn.add(playerId);
        activeParticipantIndex.remove(playerId);
        return true;
    }
""",
    "separate active and respawn state",
)

service = replace_once(
    service,
    """        if (duelEnding) {
            if (respawnToSpawn.contains(player.getUniqueId())) {
                teleportToExit(player);
            }
            return;
        }
""",
    """        if (duelEnding) {
            if (eliminatedParticipantIds.contains(player.getUniqueId())) {
                teleportToExit(player);
            }
            return;
        }
""",
    "use elimination state on join",
)

service = replace_once(
    service,
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
    """        if (respawnToSpawn.remove(playerId) || recoveryTeleportIds.contains(playerId)) {
            Location location = exitLocation();
            if (location != null) {
                event.setRespawnLocation(location);
            }
        }
""",
    "consume respawn route once",
)

service = replace_once(
    service,
    """        respawnToSpawn.add(dead.playerId());
        activeParticipantIndex.remove(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
""",
    """        respawnToSpawn.add(dead.playerId());
        eliminatedParticipantIds.add(dead.playerId());
        activeParticipantIndex.remove(dead.playerId());
        concludeDuel(winnerPlayer, DuelEndReason.KILL, true);
""",
    "record eliminated participant",
)

service = replace_once(
    service,
    """        pendingForcedDeathIds.clear();
        duelCountdownActive = false;
""",
    """        pendingForcedDeathIds.clear();
        eliminatedParticipantIds.clear();
        duelCountdownActive = false;
""",
    "clear eliminated participants",
)

service = replace_once(
    service,
    """        for (UUID playerId : List.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId())) {
            if (!isEliminatedDuringConclusion(playerId)) {
                activeParticipantIndex.add(playerId);
            }
        }
""",
    """        for (UUID playerId : List.of(activeDuel.participantOne().playerId(), activeDuel.participantTwo().playerId())) {
            if (!eliminatedParticipantIds.contains(playerId)) {
                activeParticipantIndex.add(playerId);
            }
        }
""",
    "rebuild with eliminated state",
)

service_path.write_text(service)

listener_path = Path("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/listener/DuelListener.java")
listener = listener_path.read_text()
listener = replace_once(
    listener,
    """        ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (duelDeath) {
""",
    """        ArrayList<org.bukkit.inventory.ItemStack> drops = new ArrayList<>(event.getDrops());
        event.getDrops().clear();
        event.setDroppedExp(0);
        if (victoryMomentDeath) {
            event.setKeepInventory(true);
        }
        if (duelDeath) {
""",
    "preserve victory inventory fallback",
)
listener_path.write_text(listener)
