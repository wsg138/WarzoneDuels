from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


listener_path = Path("src/main/java/dev/minecraft/warzoneduels/adapter/bukkit/listener/DuelListener.java")
listener = listener_path.read_text()
listener = replace_once(
    listener,
    """        if (victoryMomentDeath) {
            event.setKeepInventory(true);
        }
""",
    """        if (victoryMomentDeath) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
        }
""",
    "preserve victory experience fallback",
)
listener_path.write_text(listener)

service_path = Path("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java")
service = service_path.read_text()
service = replace_once(
    service,
    """    private Set<UUID> activeParticipantIds() {
        return Set.copyOf(activeParticipantIndex);
    }
""",
    """    private Set<UUID> activeParticipantIds() {
        if (activeDuel == null) {
            return Set.of();
        }
        UUID participantOne = activeDuel.participantOne().playerId();
        UUID participantTwo = activeDuel.participantTwo().playerId();
        boolean firstEliminated = eliminatedParticipantIds.contains(participantOne);
        boolean secondEliminated = eliminatedParticipantIds.contains(participantTwo);
        if (firstEliminated && secondEliminated) {
            return Set.of();
        }
        if (firstEliminated) {
            return Set.of(participantTwo);
        }
        if (secondEliminated) {
            return Set.of(participantOne);
        }
        return Set.of(participantOne, participantTwo);
    }
""",
    "derive spectator participant snapshot",
)
service_path.write_text(service)
