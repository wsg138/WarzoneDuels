package dev.minecraft.warzoneduels.integration;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.adapter.bukkit.integration.CombatLogXCombatTagPort;
import dev.minecraft.warzoneduels.adapter.economy.VaultEconomyPort;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionalIntegrationBinaryIsolationTest {
    @Test
    void optionalApisAreNotHardLinked() throws Exception {
        assertNoClassConstant(WarzoneDuelsPlugin.class, "net/milkbowl/vault/");
        assertNoClassConstant(VaultEconomyPort.class, "net/milkbowl/vault/");
        assertNoClassConstant(CombatLogXCombatTagPort.class, "com/github/sirblobman/combatlogx/");
        assertNoClassConstant(
            Class.forName("dev.minecraft.warzoneduels.app.EnthusiaTagsWatcherDisplayHook"),
            "org/enthusia/tags/"
        );
        assertNoClassConstant(
            Class.forName("dev.minecraft.warzoneduels.app.WatcherTeleportCancellationHook"),
            "org/enthusia/teleport/"
        );
    }

    private void assertNoClassConstant(Class<?> type, String forbiddenPrefix) throws IOException {
        String resource = "/" + type.getName().replace('.', '/') + ".class";
        try (InputStream raw = type.getResourceAsStream(resource)) {
            assertNotNull(raw, resource);
            DataInputStream input = new DataInputStream(raw);
            if (input.readInt() != 0xCAFEBABE) {
                throw new IOException("Invalid class file: " + resource);
            }
            input.readUnsignedShort();
            input.readUnsignedShort();
            int count = input.readUnsignedShort();
            String[] utf8 = new String[count];
            int[] classNameIndexes = new int[count];
            for (int index = 1; index < count; index++) {
                int tag = input.readUnsignedByte();
                switch (tag) {
                    case 1 -> utf8[index] = input.readUTF();
                    case 3, 4 -> input.skipNBytes(4);
                    case 5, 6 -> {
                        input.skipNBytes(8);
                        index++;
                    }
                    case 7 -> classNameIndexes[index] = input.readUnsignedShort();
                    case 8, 16, 19, 20 -> input.skipNBytes(2);
                    case 9, 10, 11, 12, 17, 18 -> input.skipNBytes(4);
                    case 15 -> input.skipNBytes(3);
                    default -> throw new IOException("Unknown constant-pool tag " + tag + " in " + resource);
                }
            }
            for (int nameIndex : classNameIndexes) {
                if (nameIndex <= 0 || nameIndex >= utf8.length) {
                    continue;
                }
                String className = utf8[nameIndex];
                assertFalse(
                    className != null && className.startsWith(forbiddenPrefix),
                    type.getName() + " hard-links optional API class " + className
                );
            }
        }
    }
}
