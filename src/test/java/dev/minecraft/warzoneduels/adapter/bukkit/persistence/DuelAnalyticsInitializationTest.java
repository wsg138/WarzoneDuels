package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMatchType;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecord;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecordParticipant;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import sun.misc.Unsafe;
import static org.junit.jupiter.api.Assertions.*;

class DuelAnalyticsInitializationTest {
    @TempDir Path directory;

    @Test void failedSchemaInitializationClosesConnectionAndAllowsRealRetry() throws Exception {
        try (Connection failed = database(); Connection retry = database()) {
            AtomicInteger opens = new AtomicInteger();
            DuelAnalyticsStore store = new DuelAnalyticsStore(plugin(Logger.getAnonymousLogger()),
                    url -> opens.getAndIncrement() == 0 ? faulty(failed, false) : retry);
            store.enable();
            assertTrue(failed.isClosed(), "an opened connection must not be leaked on setup failure");
            assertNull(connection(store));
            store.enable();
            assertSame(retry, connection(store));
            UUID first = UUID.randomUUID(), second = UUID.randomUUID();
            store.insert(new DuelRecord("retry", 1, 2, 1, first, "A", second, "B", first, "A", second, "B",
                    "arena", "Arena", "default", "keep", DuelEndReason.DRAW, true, 0, 0,
                    DuelMatchType.NORMAL, 1, List.of(new DuelRecordParticipant(first, "A", 1, true),
                    new DuelRecordParticipant(second, "B", 2, false))));
            assertEquals(1, store.findRecent(10).size());
            store.disable();
            assertTrue(retry.isClosed());
        }
    }

    @Test void closeFailureDoesNotHideSetupFailureOrKeepConnectionReference() throws Exception {
        try (Connection failed = database()) {
            List<LogRecord> logs = new ArrayList<>();
            Logger logger = Logger.getAnonymousLogger();
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { logs.add(record); }
                @Override public void flush() { }
                @Override public void close() { }
            });
            DuelAnalyticsStore store = new DuelAnalyticsStore(plugin(logger), url -> faulty(failed, true));
            store.enable();
            assertNull(connection(store));
            Throwable diagnostic = logs.getLast().getThrown();
            assertNotNull(diagnostic, "the original setup error must remain available in diagnostics");
            assertEquals("injected schema failure", diagnostic.getMessage());
            assertEquals("injected close failure", diagnostic.getSuppressed()[0].getMessage());
        }
    }

    private static Connection database() throws SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID());
    }

    private static Connection faulty(Connection real, boolean failClose) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createStatement"))
                        throw new SQLException("injected schema failure");
                    if (failClose && method.getName().equals("close"))
                        throw new SQLException("injected close failure");
                    try { return method.invoke(real, args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
    }

    private static Connection connection(DuelAnalyticsStore store) throws Exception {
        Field field = DuelAnalyticsStore.class.getDeclaredField("connection");
        field.setAccessible(true);
        return (Connection) field.get(store);
    }

    private WarzoneDuelsPlugin plugin(Logger logger) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        var plugin = (WarzoneDuelsPlugin) ((Unsafe) field.get(null)).allocateInstance(WarzoneDuelsPlugin.class);
        Field folder = JavaPlugin.class.getDeclaredField("dataFolder");
        folder.setAccessible(true);
        folder.set(plugin, directory.toFile());
        Field loggerField = JavaPlugin.class.getDeclaredField("logger");
        loggerField.setAccessible(true);
        loggerField.set(plugin, logger);
        return plugin;
    }
}
