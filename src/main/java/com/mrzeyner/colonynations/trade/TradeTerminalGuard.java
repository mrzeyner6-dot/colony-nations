package com.mrzeyner.colonynations.trade;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.compat.minecolonies.ChunkNation;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.Nation;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Placement is limited to the player's own state or city land.
 * Breaking is limited to that nation's owner or an operator, including on city land.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class TradeTerminalGuard {
    private static final long WARN_INTERVAL_TICKS = 60L;
    private static final Map<UUID, Long> NEXT_WARNING = new ConcurrentHashMap<>();

    private TradeTerminalGuard() {
    }

    /**
     * Opens before MineColonies can cancel a right click on city land.
     * Cancelling here means the block's own use method does not open a second time.
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onOpen(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof TradeTerminalBlock)) {
            return;
        }
        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof TradeTerminalBlockEntity terminal)) {
            return;
        }
        player.openMenu(terminal, buf -> buf.writeBlockPos(event.getPos()).writeBoolean(terminal.canManage(player)));
        event.setCanceled(true);
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getPlacedBlock().getBlock() instanceof TradeTerminalBlock)) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            event.setCanceled(true);
            return;
        }
        NationSavedData data = NationSavedData.get(player.server);
        if (data.unreadable()) {
            event.setCanceled(true);
            Messages.error(player.createCommandSourceStack(), "command.colony_nations.save.unreadable", data.unreadVersion());
            return;
        }
        Nation nation = data.registry().byOwner(player.getUUID()).orElse(null);
        UUID land = ChunkNation.of(player.serverLevel(), event.getPos());
        boolean ownLand = nation != null && nation.id().equals(land);
        TerminalPlacement.Denial denial = TerminalPlacement.place(nation != null, ownLand);
        if (denial == TerminalPlacement.Denial.OK) {
            return;
        }
        event.setCanceled(true);
        String key = denial == TerminalPlacement.Denial.NO_NATION
                ? "command.colony_nations.terminal.no_nation"
                : "command.colony_nations.terminal.not_yours";
        Messages.error(player.createCommandSourceStack(), key);
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof TradeTerminalBlock) || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        BlockEntity entity = player.serverLevel().getBlockEntity(event.getPos());
        UUID nationId = entity instanceof TradeTerminalBlockEntity terminal ? terminal.nationId() : null;
        NationSavedData data = NationSavedData.get(player.server);
        boolean owns = nationId != null && data.registry().byId(nationId)
                .map(nation -> player.getUUID().equals(nation.owner()))
                .orElse(false);
        if (TerminalPlacement.mayBreak(player.hasPermissions(2), owns)) {
            return;
        }
        event.setCanceled(true);
        long now = player.serverLevel().getGameTime();
        long next = NEXT_WARNING.getOrDefault(player.getUUID(), Long.MIN_VALUE);
        if (now < next) {
            return;
        }
        NEXT_WARNING.put(player.getUUID(), now + WARN_INTERVAL_TICKS);
        Messages.quietError(player, "command.colony_nations.terminal.break");
    }
}
