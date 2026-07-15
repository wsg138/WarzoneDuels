package dev.minecraft.warzoneduels.adapter.bukkit.gui;

import dev.minecraft.warzoneduels.app.DuelService;
import dev.minecraft.warzoneduels.domain.BuilderSession;
import dev.minecraft.warzoneduels.domain.DuelMapOption;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.permission.PermissionPolicy;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import java.util.List;

public final class DuelGuiListener implements Listener {
    private static final int SLOT_ACTIVE_CLOSE = 44;
    private static final int SLOT_MAP_CANCEL = 40;
    private static final int SLOT_RULES_NONE = 20;
    private static final int SLOT_RULES_PLACE_ONLY = 22;
    private static final int SLOT_RULES_PLACE_BREAK = 24;
    private static final int SLOT_RULES_BACK = 36;
    private static final int SLOT_RULES_CANCEL = 44;
    private static final int SLOT_PLACE_ONLY_COBWEB_UTILS = 20;
    private static final int SLOT_PLACE_ONLY_ALL_BLOCKS = 24;
    private static final int SLOT_PLACE_ONLY_BACK = 40;
    private static final int SLOT_EXTRAS_CRYSTALS_ANCHORS = 20;
    private static final int SLOT_EXTRAS_EXPLOSIVE_MINECARTS = 22;
    private static final int SLOT_EXTRAS_OTHER_EXPLOSIVES = 24;
    private static final int SLOT_EXTRAS_BACK = 36;
    private static final int SLOT_EXTRAS_NEXT = 40;
    private static final int SLOT_ITEM_ENDER_PEARL = 20;
    private static final int SLOT_ITEM_WIND_CHARGE = 22;
    private static final int SLOT_ITEM_MACE = 24;
    private static final int SLOT_ITEM_CHORUS = 29;
    private static final int SLOT_ITEM_SPEAR = 31;
    private static final int SLOT_ITEM_ELYTRA = 33;
    private static final int SLOT_ITEM_ENDER_CHEST = 40;
    private static final int SLOT_ITEM_BACK = 45;
    private static final int SLOT_ITEM_WAGER = 53;
    private static final int SLOT_PREVIEW_DENY = 27;
    private static final int SLOT_PREVIEW_ACCEPT = 31;
    private static final int SLOT_PREVIEW_CLOSE = 35;
    private static final int SLOT_WAGER_CUSTOM = 31;
    private static final int SLOT_WAGER_CLEAR = 49;
    private static final int SLOT_WAGER_BACK = 45;
    private static final int SLOT_WAGER_CONFIRM = 53;
    private static final int SLOT_CONFIRM_EDIT_MAP = 19;
    private static final int SLOT_CONFIRM_EDIT_RULES = 21;
    private static final int SLOT_CONFIRM_EDIT_ITEMS = 23;
    private static final int SLOT_CONFIRM_EDIT_EXTRAS = 16;
    private static final int SLOT_CONFIRM_EDIT_WAGER = 25;
    private static final int SLOT_CONFIRM_BACK_TO_WAGER = 36;
    private static final int SLOT_CONFIRM_SEND = 40;
    private static final int SLOT_CONFIRM_CANCEL = 44;
    private static final Map<Integer, Double> WAGER_DELTAS = Map.of(
        9, 1D,
        10, 10D,
        11, 100D,
        12, 1000D,
        14, -1D,
        15, -10D,
        16, -100D,
        17, -1000D
    );

    private final DuelService duelService;
    private final Map<UUID, Boolean> awaitingWagerInput = new ConcurrentHashMap<>();

    public DuelGuiListener(DuelService duelService) {
        this.duelService = duelService;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (!awaitingWagerInput.containsKey(playerId)) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission(PermissionPolicy.CHALLENGE)) {
            awaitingWagerInput.remove(playerId);
            duelService.clearBuilder(playerId);
            Bukkit.getScheduler().runTask(duelService.plugin(), () -> duelService.sendMessage(event.getPlayer(), "messages.no-permission"));
            return;
        }
        String input = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        Bukkit.getScheduler().runTask(duelService.plugin(), () -> applyCustomWager(event.getPlayer(), input));
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof GuiHolder holder)) {
            return;
        }
        event.setCancelled(true);

        switch (holder.type()) {
            case REQUEST_PREVIEW -> {
                handleRequestPreview(event.getRawSlot(), player);
                return;
            }
            case ACTIVE_SETTINGS -> {
                if (event.getRawSlot() == SLOT_ACTIVE_CLOSE) {
                    player.closeInventory();
                }
                return;
            }
            default -> {
            }
        }

        if (!player.hasPermission(PermissionPolicy.CHALLENGE)) {
            duelService.clearBuilder(player.getUniqueId());
            player.closeInventory();
            duelService.sendMessage(player, "messages.no-permission");
            return;
        }

        BuilderSession builder = duelService.getBuilder(player.getUniqueId());
        if (builder == null) {
            player.closeInventory();
            return;
        }

        DuelSettings settings = builder.settings();
        switch (holder.type()) {
            case MAP -> handleMap(event.getRawSlot(), player, settings);
            case RULES -> handleRules(event.getRawSlot(), player, settings);
            case PLACE_ONLY -> handlePlaceOnly(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case EXTRAS -> handleExtras(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case ITEM_RULES -> handleItemRules(event.getRawSlot(), player, settings, event.getClick(), holder.returnToConfirm());
            case WAGER -> handleWager(event.getRawSlot(), player, settings, holder.returnToConfirm());
            case CONFIRM -> handleConfirm(event.getRawSlot(), player, settings);
            case REQUEST_PREVIEW, ACTIVE_SETTINGS -> {
            }
        }
    }

    private void handleMap(int slot, Player player, DuelSettings settings) {
        List<DuelMapOption> options = duelService.mapOptions();
        int[] mapSlots = {20, 22, 24};
        for (int i = 0; i < Math.min(options.size(), mapSlots.length); i++) {
            if (slot != mapSlots[i]) {
                continue;
            }
            DuelMapOption option = options.get(i);
            if (!option.available()) {
                return;
            }
            duelService.applyMapChoice(settings, option);
            player.openInventory(DuelGui.buildRulesGui(settings));
            return;
        }
        if (slot == SLOT_MAP_CANCEL) {
            duelService.clearBuilder(player.getUniqueId());
            player.closeInventory();
        }
    }

    private void handleRules(int slot, Player player, DuelSettings settings) {
        switch (slot) {
            case SLOT_RULES_NONE -> {
                settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.NONE);
                player.openInventory(DuelGui.buildItemRulesGui(settings));
                return;
            }
            case SLOT_RULES_PLACE_ONLY -> {
                settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_ONLY);
                player.openInventory(DuelGui.buildPlaceOnlyGui());
                return;
            }
            case SLOT_RULES_PLACE_BREAK -> {
                if (!settings.isMapSupportsBlockBreaking()) {
                    return;
                }
                settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_BREAK);
                player.openInventory(DuelGui.buildExtrasGui(settings));
                return;
            }
            case SLOT_RULES_BACK -> player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), settings));
            case SLOT_RULES_CANCEL -> {
                duelService.clearBuilder(player.getUniqueId());
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    private void handlePlaceOnly(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        switch (slot) {
            case SLOT_PLACE_ONLY_COBWEB_UTILS -> {
                settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.COBWEB_UTILS);
                settings.clearExplosiveRules();
                player.openInventory(DuelGui.buildItemRulesGui(settings, returnToConfirm));
            }
            case SLOT_PLACE_ONLY_ALL_BLOCKS -> {
                settings.setPlaceOnlyMode(DuelSettings.PlaceOnlyMode.ALL_BLOCKS);
                player.openInventory(duelService.shouldShowExplosivesMenu(settings)
                    ? DuelGui.buildExtrasGui(settings, returnToConfirm)
                    : DuelGui.buildItemRulesGui(settings, returnToConfirm));
            }
            case SLOT_PLACE_ONLY_BACK -> player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildRulesGui(settings));
            default -> {
            }
        }
    }

    private void handleExtras(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        switch (slot) {
            case SLOT_EXTRAS_CRYSTALS_ANCHORS -> {
                settings.setAllowCrystalsAnchors(!settings.isAllowCrystalsAnchors());
                player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
            }
            case SLOT_EXTRAS_EXPLOSIVE_MINECARTS -> {
                settings.setAllowExplosiveMinecarts(!settings.isAllowExplosiveMinecarts());
                player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
            }
            case SLOT_EXTRAS_OTHER_EXPLOSIVES -> {
                settings.setAllowOtherExplosives(!settings.isAllowOtherExplosives());
                player.openInventory(DuelGui.buildExtrasGui(settings, returnToConfirm));
            }
            case SLOT_EXTRAS_BACK -> openExtrasBack(player, settings, returnToConfirm);
            case SLOT_EXTRAS_NEXT -> player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildItemRulesGui(settings));
            default -> {
            }
        }
    }

    private void openExtrasBack(Player player, DuelSettings settings, boolean returnToConfirm) {
        if (returnToConfirm) {
            player.openInventory(DuelGui.buildConfirmGui(settings));
        } else if (settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.PLACE_BREAK) {
            player.openInventory(DuelGui.buildRulesGui(settings));
        } else {
            player.openInventory(DuelGui.buildPlaceOnlyGui());
        }
    }

    private void handleItemRules(int slot, Player player, DuelSettings settings, ClickType click, boolean returnToConfirm) {
        if (toggleItemRule(slot, settings, click)) {
            player.openInventory(DuelGui.buildItemRulesGui(settings, returnToConfirm));
            return;
        }
        if (slot == SLOT_ITEM_BACK) {
            openItemRulesBack(player, settings, returnToConfirm);
            return;
        }
        if (slot == SLOT_ITEM_WAGER) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
        }
    }

    private boolean toggleItemRule(int slot, DuelSettings settings, ClickType click) {
        switch (slot) {
            case SLOT_ITEM_ENDER_PEARL -> {
                if (click.isRightClick()) {
                    settings.setEnderPearlCooldownSeconds(nextCooldown(settings.getEnderPearlCooldownSeconds()));
                } else {
                    settings.setAllowEnderPearls(!settings.isAllowEnderPearls());
                }
                return true;
            }
            case SLOT_ITEM_WIND_CHARGE -> {
                if (click.isRightClick()) {
                    settings.setWindChargeCooldownSeconds(nextCooldown(settings.getWindChargeCooldownSeconds()));
                } else {
                    settings.setAllowWindCharges(!settings.isAllowWindCharges());
                }
                return true;
            }
            case SLOT_ITEM_MACE -> settings.setAllowMaces(!settings.isAllowMaces());
            case SLOT_ITEM_CHORUS -> settings.setAllowChorusFruit(!settings.isAllowChorusFruit());
            case SLOT_ITEM_SPEAR -> settings.setAllowSpears(!settings.isAllowSpears());
            case SLOT_ITEM_ELYTRA -> settings.setAllowElytras(!settings.isAllowElytras());
            case SLOT_ITEM_ENDER_CHEST -> settings.setAllowEnderChests(!settings.isAllowEnderChests());
            default -> {
                return false;
            }
        }
        return true;
    }

    private void openItemRulesBack(Player player, DuelSettings settings, boolean returnToConfirm) {
        if (returnToConfirm) {
            player.openInventory(DuelGui.buildConfirmGui(settings));
        } else if (duelService.shouldShowExplosivesMenu(settings)) {
            player.openInventory(DuelGui.buildExtrasGui(settings));
        } else {
            player.openInventory(settings.getPlaceBreakMode() == DuelSettings.PlaceBreakMode.NONE
                ? DuelGui.buildRulesGui(settings)
                : DuelGui.buildPlaceOnlyGui());
        }
    }

    private void handleRequestPreview(int slot, Player player) {
        if (slot == SLOT_PREVIEW_ACCEPT) {
            duelService.confirmAcceptRequest(player);
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREVIEW_DENY) {
            duelService.denyRequest(player);
            player.closeInventory();
            return;
        }
        if (slot == SLOT_PREVIEW_CLOSE) {
            player.closeInventory();
        }
    }

    private int nextCooldown(int current) {
        return switch (current) {
            case 0 -> 5;
            case 5 -> 10;
            case 10 -> 15;
            case 15 -> 30;
            default -> 0;
        };
    }

    private void handleWager(int slot, Player player, DuelSettings settings, boolean returnToConfirm) {
        Double delta = WAGER_DELTAS.get(slot);
        if (delta != null) {
            settings.setWager(clampWager(settings.getWager() + delta));
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        if (slot == SLOT_WAGER_CONFIRM) {
            player.openInventory(DuelGui.buildConfirmGui(settings));
            return;
        }
        if (slot == SLOT_WAGER_BACK) {
            player.openInventory(returnToConfirm ? DuelGui.buildConfirmGui(settings) : DuelGui.buildItemRulesGui(settings));
            return;
        }
        if (slot == SLOT_WAGER_CLEAR) {
            settings.setWager(0D);
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        if (slot == SLOT_WAGER_CUSTOM) {
            awaitingWagerInput.put(player.getUniqueId(), returnToConfirm);
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "[Duel] " + ChatColor.YELLOW + "Type a custom wager amount in chat. Type " + ChatColor.AQUA + "cancel" + ChatColor.YELLOW + " to back out.");
        }
    }

    private double clampWager(double wager) {
        return Math.max(0D, Math.min(duelService.maxWager(), wager));
    }

    private void applyCustomWager(Player player, String input) {
        Boolean returnToConfirm = awaitingWagerInput.remove(player.getUniqueId());
        if (returnToConfirm == null) {
            return;
        }
        BuilderSession builder = duelService.getBuilder(player.getUniqueId());
        if (builder == null) {
            return;
        }
        DuelSettings settings = builder.settings();
        if (input.equalsIgnoreCase("cancel")) {
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        double wager;
        try {
            wager = Double.parseDouble(input);
        } catch (NumberFormatException ex) {
            player.sendMessage(ChatColor.RED + "[Duel] " + ChatColor.GRAY + "Invalid amount. Use a number or type cancel.");
            player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
            return;
        }
        settings.setWager(clampWager(wager));
        player.sendMessage(ChatColor.GOLD + "[Duel] " + ChatColor.GREEN + "Wager set to $" + DuelGui.formatAmount(settings.getWager()) + ".");
        player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), returnToConfirm));
    }

    private void handleConfirm(int slot, Player player, DuelSettings settings) {
        switch (slot) {
            case SLOT_CONFIRM_EDIT_MAP -> player.openInventory(DuelGui.buildMapGui(duelService.mapOptions(), settings));
            case SLOT_CONFIRM_EDIT_RULES -> player.openInventory(DuelGui.buildRulesGui(settings));
            case SLOT_CONFIRM_EDIT_ITEMS -> player.openInventory(DuelGui.buildItemRulesGui(settings, true));
            case SLOT_CONFIRM_EDIT_EXTRAS -> {
                if (duelService.shouldShowExplosivesMenu(settings)) {
                    player.openInventory(DuelGui.buildExtrasGui(settings, true));
                }
            }
            case SLOT_CONFIRM_EDIT_WAGER -> player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager(), true));
            case SLOT_CONFIRM_BACK_TO_WAGER -> player.openInventory(DuelGui.buildWagerGui(settings, duelService.maxWager()));
            case SLOT_CONFIRM_SEND -> {
                duelService.sanitizeBuilderSettings(settings);
                duelService.sendRequest(player);
                player.closeInventory();
            }
            case SLOT_CONFIRM_CANCEL -> {
                duelService.clearBuilder(player.getUniqueId());
                player.closeInventory();
            }
            default -> {
            }
        }
    }
}
