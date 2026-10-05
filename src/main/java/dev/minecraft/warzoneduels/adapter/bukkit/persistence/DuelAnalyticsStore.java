package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.DuelEndReason;
import dev.minecraft.warzoneduels.domain.DuelMatchType;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecord;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecordParticipant;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@SuppressWarnings("PMD.AvoidSynchronizedAtMethodLevel")
public final class DuelAnalyticsStore {
    private final WarzoneDuelsPlugin plugin;
    private final File databaseFile;
    private final ConnectionFactory connectionFactory;
    private Connection connection;

    public DuelAnalyticsStore(WarzoneDuelsPlugin plugin) {
        this(plugin, DriverManager::getConnection);
    }

    DuelAnalyticsStore(WarzoneDuelsPlugin plugin, ConnectionFactory connectionFactory) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "duel-analytics.mv.db");
        this.connectionFactory = connectionFactory;
    }

    @FunctionalInterface
    interface ConnectionFactory {
        Connection open(String url) throws SQLException;
    }

    public synchronized void enable() {
        if (connection != null) {
            return;
        }
        try {
            Class.forName("org.h2.Driver");
            Path parent = databaseFile.toPath().toAbsolutePath().getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            String basePath = databaseFile.toPath().toAbsolutePath().toString().replace("\\", "/");
            if (basePath.endsWith(".mv.db")) {
                basePath = basePath.substring(0, basePath.length() - ".mv.db".length());
            }
            connection = connectionFactory.open("jdbc:h2:file:" + basePath + ";AUTO_SERVER=FALSE;MODE=MySQL");
            connection.setAutoCommit(true);
            initializeSchema();
        } catch (Exception ex) {
            Connection failedConnection = connection;
            connection = null;
            if (failedConnection != null) {
                try {
                    failedConnection.close();
                } catch (SQLException closeFailure) {
                    ex.addSuppressed(closeFailure);
                }
            }
            plugin.getLogger().log(java.util.logging.Level.WARNING,
                    "Failed to open duel analytics store: " + ex.getMessage(), ex);
        }
    }

    public synchronized void disable() {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to close duel analytics store: " + ex.getMessage());
        } finally {
            connection = null;
        }
    }

    public synchronized void insert(DuelRecord record) {
        if (connection == null || record == null) {
            return;
        }
        try {
            insertAtomically(record);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to persist duel record: " + ex.getMessage());
        }
    }

    private void insertAtomically(DuelRecord record) throws SQLException {
        boolean autoCommit = connection.getAutoCommit();
        java.sql.Savepoint savepoint = null;
        if (autoCommit) {
            connection.setAutoCommit(false);
        } else {
            savepoint = connection.setSavepoint();
        }
        try {
            insertRecord(record);
            if (autoCommit) {
                connection.commit();
            } else {
                connection.releaseSavepoint(savepoint);
            }
        } catch (SQLException ex) {
            try {
                if (autoCommit) {
                    connection.rollback();
                } else {
                    connection.rollback(savepoint);
                    connection.releaseSavepoint(savepoint);
                }
            } catch (SQLException rollbackFailure) {
                ex.addSuppressed(rollbackFailure);
                // Never enable auto-commit on an uncertain transaction: that could persist partial data.
                Connection failedConnection = connection;
                connection = null;
                try {
                    failedConnection.close();
                } catch (SQLException closeFailure) {
                    ex.addSuppressed(closeFailure);
                }
            }
            throw ex;
        } finally {
            if (autoCommit && connection != null) {
                connection.setAutoCommit(true);
            }
        }
    }

    private void insertRecord(DuelRecord record) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO duel_records (
                reference, started_at, ended_at, duration_ms,
                player_one_id, player_one_name, player_two_id, player_two_name,
                winner_id, winner_name, loser_id, loser_name,
                map_id, map_name, ruleset, item_rules,
                end_reason, counted_as_match, spectator_count, wager,
                match_type, team_size
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setString(1, record.reference());
            statement.setLong(2, record.startedAtEpochMs());
            statement.setLong(3, record.endedAtEpochMs());
            statement.setLong(4, record.durationMillis());
            statement.setString(5, toString(record.playerOneId()));
            statement.setString(6, record.playerOneName());
            statement.setString(7, toString(record.playerTwoId()));
            statement.setString(8, record.playerTwoName());
            statement.setString(9, toString(record.winnerId()));
            statement.setString(10, record.winnerName());
            statement.setString(11, toString(record.loserId()));
            statement.setString(12, record.loserName());
            statement.setString(13, record.mapId());
            statement.setString(14, record.mapName());
            statement.setString(15, record.ruleset());
            statement.setString(16, record.itemRules());
            statement.setString(17, record.endReason().name());
            statement.setBoolean(18, record.countedAsMatch());
            statement.setInt(19, record.spectatorCount());
            statement.setDouble(20, record.wager());
            statement.setString(21, record.matchType().name());
            statement.setInt(22, record.teamSize());
            statement.executeUpdate();
            insertParticipants(record);
        }
    }

    public synchronized long countAll() {
        if (connection == null) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM duel_records")) {
            return readLong(statement);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
            return 0L;
        }
    }

    public synchronized long countSince(long sinceEpochMs) {
        if (connection == null) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM duel_records WHERE ended_at >= ?")) {
            statement.setLong(1, sinceEpochMs);
            return readLong(statement);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
            return 0L;
        }
    }

    public synchronized long countForPlayerSince(UUID playerId, long sinceEpochMs) {
        if (connection == null) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM duel_records
            WHERE ended_at >= ? AND (
                player_one_id = ? OR player_two_id = ? OR EXISTS (
                    SELECT 1 FROM duel_record_participants participant
                    WHERE participant.duel_reference = duel_records.reference AND participant.player_id = ?
                )
            )
            """)) {
            statement.setLong(1, sinceEpochMs);
            statement.setString(2, toString(playerId));
            statement.setString(3, toString(playerId));
            statement.setString(4, toString(playerId));
            return readLong(statement);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
            return 0L;
        }
    }

    public synchronized List<DuelRecord> findRecent(int limit) {
        List<DuelRecord> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT * FROM duel_records
            ORDER BY ended_at DESC
            LIMIT ?
            """)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(readRecord(resultSet));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
        }
        return results;
    }

    public synchronized List<DuelRecord> findRecentForPlayer(UUID playerId, int limit) {
        List<DuelRecord> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT * FROM duel_records
            WHERE player_one_id = ? OR player_two_id = ? OR EXISTS (
                SELECT 1 FROM duel_record_participants participant
                WHERE participant.duel_reference = duel_records.reference AND participant.player_id = ?
            )
            ORDER BY ended_at DESC
            LIMIT ?
            """)) {
            statement.setString(1, toString(playerId));
            statement.setString(2, toString(playerId));
            statement.setString(3, toString(playerId));
            statement.setInt(4, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    results.add(readRecord(resultSet));
                }
            }
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
        }
        return results;
    }

    public synchronized List<Map.Entry<String, Long>> topMapsSince(long sinceEpochMs, int limit) {
        List<Map.Entry<String, Long>> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT map_name, COUNT(*) AS total
            FROM duel_records
            WHERE ended_at >= ?
            GROUP BY map_name
            ORDER BY total DESC, map_name ASC
            LIMIT ?
            """)) {
            statement.setLong(1, sinceEpochMs);
            statement.setInt(2, limit);
            readEntries(statement, results);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
        }
        return results;
    }

    public synchronized List<Map.Entry<String, Long>> topRulesSince(long sinceEpochMs, int limit) {
        List<Map.Entry<String, Long>> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT ruleset, COUNT(*) AS total
            FROM duel_records
            WHERE ended_at >= ?
            GROUP BY ruleset
            ORDER BY total DESC, ruleset ASC
            LIMIT ?
            """)) {
            statement.setLong(1, sinceEpochMs);
            statement.setInt(2, limit);
            readEntries(statement, results);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
        }
        return results;
    }

    public synchronized List<Map.Entry<String, Long>> topEndReasonsSince(long sinceEpochMs, int limit) {
        List<Map.Entry<String, Long>> results = new ArrayList<>();
        if (connection == null) {
            return results;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT end_reason, COUNT(*) AS total
            FROM duel_records
            WHERE ended_at >= ?
            GROUP BY end_reason
            ORDER BY total DESC, end_reason ASC
            LIMIT ?
            """)) {
            statement.setLong(1, sinceEpochMs);
            statement.setInt(2, limit);
            readEntries(statement, results);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
        }
        return results;
    }

    public synchronized long countCancelledSince(long sinceEpochMs) {
        if (connection == null) {
            return 0L;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT COUNT(*) FROM duel_records
            WHERE ended_at >= ? AND counted_as_match = false
            """)) {
            statement.setLong(1, sinceEpochMs);
            return readLong(statement);
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to query duel analytics: " + ex.getMessage());
            return 0L;
        }
    }

    public synchronized void cleanupOlderThan(long cutoffEpochMs) {
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM duel_records WHERE ended_at < ?")) {
            statement.setLong(1, cutoffEpochMs);
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("Failed to prune duel analytics: " + ex.getMessage());
        }
    }

    private void initializeSchema() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS duel_records (
                    reference VARCHAR(40) PRIMARY KEY,
                    started_at BIGINT NOT NULL,
                    ended_at BIGINT NOT NULL,
                    duration_ms BIGINT NOT NULL,
                    player_one_id VARCHAR(36) NOT NULL,
                    player_one_name VARCHAR(64) NOT NULL,
                    player_two_id VARCHAR(36) NOT NULL,
                    player_two_name VARCHAR(64) NOT NULL,
                    winner_id VARCHAR(36),
                    winner_name VARCHAR(64),
                    loser_id VARCHAR(36),
                    loser_name VARCHAR(64),
                    map_id VARCHAR(64) NOT NULL,
                    map_name VARCHAR(64) NOT NULL,
                    ruleset VARCHAR(255) NOT NULL,
                    item_rules VARCHAR(255) NOT NULL,
                    end_reason VARCHAR(32) NOT NULL,
                    counted_as_match BOOLEAN NOT NULL,
                    spectator_count INT NOT NULL,
                    wager DOUBLE NOT NULL
                )
                """);
            statement.executeUpdate("ALTER TABLE duel_records ADD COLUMN IF NOT EXISTS match_type VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
            statement.executeUpdate("ALTER TABLE duel_records ADD COLUMN IF NOT EXISTS team_size INT NOT NULL DEFAULT 1");
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS duel_record_participants (
                    duel_reference VARCHAR(40) NOT NULL,
                    player_id VARCHAR(36) NOT NULL,
                    player_name VARCHAR(64) NOT NULL,
                    team_index INT NOT NULL,
                    winner BOOLEAN NOT NULL,
                    PRIMARY KEY (duel_reference, player_id),
                    FOREIGN KEY (duel_reference) REFERENCES duel_records(reference) ON DELETE CASCADE
                )
                """);
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_ended_at ON duel_records (ended_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_player_one ON duel_records (player_one_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_player_two ON duel_records (player_two_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_winner ON duel_records (winner_id)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_map ON duel_records (map_name)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_ruleset ON duel_records (ruleset)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_records_reason ON duel_records (end_reason)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_duel_participants_player ON duel_record_participants (player_id)");
        }
    }

    private long readLong(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private void readEntries(PreparedStatement statement, List<Map.Entry<String, Long>> results) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                results.add(Map.entry(resultSet.getString(1), resultSet.getLong(2)));
            }
        }
    }

    private DuelRecord readRecord(ResultSet resultSet) throws SQLException {
        return new DuelRecord(
            resultSet.getString("reference"),
            resultSet.getLong("started_at"),
            resultSet.getLong("ended_at"),
            resultSet.getLong("duration_ms"),
            fromString(resultSet.getString("player_one_id")),
            resultSet.getString("player_one_name"),
            fromString(resultSet.getString("player_two_id")),
            resultSet.getString("player_two_name"),
            fromString(resultSet.getString("winner_id")),
            resultSet.getString("winner_name"),
            fromString(resultSet.getString("loser_id")),
            resultSet.getString("loser_name"),
            resultSet.getString("map_id"),
            resultSet.getString("map_name"),
            resultSet.getString("ruleset"),
            resultSet.getString("item_rules"),
            DuelEndReason.valueOf(resultSet.getString("end_reason")),
            resultSet.getBoolean("counted_as_match"),
            resultSet.getInt("spectator_count"),
            resultSet.getDouble("wager"),
            readMatchType(resultSet.getString("match_type")),
            resultSet.getInt("team_size"),
            readParticipants(resultSet.getString("reference"))
        );
    }

    private DuelMatchType readMatchType(String value) {
        try {
            return DuelMatchType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return DuelMatchType.NORMAL;
        }
    }

    private void insertParticipants(DuelRecord record) throws SQLException {
        if (record.participants().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO duel_record_participants (
                duel_reference, player_id, player_name, team_index, winner
            ) VALUES (?, ?, ?, ?, ?)
            """)) {
            for (DuelRecordParticipant participant : record.participants()) {
                statement.setString(1, record.reference());
                statement.setString(2, participant.playerId().toString());
                statement.setString(3, participant.playerName());
                statement.setInt(4, participant.teamIndex());
                statement.setBoolean(5, participant.winner());
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<DuelRecordParticipant> readParticipants(String reference) throws SQLException {
        List<DuelRecordParticipant> participants = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
            SELECT player_id, player_name, team_index, winner
            FROM duel_record_participants
            WHERE duel_reference = ?
            ORDER BY team_index, player_name
            """)) {
            statement.setString(1, reference);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    participants.add(new DuelRecordParticipant(
                        fromString(resultSet.getString("player_id")),
                        resultSet.getString("player_name"),
                        resultSet.getInt("team_index"),
                        resultSet.getBoolean("winner")
                    ));
                }
            }
        }
        return participants;
    }

    private static String toString(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static UUID fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return UUID.fromString(value);
    }
}
