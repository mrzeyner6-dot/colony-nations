package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.network.TerritorySync;
import com.mrzeyner.colonynations.data.StoredNotice;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.CityCenter;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Charges state-land upkeep at each overworld dawn. One missed day is settled per server tick.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class NationUpkeep {
    private static final long TICKS_PER_DAY = 24000L;

    private NationUpkeep() {
    }

    public static int run(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return 0;
        }
        int settled = 0;
        for (Nation nation : List.copyOf(data.registry().all())) {
            if (settle(server, data, nation)) {
                settled++;
            }
        }
        if (settled > 0) {
            data.setDirty();
        }
        return settled;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        long today = server.overworld().getDayTime() / TICKS_PER_DAY;
        if (data.upkeepDay() < 0L) {
            data.setUpkeepDay(today);
            data.setDirty();
            return;
        }
        if (today <= data.upkeepDay()) {
            return;
        }
        run(server);
        data.setUpkeepDay(data.upkeepDay() + 1L);
        data.setDirty();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.getServer() == null) {
            return;
        }
        NationSavedData data = NationSavedData.get(player.getServer());
        List<StoredNotice> pending = data.takeNotices(player.getUUID());
        if (pending.isEmpty()) {
            return;
        }
        for (StoredNotice notice : pending) {
            Object[] args = notice.args().toArray();
            if (notice.event()) {
                Messages.event(player, notice.key(), args);
            } else {
                Messages.info(player, notice.key(), args);
            }
        }
        data.setDirty();
    }

    private static boolean settle(MinecraftServer server, NationSavedData data, Nation nation) {
        List<CityCenter> centers = new ArrayList<>();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null || colony.getCenter() == null) {
                continue;
            }
            BlockPos pos = colony.getCenter();
            centers.add(new CityCenter(city, new ChunkRef(city.dimension(), pos.getX() >> 4, pos.getZ() >> 4)));
        }
        Set<ChunkRef> before = data.territory().chunks(nation.id());
        Upkeep.Settlement settlement = Upkeep.settle(
                nation.treasury(),
                Config.UPKEEP_PER_CHUNK.get(),
                Config.UPKEEP_RELEASE_PER_DAY.get(),
                data.territory().chunks(nation.id()),
                ColonyClaimChunks.ofNation(nation),
                centers);
        if (settlement.paid() == 0L && settlement.released().isEmpty()) {
            return false;
        }
        data.registry().setTreasury(nation.id(), nation.treasury() - settlement.paid());
        for (ChunkRef chunk : settlement.released()) {
            data.territory().release(chunk);
        }
        TerritorySync.stateChanged(server, nation.id(), before, data.territory().chunks(nation.id()));
        Nation updated = data.registry().byId(nation.id()).orElse(nation);
        boolean shortfall = !settlement.released().isEmpty();
        String key = shortfall ? "command.colony_nations.upkeep.short" : "command.colony_nations.upkeep.paid";
        String[] args = shortfall
                ? new String[] {
                    Long.toString(settlement.paid()),
                    Integer.toString(settlement.released().size()),
                    Long.toString(updated.treasury())
                }
                : new String[] {
                    Long.toString(settlement.paid()),
                    Integer.toString(data.territory().count(nation.id())),
                    Long.toString(updated.treasury())
                };
        ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
        if (owner != null) {
            deliver(owner, shortfall, key, args);
        } else {
            data.addNotice(nation.owner(), shortfall, key, args);
        }
        return true;
    }

    private static void deliver(Player player, boolean event, String key, String[] args) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (event) {
            Messages.event(serverPlayer, key, (Object[]) args);
        } else {
            Messages.info(serverPlayer, key, (Object[]) args);
        }
    }
}
