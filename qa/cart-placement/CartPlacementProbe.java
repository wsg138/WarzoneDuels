import com.mojang.authlib.GameProfile;
import dev.minecraft.warzoneduels.WarzoneDuelsPlugin;
import dev.minecraft.warzoneduels.domain.ActiveDuel;
import dev.minecraft.warzoneduels.domain.DuelSettings;
import dev.minecraft.warzoneduels.domain.MatchParticipant;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Minecart;
import org.bukkit.plugin.java.JavaPlugin;

/** Server-side acceptance fixture for disposable Paper 26.2. */
public final class CartPlacementProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        if (!Boolean.getBoolean("warzoneduels.cart-probe") || !"127.0.0.1".equals(Bukkit.getIp())) {
            return;
        }
        Bukkit.getScheduler().runTask(this, () -> {
            try {
                runChecks();
                getLogger().info("Cart placement checks passed");
            } catch (Exception | AssertionError failure) {
                getLogger().log(java.util.logging.Level.SEVERE, "Cart placement checks failed", failure);
            } finally {
                Bukkit.shutdown();
            }
        });
    }

    private void runChecks() throws ReflectiveOperationException {
        var plugin = (WarzoneDuelsPlugin) Bukkit.getPluginManager().getPlugin("WarzoneDuels");
        var service = plugin.duelService();
        var server = ((CraftServer) Bukkit.getServer()).getServer();
        var world = (CraftWorld) Bukkit.getWorld("world");
        var profile = new GameProfile(UUID.randomUUID(), "CartProbe");
        var player = new ServerPlayer(server, world.getHandle(), profile, ClientInformation.createDefault());
        player.connection = new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND),
            player, CommonListenerCookie.createInitial(profile, false));
        var settings = new DuelSettings();
        settings.setMapId("desert");
        settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_BREAK);
        settings.setAllowExplosiveMinecarts(true);
        var opponent = new MatchParticipant(UUID.randomUUID(), "Opponent");
        var match = new ActiveDuel(new MatchParticipant(player.getUUID(), "CartProbe"), opponent, settings, 0);
        // Stage an active match without connecting two clients or replacing placement event handlers.
        var active = service.getClass().getDeclaredField("activeDuel");
        active.setAccessible(true);
        if (active.get(service) != null) throw new IllegalStateException("Expected a fresh test arena");
        active.set(service, match);
        try {
            check(world, player, Material.TNT_MINECART, -319, true);
            check(world, player, Material.MINECART, -319, false);
            check(world, player, Material.TNT_MINECART, -376, false);
            settings.setAllowExplosiveMinecarts(false);
            check(world, player, Material.TNT_MINECART, -319, false);
            settings.setAllowExplosiveMinecarts(true);
            settings.setPlaceBreakMode(DuelSettings.PlaceBreakMode.PLACE_ONLY);
            check(world, player, Material.TNT_MINECART, -319, false);
        } finally {
            active.set(service, null);
        }
        check(world, player, Material.TNT_MINECART, -319, false);
    }

    private void check(World world, ServerPlayer player, Material item, int z, boolean expected) {
        var rail = world.getBlockAt(-140, 171, z);
        rail.getRelative(BlockFace.DOWN).setType(Material.SANDSTONE, false);
        rail.setType(Material.RAIL, false);
        player.setPos(-140.5, 172, z - .5);
        player.getBukkitEntity().getInventory().setItemInMainHand(new org.bukkit.inventory.ItemStack(item));
        var pos = new BlockPos(rail.getX(), rail.getY(), rail.getZ());
        var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        player.gameMode.useItemOn(player, ((CraftWorld) world).getHandle(), player.getMainHandItem(),
            InteractionHand.MAIN_HAND, hit);
        var carts = world.getNearbyEntities(rail.getLocation().add(.5, .5, .5), 1, 1, 1)
            .stream().filter(Minecart.class::isInstance).toList();
        getLogger().info(item + " at " + pos + " expected=" + expected + " carts=" + carts.size());
        if (carts.size() != (expected ? 1 : 0)) throw new AssertionError("Unexpected cart count");
        carts.forEach(org.bukkit.entity.Entity::remove);
    }
}
