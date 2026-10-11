package com.mrzeyner.colonynations.territory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.MenuProvider;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * State land blocks edits from anyone except the nation owner and operators.
 * City land is left to MineColonies. Walking, riding, doors and buttons stay allowed.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class StateLandGuard {
    private static final long DENY_COOLDOWN_TICKS = 60L;
    private static final Map<UUID, Long> LAST_DENY = new HashMap<>();

    private StateLandGuard() {
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        String nation = deniedBy(player, event.getPos());
        if (nation == null) {
            return;
        }
        event.setCanceled(true);
        deny(player, nation);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        String nation = deniedBy(player, event.getPos());
        if (nation == null) {
            return;
        }
        event.setCanceled(true);
        deny(player, nation);
    }

    @SubscribeEvent
    public static void onUse(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos clicked = event.getPos();
        BlockPos into = clicked.relative(event.getFace());
        ItemStack stack = player.getItemInHand(event.getHand() == null ? InteractionHand.MAIN_HAND : event.getHand());
        boolean container = isContainer(level, clicked);
        boolean fluidOrFire = stack.getItem() instanceof BucketItem || stack.is(Items.FLINT_AND_STEEL) || stack.is(Items.FIRE_CHARGE);
        if (!container && !fluidOrFire) {
            return;
        }
        String nation = deniedBy(player, container ? clicked : into);
        if (nation == null && fluidOrFire) {
            nation = deniedBy(player, clicked);
        }
        if (nation == null) {
            return;
        }
        event.setCanceled(true);
        deny(player, nation);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Iterator<BlockPos> blocks = event.getAffectedBlocks().iterator();
        while (blocks.hasNext()) {
            if (stateOwner(level, blocks.next()) != null) {
                blocks.remove();
            }
        }
    }

    /**
     * @return nation name when this player must be stopped, or null when the action may proceed
     */
    private static String deniedBy(ServerPlayer player, BlockPos pos) {
        if (!(player.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID owner = stateOwner(level, pos);
        if (owner == null) {
            return null;
        }
        // TODO(T-war) while this nation is at war, enemy players are not blocked on its state land.
        if (player.hasPermissions(2)) {
            return null;
        }
        NationSavedData data = NationSavedData.get(level.getServer());
        Nation nation = data.registry().byId(owner).orElse(null);
        if (nation != null && nation.owner().equals(player.getUUID())) {
            return null;
        }
        return nation == null ? "?" : nation.name();
    }

    private static UUID stateOwner(ServerLevel level, BlockPos pos) {
        ChunkRef chunk = new ChunkRef(level.dimension().location().toString(), pos.getX() >> 4, pos.getZ() >> 4);
        return NationSavedData.get(level.getServer()).territory().owner(chunk).orElse(null);
    }

    private static boolean isContainer(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getMenuProvider(level, pos) != null) {
            return true;
        }
        BlockEntity entity = level.getBlockEntity(pos);
        return entity instanceof MenuProvider;
    }

    private static void deny(ServerPlayer player, String nation) {
        long now = player.level().getGameTime();
        Long previous = LAST_DENY.get(player.getUUID());
        if (previous != null && now - previous < DENY_COOLDOWN_TICKS) {
            return;
        }
        LAST_DENY.put(player.getUUID(), now);
        Messages.silentError(player, "command.colony_nations.territory.protected", nation);
    }
}
