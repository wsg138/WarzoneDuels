package dev.minecraft.warzoneduels.adapter.plan;

import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import com.djrapitops.plan.delivery.web.resolver.MimeType;
import com.djrapitops.plan.delivery.web.resolver.Resolver;
import com.djrapitops.plan.delivery.web.resolver.Response;
import com.djrapitops.plan.delivery.web.resolver.exception.NotFoundException;
import com.djrapitops.plan.delivery.web.resolver.request.Request;
import com.djrapitops.plan.delivery.web.resolver.request.URIQuery;
import com.djrapitops.plan.delivery.web.resolver.request.WebUser;
import dev.minecraft.warzoneduels.app.DuelAnalyticsService;
import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.app.StatsService;
import dev.minecraft.warzoneduels.domain.analytics.DuelRecord;
import dev.minecraft.warzoneduels.domain.stats.PlayerDuelStats;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@SuppressWarnings({"PMD.UseConcurrentHashMap", "PMD.AvoidInstantiatingObjectsInLoops"})
public final class WarzoneDuelsPlanResolver implements Resolver {
    private static final String METHOD_GET = "GET";
    private static final String QUERY_PLAYER = "player";
    private static final int HOURS_PER_DAY = 24;
    private static final int DAYS_PER_WEEK = 7;
    private static final int DAYS_PER_MONTH = 30;
    private static final int PLAYER_HISTORY_DAYS = 3650;
    private static final int DEFAULT_RECENT_DUELS_LIMIT = 20;
    private static final int PLAYER_RECENT_DUELS_LIMIT = 15;
    private static final int RECENT_OPPONENT_LIMIT = 5;
    private static final int TOP_USAGE_LIMIT = 8;
    private static final double MONEY_EPSILON = 0.0001D;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
        .ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.US)
        .withZone(ZoneId.systemDefault());

    private final WarzoneDuelsPlugin plugin;
    private final StatsService statsService;
    private final DuelAnalyticsService analyticsService;
    private final DuelService duelService;

    public WarzoneDuelsPlanResolver(WarzoneDuelsPlugin plugin, StatsService statsService, DuelAnalyticsService analyticsService, DuelService duelService) {
        this.plugin = plugin;
        this.statsService = statsService;
        this.analyticsService = analyticsService;
        this.duelService = duelService;
    }

    @Override
    public boolean canAccess(Request request) {
        WebUser user = request.getUser().orElse(new WebUser(""));
        return user.hasPermission("page.server");
    }

    @Override
    public Optional<Response> resolve(Request request) {
        if (!METHOD_GET.equalsIgnoreCase(request.getMethod())) {
            return Optional.empty();
        }

        String path = request.getPath().asString().toLowerCase(Locale.ROOT);
        boolean json = path.endsWith("/api") || "json".equalsIgnoreCase(request.getQuery().get("format").orElse(""));
        if (json) {
            return Optional.of(Response.builder()
                .setMimeType(MimeType.JSON)
                .setJSONContent(buildJson(request.getQuery()))
                .build());
        }
        if (path.equals("/warzone-duels") || path.equals("/warzone-duels/") || path.startsWith("/warzone-duels/")) {
            return Optional.of(Response.builder()
                .setMimeType(MimeType.HTML)
                .setContent(renderHtml(request.getQuery()))
                .build());
        }
        throw new NotFoundException("Unknown WarzoneDuels page path.");
    }

    private Map<String, Object> buildJson(URIQuery query) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("totalDuels", analyticsService.countTotalDuels());
        root.put("duels24h", analyticsService.countDuelsSince(Duration.ofHours(HOURS_PER_DAY)));
        root.put("duels7d", analyticsService.countDuelsSince(Duration.ofDays(DAYS_PER_WEEK)));
        root.put("duels30d", analyticsService.countDuelsSince(Duration.ofDays(DAYS_PER_MONTH)));
        root.put("activeDuels", duelService.hasActiveDuel() ? 1 : 0);
        root.put("mostUsedMap", analyticsService.mostUsedMapSince(Duration.ofDays(DAYS_PER_MONTH)));
        root.put("mostUsedRuleset", analyticsService.mostUsedRulesSince(Duration.ofDays(DAYS_PER_MONTH)));
        int recentLimit = Math.max(RECENT_OPPONENT_LIMIT, plugin.getConfig().getInt("analytics.recent-duels-limit", DEFAULT_RECENT_DUELS_LIMIT));
        root.put("recentDuels", serializeRecentDuels(analyticsService.recentDuels(recentLimit)));

        query.get(QUERY_PLAYER).ifPresent(playerQuery -> {
            PlayerLookup lookup = findPlayer(playerQuery);
            if (lookup != null) {
                root.put(QUERY_PLAYER, buildPlayerJson(lookup));
            }
        });
        return root;
    }

    private Map<String, Object> buildPlayerJson(PlayerLookup lookup) {
        Map<String, Object> player = new LinkedHashMap<>();
        PlayerDuelStats stats = lookup.stats();
        player.put("playerId", lookup.playerId().toString());
        player.put("name", lookup.name());
        player.put("matchesPlayed", stats == null ? analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofDays(PLAYER_HISTORY_DAYS)) : stats.matchesPlayed());
        player.put("wins", stats == null ? 0 : stats.wins());
        player.put("losses", stats == null ? 0 : stats.losses());
        player.put("draws", stats == null ? 0 : stats.draws());
        player.put("currentStreak", stats == null ? 0 : stats.currentWinStreak());
        player.put("bestStreak", stats == null ? 0 : stats.bestWinStreak());
        player.put("duels24h", analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofHours(HOURS_PER_DAY)));
        player.put("duels7d", analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofDays(DAYS_PER_WEEK)));
        player.put("duels30d", analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofDays(DAYS_PER_MONTH)));
        player.put("mostUsedMap", analyticsService.mostPlayedMapForPlayer(lookup.playerId()));
        player.put("mostUsedRuleset", analyticsService.mostPlayedRulesForPlayer(lookup.playerId()));
        player.put("recentOpponents", analyticsService.recentOpponents(lookup.playerId(), RECENT_OPPONENT_LIMIT));
        player.put("recentDuels", serializeRecentDuels(analyticsService.recentDuelsForPlayer(lookup.playerId(), PLAYER_RECENT_DUELS_LIMIT)));
        return player;
    }

    private String renderHtml(URIQuery query) {
        PlayerLookup lookup = query.get(QUERY_PLAYER).map(this::findPlayer).orElse(null);
        StringBuilder html = new StringBuilder(20_000);
        appendPageStart(html);
        appendSearchForm(html, query);
        renderSummaryCards(html);
        int recentLimit = Math.max(RECENT_OPPONENT_LIMIT, plugin.getConfig().getInt("analytics.recent-duels-limit", DEFAULT_RECENT_DUELS_LIMIT));
        renderRecentDuels(html, analyticsService.recentDuels(recentLimit));
        if (lookup != null) {
            renderPlayerSection(html, lookup);
        } else {
            appendEmptyPlayerFocus(html);
        }
        renderArenaSection(html);
        html.append("</div></body></html>");
        return html.toString();
    }

    private void appendPageStart(StringBuilder html) {
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width, initial-scale=1">
              <title>WarzoneDuels Dashboard</title>
              <style>
                :root {
                  --bg: #0f1115;
                  --panel: #171b22;
                  --panel-alt: #1d232c;
                  --border: #2c3440;
                  --text: #e7edf7;
                  --muted: #98a2b3;
                  --accent: #f87171;
                  --accent-2: #60a5fa;
                  --good: #4ade80;
                  --warn: #fbbf24;
                }
                body { margin: 0; background: var(--bg); color: var(--text); font-family: Arial, sans-serif; }
                .wrap { padding: 24px; max-width: 1600px; margin: 0 auto; }
                h1 { margin: 0 0 8px; font-size: 30px; }
                .sub { color: var(--muted); margin-bottom: 18px; }
                .grid { display: grid; gap: 14px; }
                .cards { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); margin-bottom: 18px; }
                .card, .panel { background: linear-gradient(180deg, var(--panel), var(--panel-alt)); border: 1px solid var(--border); border-radius: 16px; }
                .card { padding: 16px; }
                .label { color: var(--muted); font-size: 12px; text-transform: uppercase; letter-spacing: .08em; }
                .value { margin-top: 8px; font-size: 28px; font-weight: 700; }
                .section { margin-top: 18px; }
                .section h2 { margin: 0 0 12px; font-size: 20px; }
                .panel { overflow: hidden; }
                .panel .inner { padding: 14px; }
                table { width: 100%; border-collapse: collapse; }
                th, td { padding: 10px 12px; border-bottom: 1px solid var(--border); text-align: left; vertical-align: top; }
                th { color: var(--muted); font-size: 12px; text-transform: uppercase; letter-spacing: .08em; }
                tr:hover td { background: rgba(255,255,255,0.02); }
                .badge { display: inline-block; padding: 4px 8px; border-radius: 999px; font-size: 12px; background: rgba(96,165,250,.16); color: #bfdbfe; }
                .good { color: var(--good); }
                .bad { color: #fca5a5; }
                .warn { color: var(--warn); }
                .muted { color: var(--muted); }
                .split { display: grid; grid-template-columns: repeat(auto-fit, minmax(320px, 1fr)); gap: 14px; }
                .search { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 18px; }
                input[type=text] { background: #0b0d12; color: var(--text); border: 1px solid var(--border); border-radius: 10px; padding: 10px 12px; min-width: 280px; }
                button, a.button { background: var(--accent-2); color: white; border: 0; border-radius: 10px; padding: 10px 14px; text-decoration: none; display: inline-block; cursor: pointer; }
                a.button.secondary { background: #334155; }
              </style>
            </head>
            <body>
            <div class="wrap">
            """);

        html.append("<h1>WarzoneDuels Dashboard</h1>");
        html.append("<div class=\"sub\">Recent duels, player history, and arena usage at a glance.</div>");
    }

    private void appendSearchForm(StringBuilder html, URIQuery query) {
        html.append("""
            <form class="search" method="get" action="/warzone-duels">
              <input type="text" name="player" placeholder="Player name or UUID" value="%s">
              <button type="submit">Open Player</button>
              <a class="button secondary" href="/warzone-duels">Clear</a>
              <a class="button secondary" href="/warzone-duels/api?format=json">JSON</a>
            </form>
            """.formatted(escapeHtml(query.get(QUERY_PLAYER).orElse(""))));
    }

    private void appendEmptyPlayerFocus(StringBuilder html) {
        html.append("""
            <div class="section panel"><div class="inner">
              <h2>Player Focus</h2>
              <div class="muted">Enter a player name or UUID above to inspect duel history and player-specific stats.</div>
            </div></div>
            """);
    }

    private void renderSummaryCards(StringBuilder html) {
        html.append("<div class=\"grid cards\">");
        appendCards(
            html,
            card("Total Duels", formatNumber(analyticsService.countTotalDuels())),
            card("Duels 24h", formatNumber(analyticsService.countDuelsSince(Duration.ofHours(HOURS_PER_DAY)))),
            card("Duels 7d", formatNumber(analyticsService.countDuelsSince(Duration.ofDays(DAYS_PER_WEEK)))),
            card("Active Duels", duelService.hasActiveDuel() ? "1" : "0"),
            card("Most Used Map", analyticsService.mostUsedMapSince(Duration.ofDays(DAYS_PER_MONTH)))
        );
        html.append("</div>");
    }

    private void renderRecentDuels(StringBuilder html, List<DuelRecord> records) {
        html.append("""
            <div class="section panel">
              <div class="inner">
                <h2>Recent Duels</h2>
                <table>
                  <thead>
                    <tr>
                      <th>Ref</th>
                      <th>When</th>
                      <th>Players</th>
                      <th>Winner</th>
                      <th>Map</th>
                      <th>Rules</th>
                      <th>Duration</th>
                      <th>Spectators</th>
                      <th>Reason</th>
                      <th>Wager</th>
                    </tr>
                  </thead>
                  <tbody>
            """);
        if (records.isEmpty()) {
            html.append("<tr><td colspan=\"10\" class=\"muted\">No duel records yet.</td></tr>");
        } else {
            for (DuelRecord record : records) {
                html.append("<tr>");
                html.append(td(escapeHtml(record.reference())));
                html.append(td(escapeHtml(formatDate(record.endedAtEpochMs()))));
                html.append(td(escapeHtml(record.playerOneName() + " vs " + record.playerTwoName())));
                html.append(td(escapeHtml(winnerLabel(record))));
                html.append(td(escapeHtml(record.mapName())));
                html.append(td(escapeHtml(record.ruleset())));
                html.append(td(escapeHtml(formatDuration(record.durationMillis()))));
                html.append(td(record.spectatorCount() > 0 ? String.valueOf(record.spectatorCount()) : "0"));
                html.append(td(reasonBadge(record.endReason().name())));
                html.append(td(escapeHtml(formatMoney(record.wager()))));
                html.append("</tr>");
            }
        }
        html.append("</tbody></table></div></div>");
    }

    private void renderPlayerSection(StringBuilder html, PlayerLookup lookup) {
        PlayerDuelStats stats = lookup.stats();
        String displayName = stats != null ? stats.lastKnownName() : lookup.name();
        appendPlayerHeader(html, displayName);
        appendPlayerCards(html, lookup, stats);
        appendPlayerSummary(html, lookup, stats);
        appendRecentPlayerDuels(html, lookup);
        html.append("""
                    </tbody>
                  </table>
                </div></div>
              </div>
            """);
    }

    private void appendPlayerHeader(StringBuilder html, String displayName) {
        html.append("""
            <div class="section panel">
              <div class="inner">
                <h2>Player Focus: %s</h2>
                <div class="grid cards">
            """.formatted(escapeHtml(displayName)));
    }

    private void appendPlayerCards(StringBuilder html, PlayerLookup lookup, PlayerDuelStats stats) {
        appendCards(
            html,
            card("Matches", formatNumber(stats == null ? analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofDays(PLAYER_HISTORY_DAYS)) : stats.matchesPlayed())),
            card("Wins", formatNumber(stats == null ? 0 : stats.wins())),
            card("Losses", formatNumber(stats == null ? 0 : stats.losses())),
            card("Draws", formatNumber(stats == null ? 0 : stats.draws())),
            card("Current Streak", formatNumber(stats == null ? 0 : stats.currentWinStreak())),
            card("Best Streak", formatNumber(stats == null ? 0 : stats.bestWinStreak())),
            card("24h", formatNumber(analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofHours(HOURS_PER_DAY)))),
            card("7d", formatNumber(analyticsService.countDuelsForPlayerSince(lookup.playerId(), Duration.ofDays(DAYS_PER_WEEK))))
        );
        html.append("</div>");
    }

    private void appendPlayerSummary(StringBuilder html, PlayerLookup lookup, PlayerDuelStats stats) {
        html.append("""
                <div class="split" style="margin-top: 14px;">
                  <div class="panel"><div class="inner">
                    <h2>Player Summary</h2>
                    <table>
                      <tbody>
            """);
        appendRows(
            html,
            row("Most Used Map", analyticsService.mostPlayedMapForPlayer(lookup.playerId())),
            row("Most Used Ruleset", analyticsService.mostPlayedRulesForPlayer(lookup.playerId())),
            row("Disconnect Losses", stats == null ? "0" : String.valueOf(stats.disconnectForfeitLosses())),
            row("Recent Opponents", renderEntries(analyticsService.recentOpponents(lookup.playerId(), RECENT_OPPONENT_LIMIT)))
        );
        html.append("""
                      </tbody>
                    </table>
                  </div>
                  <div class="panel"><div class="inner">
                    <h2>Recent Player Duels</h2>
                    <table>
                      <thead>
                        <tr>
                          <th>When</th>
                          <th>Opponent</th>
                          <th>Result</th>
                          <th>Map</th>
                          <th>Reason</th>
                        </tr>
                      </thead>
                      <tbody>
            """);
    }

    private void appendRecentPlayerDuels(StringBuilder html, PlayerLookup lookup) {
        List<DuelRecord> records = analyticsService.recentDuelsForPlayer(lookup.playerId(), PLAYER_RECENT_DUELS_LIMIT);
        if (records.isEmpty()) {
            html.append("<tr><td colspan=\"5\" class=\"muted\">No duel history found for this player.</td></tr>");
        } else {
            for (DuelRecord record : records) {
                html.append("<tr>");
                html.append(td(escapeHtml(formatDate(record.endedAtEpochMs()))));
                html.append(td(escapeHtml(opponentName(record, lookup.playerId()))));
                html.append(td(escapeHtml(resultLabel(record, lookup.playerId()))));
                html.append(td(escapeHtml(record.mapName())));
                html.append(td(reasonBadge(record.endReason().name())));
                html.append("</tr>");
            }
        }
    }

    private void renderArenaSection(StringBuilder html) {
        html.append("""
            <div class="section panel">
              <div class="inner">
                <h2>Arena / Map Usage</h2>
                <div class="split">
            """);
        appendRows(
            html,
            renderUsageTable("Most Used Maps (30d)", analyticsService.topMapsSince(Duration.ofDays(DAYS_PER_MONTH), TOP_USAGE_LIMIT)),
            renderUsageTable("Most Used Rulesets (30d)", analyticsService.topRulesSince(Duration.ofDays(DAYS_PER_MONTH), TOP_USAGE_LIMIT)),
            renderUsageTable("End Reasons (30d)", analyticsService.topEndReasonsSince(Duration.ofDays(DAYS_PER_MONTH), TOP_USAGE_LIMIT))
        );
        html.append("</div></div></div>");
    }

    private String renderUsageTable(String title, List<Map.Entry<String, Long>> entries) {
        StringBuilder html = new StringBuilder();
        html.append("""
            <div class="panel"><div class="inner">
              <h2>%s</h2>
              <table>
                <thead><tr><th>Name</th><th>Count</th></tr></thead>
                <tbody>
            """.formatted(escapeHtml(title)));
        if (entries.isEmpty()) {
            html.append("<tr><td colspan=\"2\" class=\"muted\">No data available.</td></tr>");
        } else {
            for (Map.Entry<String, Long> entry : entries) {
                html.append("<tr><td>").append(escapeHtml(entry.getKey())).append("</td><td>").append(formatNumber(entry.getValue())).append("</td></tr>");
            }
        }
        html.append("</tbody></table></div></div>");
        return html.toString();
    }

    private String card(String label, String value) {
        return """
            <div class="card"><div class="label">%s</div><div class="value">%s</div></div>
            """.formatted(escapeHtml(label), value);
    }

    private void appendCards(StringBuilder html, String... cards) {
        for (String card : cards) {
            html.append(card);
        }
    }

    private void appendRows(StringBuilder html, String... rows) {
        for (String row : rows) {
            html.append(row);
        }
    }

    private String row(String label, String value) {
        return "<tr><th>" + escapeHtml(label) + "</th><td>" + escapeHtml(value) + "</td></tr>";
    }

    private String td(String value) {
        return "<td>" + value + "</td>";
    }

    private String renderEntries(List<Map.Entry<String, Long>> entries) {
        if (entries.isEmpty()) {
            return "None";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Long> entry : entries) {
            parts.add(entry.getKey() + " (" + entry.getValue() + ")");
        }
        return String.join(", ", parts);
    }

    private String reasonBadge(String reason) {
        String lower = reason.toLowerCase(Locale.ROOT);
        String clazz = lower.contains("kill") ? "good" : lower.contains("draw") ? "warn" : "bad";
        return "<span class=\"" + clazz + "\">" + escapeHtml(reason) + "</span>";
    }

    private String winnerLabel(DuelRecord record) {
        if (record.winnerName() != null) {
            return record.winnerName();
        }
        if (record.endReason() == dev.minecraft.warzoneduels.domain.DuelEndReason.DRAW) {
            return "Draw";
        }
        return "No winner";
    }

    private String resultLabel(DuelRecord record, UUID playerId) {
        if (record.endReason() == dev.minecraft.warzoneduels.domain.DuelEndReason.DRAW) {
            return "Draw";
        }
        if (playerId.equals(record.winnerId())) {
            return "Win";
        }
        if (playerId.equals(record.loserId())) {
            return "Loss";
        }
        return "Unknown";
    }

    private String opponentName(DuelRecord record, UUID playerId) {
        if (playerId.equals(record.playerOneId())) {
            return record.playerTwoName();
        }
        if (playerId.equals(record.playerTwoId())) {
            return record.playerOneName();
        }
        return "Unknown";
    }

    private String formatDate(long epochMs) {
        return DATE_TIME.format(Instant.ofEpochMilli(epochMs));
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        long remainingSeconds = seconds % 60L;
        if (hours > 0) {
            return String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, remainingSeconds);
        }
        if (minutes > 0) {
            return String.format(Locale.US, "%dm %02ds", minutes, remainingSeconds);
        }
        return remainingSeconds + "s";
    }

    private String formatMoney(double value) {
        if (Math.abs(value) < MONEY_EPSILON) {
            return "0";
        }
        return String.format(Locale.US, "%,.2f", value);
    }

    private String formatNumber(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    private List<Map<String, Object>> serializeRecentDuels(List<DuelRecord> records) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (DuelRecord record : records) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("reference", record.reference());
            entry.put("endedAt", record.endedAtEpochMs());
            entry.put("durationMillis", record.durationMillis());
            entry.put("playerOne", record.playerOneName());
            entry.put("playerTwo", record.playerTwoName());
            entry.put("winner", record.winnerName());
            entry.put("loser", record.loserName());
            entry.put("map", record.mapName());
            entry.put("ruleset", record.ruleset());
            entry.put("reason", record.endReason().name());
            entry.put("spectators", record.spectatorCount());
            entry.put("wager", record.wager());
            result.add(entry);
        }
        return result;
    }

    private PlayerLookup findPlayer(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            UUID uuid = UUID.fromString(query);
            PlayerDuelStats stats = statsService.findById(uuid);
            String name = stats != null ? stats.lastKnownName() : query;
            return new PlayerLookup(uuid, name, stats);
        } catch (IllegalArgumentException ignored) {
            PlayerDuelStats stats = statsService.findExistingByName(query);
            if (stats == null) {
                return null;
            }
            return new PlayerLookup(stats.playerId(), stats.lastKnownName(), stats);
        }
    }

    private record PlayerLookup(UUID playerId, String name, PlayerDuelStats stats) {
    }
}
