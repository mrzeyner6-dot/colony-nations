package com.mrzeyner.colonynations.territory;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.trade.TradeTerminalBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.minecraft.world.MenuProvider;

/**
 * Foreign players cannot build, dig, bucket, ignite, or open containers on state land.
 * Doors and buttons are left alone. City land stays with MineColonies.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class StateLandGuard {
    private static final long WARN_INTERVAL_TICKS = 60L;
    private static final Map<UUID, Long> NEXT_WARNING = new ConcurrentHashMap<>();

    private StateLandGuard() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof TradeTerminalBlock) {
            return;
        }
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (denied(player, event.getPos())) {
            event.setCanceled(true);
            warn(player);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().getBlock() instanceof TradeTerminalBlock) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (denied(player, event.getPos())) {
            event.setCanceled(true);
            warn(player);
        }
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getLevel().isClientSide()) {
            return;
        }
        if (!denied(player, event.getPos())) {
            return;
        }
        ItemStack stack = player.getItemInHand(event.getHand());
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() instanceof TradeTerminalBlock) {
            return;
        }
        BlockEntity entity = event.getLevel().getBlockEntity(event.getPos());
        boolean container = state.getBlock() instanceof MenuProvider || entity instanceof MenuProvider;
        boolean bucket = stack.getItem() instanceof BucketItem;
        boolean ignite = stack.getItem() instanceof FlintAndSteelItem || stack.getItem() instanceof FireChargeItem;
        if (!container && !bucket && !ignite) {
            return;
        }
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.FAIL);
        warn(player);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        NationSavedData data = NationSavedData.get(serverLevel.getServer());
        if (data.unreadable()) {
            return;
        }
        String dimension = serverLevel.dimension().location().toString();
        event.getAffectedBlocks().removeIf(pos -> isStateLand(data, dimension, pos));
        // TODO(T-war) during a war, do not strip blocks inside the enemy's state land.
    }

    /**
     * @return true when this player must not change the chunk
     */
    private static boolean denied(ServerPlayer player, BlockPos pos) {
        // TODO(T-war) return false while this player's nation is at war with the chunk's owner.
        if (player.hasPermissions(2)) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        NationSavedData data = NationSavedData.get(level.getServer());
        if (data.unreadable() || !isStateLand(data, level.dimension().location().toString(), pos)) {
            return false;
        }
        ChunkRef chunk = chunk(level.dimension().location().toString(), pos);
        Optional<UUID> owner = data.territory().owner(chunk);
        if (owner.isEmpty()) {
            return false;
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        return nation == null || !nation.id().equals(owner.get());
    }

    private static boolean isStateLand(NationSavedData data, String dimension, BlockPos pos) {
        return data.territory().owner(chunk(dimension, pos)).isPresent();
    }

    private static ChunkRef chunk(String dimension, BlockPos pos) {
        return new ChunkRef(dimension, pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static void warn(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        long next = NEXT_WARNING.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < next) {
            return;
        }
        NEXT_WARNING.put(player.getUUID(), now + WARN_INTERVAL_TICKS);
        Messages.quietError(player, "command.colony_nations.territory.protected");
    }
}
