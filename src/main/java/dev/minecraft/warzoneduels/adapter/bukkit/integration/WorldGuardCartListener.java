package dev.minecraft.warzoneduels.adapter.bukkit.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.bukkit.event.entity.SpawnEntityEvent;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import java.util.function.BiPredicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Rail;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class WorldGuardCartListener implements Listener {
    private final BiPredicate<Player, Location> duelAllowsPlacement;

    public WorldGuardCartListener(BiPredicate<Player, Location> duelAllowsPlacement) {
        this.duelAllowsPlacement = duelAllowsPlacement;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCartSpawn(SpawnEntityEvent event) {
        if (event.getResult() != Event.Result.DEFAULT || !event.getRelevantFlags().isEmpty()
                || event.getEffectiveType() != EntityType.TNT_MINECART
                || !(event.getOriginalEvent() instanceof PlayerInteractEvent click)
                || click.getAction() != Action.RIGHT_CLICK_BLOCK || click.useItemInHand() == Event.Result.DENY
                || click.useInteractedBlock() == Event.Result.DENY
                || click.getItem() == null || click.getItem().getType() != Material.TNT_MINECART) {
            return;
        }
        Block rail = click.getClickedBlock();
        Location target = event.getTarget();
        if (rail == null || !(rail.getBlockData() instanceof Rail) || !rail.equals(target.getBlock())
                || !duelAllowsPlacement.test(click.getPlayer(), target)) {
            return;
        }
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        var point = BukkitAdapter.adapt(target);
        var actor = WorldGuardPlugin.inst().wrapPlayer(click.getPlayer());
        if (query.queryState(point, actor, Flags.TNT) == StateFlag.State.ALLOW
                && query.testBuild(point, actor, Flags.BLOCK_PLACE)
                && !query.testBuild(point, actor, Flags.PLACE_VEHICLE)) {
            event.setAllowed(true);
        }
    }
}
