package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class WeeklyWinsQueryTest {
    private static final String WINS="wins";
    private static java.sql.Connection database(String name) throws java.sql.SQLException {
        return DriverManager.getConnection("jdbc:h2:mem:"+name);
    }
    @Test
    void countsOnlyQualifiedWinsAndUsesLatestNameWithinHalfOpenWindow() throws Exception {
        try (var connection = database("weekly")) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE duel_records(reference VARCHAR, winner_id VARCHAR, winner_name VARCHAR, ended_at BIGINT, counted_as_match BOOLEAN, end_reason VARCHAR DEFAULT 'KILL')");
                statement.execute("INSERT INTO duel_records(reference,winner_id,winner_name,ended_at,counted_as_match) VALUES ('a','uuid-a','OldName',100,TRUE),('b','uuid-a','NewName',199,TRUE),"
                    + "('c','uuid-b','B',101,TRUE),('d','uuid-b','B',150,TRUE),('e','uuid-c','C',120,TRUE),"
                    + "('f','uuid-d','D',125,TRUE),('before','uuid-c','C',99,TRUE),('end','uuid-c','C',200,TRUE),"
                    + "('cancel','uuid-c','C',140,FALSE),('draw',NULL,NULL,140,TRUE)");
                statement.execute("INSERT INTO duel_records VALUES ('malformed-draw','uuid-c','C',145,TRUE,'DRAW')");
            }
            var results = WeeklyWinsQuery.topThree(connection, 100, 200);
            assertEquals(3, results.size());
            assertEquals("uuid-a", results.get(0).get("id"));
            assertEquals("NewName", results.get(0).get("name"));
            assertEquals(2L, results.get(0).get(WINS));
            assertEquals("uuid-b", results.get(1).get("id"));
            assertEquals(1L, results.get(2).get(WINS));
            assertEquals(java.util.Set.of("id", "name", WINS), results.get(0).keySet());
            assertTrue(WeeklyWinsQuery.topThree(connection, 300, 400).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> WeeklyWinsQuery.topThree(connection, 200, 100));
        }
    }
    @Test
    void winningTeamParticipantsReceiveTheirOwnCountWithoutCreditingLosers() throws Exception {
        try (var connection = database("weeklyteam")) {
            try (var statement=connection.createStatement()) {
                statement.execute("CREATE TABLE duel_records(reference VARCHAR, winner_id VARCHAR, winner_name VARCHAR, ended_at BIGINT, counted_as_match BOOLEAN, end_reason VARCHAR, team_size INT)");
                statement.execute("CREATE TABLE duel_record_participants(duel_reference VARCHAR,player_id VARCHAR,player_name VARCHAR,winner BOOLEAN)");
                statement.execute("INSERT INTO duel_records VALUES ('team','winner-a','A',110,TRUE,'KILL',2),('draw','winner-a','A',111,TRUE,'DRAW',2),('cancel','winner-a','A',112,FALSE,'KILL',2)");
                statement.execute("INSERT INTO duel_record_participants VALUES ('team','winner-a','A',TRUE),('team','winner-b','B',TRUE),('team','loser','Loser',FALSE),('draw','winner-b','B',TRUE),('cancel','winner-b','B',TRUE)");
            }
            var rows=WeeklyWinsQuery.topThree(connection,100,200);
            assertEquals(java.util.List.of("winner-a","winner-b"),rows.stream().map(row->row.get("id")).toList());
            assertTrue(rows.stream().allMatch(row->row.get(WINS).equals(1L)));
        }
    }
    @Test void incompleteTeamCreditsFailClosedAndBoundedResultsDeclareCompleteness() throws Exception {
        try(var connection=database("weeklybounded")) {
            try(var statement=connection.createStatement()) {
                statement.execute("CREATE TABLE duel_records(reference VARCHAR,winner_id VARCHAR,winner_name VARCHAR,ended_at BIGINT,counted_as_match BOOLEAN,end_reason VARCHAR,team_size INT)");
                statement.execute("INSERT INTO duel_records VALUES ('team','a','A',110,TRUE,'KILL',2)");
            }
            assertThrows(java.sql.SQLException.class,()->WeeklyWinsQuery.snapshot(connection,100,200));
            try(var statement=connection.createStatement()) {
                statement.execute("DELETE FROM duel_records");
                for(int index=0;index<501;index++)statement.execute("INSERT INTO duel_records VALUES ('r"+index+"','id"+index+"','Player',110,TRUE,'KILL',1)");
            }
            var limited=WeeklyWinsQuery.snapshot(connection,100,200);
            assertEquals(false,limited.get("complete"));assertEquals(500,((java.util.List<?>)limited.get("items")).size());
            assertEquals(true,WeeklyWinsQuery.snapshot(connection,300,400).get("complete"));
        }
    }
}
