package dev.minecraft.warzoneduels.adapter.bukkit.persistence;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Public read-only projection; uses persisted winner credits, never opponent data. */
public final class WeeklyWinsQuery {
    private static final String RECORDS="duel_records",PARTICIPANTS="duel_record_participants",TEAM_SIZE="team_size";
    private WeeklyWinsQuery() { }
    public static List<Map<String,Object>> topThree(Connection connection,long start,long end) throws SQLException {
        return read(connection,start,end,3);
    }
    public static Map<String,Object> snapshot(Connection connection,long start,long end) throws SQLException {
        var rows=read(connection,start,end,501);
        return Map.of("weekStartsAt",Instant.ofEpochMilli(start).toString(),
                "generatedAt",Instant.ofEpochMilli(end-1).toString(),"complete",rows.size()<=500,
                "items",List.copyOf(rows.subList(0,Math.min(500,rows.size()))));
    }
    private static boolean hasTable(Connection connection,String name) throws SQLException {
        try(var tables=connection.getMetaData().getTables(null,null,null,new String[]{"TABLE"})) {
            while(tables.next())if(name.equalsIgnoreCase(tables.getString("TABLE_NAME")))return true;
        }
        return false;
    }
    private static boolean hasTeamSize(Connection connection) throws SQLException {
        try(var columns=connection.getMetaData().getColumns(null,null,null,null)) {
            while(columns.next())if(RECORDS.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    &&TEAM_SIZE.equalsIgnoreCase(columns.getString("COLUMN_NAME")))return true;
        }
        return false;
    }
    private static void requireTeamCredits(Connection connection,long start,long end,boolean participants) throws SQLException {
        if(!hasTeamSize(connection))return;
        String missing=participants?" AND NOT EXISTS(SELECT 1 FROM duel_record_participants p WHERE p.duel_reference=r.reference AND p.winner=TRUE)":"";
        try(var statement=connection.prepareStatement("SELECT 1 FROM duel_records r WHERE r.counted_as_match=TRUE AND r.end_reason<>'DRAW' AND r.team_size>1 AND r.ended_at>=? AND r.ended_at<?"+missing+" LIMIT 1")) {
            statement.setQueryTimeout(5);statement.setLong(1,start);statement.setLong(2,end);
            try(var rows=statement.executeQuery()) {
                if(rows.next())throw new SQLException("Winning team participants unavailable");
            }
        }
    }
    private static List<Map<String,Object>> read(Connection connection,long start,long end,int limit) throws SQLException {
        if(start<0||end<=start||end-start>7L*86400000)throw new IllegalArgumentException("Invalid weekly interval");
        boolean participants=hasTable(connection,PARTICIPANTS);
        requireTeamCredits(connection,start,end,participants);
        String legacy="SELECT r.reference AS duel_reference,r.winner_id AS player_id,r.winner_name AS player_name FROM duel_records r WHERE r.winner_id IS NOT NULL AND r.winner_id<>''";
        String credits=participants?"SELECT DISTINCT p.duel_reference,p.player_id,p.player_name FROM duel_record_participants p WHERE p.winner=TRUE UNION ALL "+legacy+" AND NOT EXISTS(SELECT 1 FROM duel_record_participants p WHERE p.duel_reference=r.reference)":legacy;
        String sql="""
            SELECT player_id,total,player_name AS latest_name FROM (
                SELECT c.player_id,c.player_name,COUNT(*) OVER(PARTITION BY c.player_id) AS total,
                    ROW_NUMBER() OVER(PARTITION BY c.player_id ORDER BY r.ended_at DESC,c.duel_reference ASC) AS latest
                FROM (%s) c JOIN duel_records r ON r.reference=c.duel_reference
                WHERE r.counted_as_match=TRUE AND r.end_reason<>'DRAW' AND r.ended_at>=? AND r.ended_at<?
            ) ranked WHERE latest=1 ORDER BY total DESC,player_id ASC LIMIT ?
            """.formatted(credits);
        var result=new ArrayList<Map<String,Object>>();
        try(var statement=connection.prepareStatement(sql)) {
            statement.setQueryTimeout(5);statement.setLong(1,start);statement.setLong(2,end);statement.setInt(3,limit);
            try(var rows=statement.executeQuery()) {
                while(rows.next())result.add(Map.of("id",rows.getString("player_id"),
                        "name",Objects.requireNonNullElse(rows.getString("latest_name"),rows.getString("player_id")),"wins",rows.getLong("total")));
            }
        }
        return List.copyOf(result);
    }
}
