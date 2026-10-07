package dev.minecraft.warzoneduels.app;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Adapter ordering contracts; real Paper lifecycle acceptance remains separate. */
class PendingDeathTransitionTest {
    private String source() throws Exception {
        return Files.readString(Path.of("src/main/java/dev/minecraft/warzoneduels/app/DuelService.java"))
            .replace("\r\n", "\n");
    }

    @Test void disableDrainsCapturedDropsBeforeCancellingOrSaving() throws Exception {
        String text = source();
        String disable = text.substring(text.indexOf("public void disable(boolean serverStopping)"),
                                       text.indexOf("public void reloadConfig()"));
        int drain = disable.indexOf("flushPendingDeathsBeforeTransition();");
        assertTrue(drain >= 0, "Captured deaths must be resolved before reload loses their drops");
        assertTrue(drain < disable.indexOf("cancelDeathResolutionTask();"));
        assertTrue(drain < disable.indexOf("saveActiveDuelSync("));
    }

    @Test void terminalTransitionHonorsAnAlreadyResolvedDeathOutcome() throws Exception {
        String text = source();
        String terminal = text.substring(text.indexOf("private void concludeDuel(\n        UUID winnerId,"),
                                        text.indexOf("private void finishConcludedDuel("));
        assertTrue(terminal.contains("flushPendingDeathsBeforeTransition();"));
        assertTrue(terminal.indexOf("flushPendingDeathsBeforeTransition();") < terminal.indexOf("duelEnding = true;"));
        String afterDrain = terminal.substring(terminal.indexOf("flushPendingDeathsBeforeTransition();"));
        assertTrue(afterDrain.indexOf("if (activeDuel == null || duelEnding)") < afterDrain.indexOf("duelEnding = true;"));
    }

    @Test void synchronousDrainCancelsTheScheduledCallbackFirst() throws Exception {
        String text = source();
        int start = text.indexOf("private void flushPendingDeathsBeforeTransition()");
        assertTrue(start >= 0, "Lifecycle transitions need a shared drain boundary");
        String drain = text.substring(start, text.indexOf("private void resolvePendingDeaths()", start));
        assertTrue(drain.contains("!pendingDeaths.isEmpty()"));
        assertTrue(drain.indexOf("cancelDeathResolutionTask();") < drain.indexOf("resolvePendingDeaths();"));
    }
}
