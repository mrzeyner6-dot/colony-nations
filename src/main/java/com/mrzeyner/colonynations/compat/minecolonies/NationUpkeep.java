package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.mojang.brigadier.context.CommandContext;

import com.minecolonies.api.colony.IColony;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.Config;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.data.PendingNotice;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.territory.ChunkRef;
import com.mrzeyner.colonynations.territory.CityCore;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Charges state-land upkeep. The once-per-day clock lives in {@link NationDaily}, which collects tax first.
 * Offline owners receive the line when they next log in.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class NationUpkeep {
    private NationUpkeep() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        NationSavedData data = NationSavedData.get(player.server);
        for (PendingNotice notice : data.takeNotices(player.getUUID())) {
            if ("command.colony_nations.secession.offer".equals(notice.key())
                    && Secession.replayOffer(player.createCommandSourceStack(), notice.args())) {
                continue;
            }
            Object[] args = notice.args().toArray();
            if (notice.alarm()) {
                Messages.error(player.createCommandSourceStack(), notice.key(), args);
            } else {
                Messages.info(player.createCommandSourceStack(), notice.key(), args);
            }
        }
    }

    public static int runCommand(CommandContext<CommandSourceStack> ctx) {
        run(ctx.getSource().getServer());
        return Messages.success(ctx.getSource(), "command.colony_nations.upkeep.ran");
    }

    public static void run(MinecraftServer server) {
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            return;
        }
        long gameTime = server.overworld().getGameTime();
        int perChunk = Config.UPKEEP_PER_CHUNK.get();
        int releasePerDay = Config.UPKEEP_RELEASE_PER_DAY.get();
        boolean dirty = false;
        int width = Config.FOUNDING_RING_CHUNKS.get();
        for (Nation nation : List.copyOf(data.registry().all())) {
            Set<ChunkRef> state = new HashSet<>(data.territory().chunks(nation.id()));
            if (state.isEmpty()) {
                continue;
            }
            Survey survey = survey(data, nation, gameTime);
            Set<ChunkRef> core = CityCore.among(state, List.of(survey.cities()), width);
            int annexed = state.size() - core.size();
            if (annexed == 0) {
                continue;
            }
            long cost = (long) annexed * perChunk;
            Upkeep.Charge charge = Upkeep.charge(nation.treasury(), cost);
            List<ChunkRef> released = List.of();
            if (charge.shortfall()) {
                released = Upkeep.furthestRemovable(
                        state,
                        core,
                        Set.copyOf(survey.cities()),
                        survey.centers(),
                        releasePerDay);
                for (ChunkRef chunk : released) {
                    data.territory().release(chunk);
                }
                TerritorySync.releaseAll(server, released);
            }
            if (charge.paid() == 0L && released.isEmpty()) {
                continue;
            }
            if (charge.paid() > 0L) {
                data.registry().setTreasury(nation.id(), nation.treasury() - charge.paid());
            }
            dirty = true;
            tell(server, data, nation, charge, annexed, released);
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private static void tell(
            MinecraftServer server,
            NationSavedData data,
            Nation nation,
            Upkeep.Charge charge,
            int chunks,
            List<ChunkRef> released) {
        boolean alarm = charge.shortfall();
        String key = alarm ? "command.colony_nations.upkeep.short" : "command.colony_nations.upkeep.paid";
        List<String> args = List.of(
                Long.toString(charge.paid()),
                Integer.toString(alarm ? released.size() : chunks),
                Long.toString(nation.treasury() - charge.paid()));
        ServerPlayer owner = server.getPlayerList().getPlayer(nation.owner());
        if (owner == null) {
            data.addNotice(new PendingNotice(nation.owner(), alarm, key, args));
            return;
        }
        Object[] values = args.toArray();
        if (alarm) {
            Messages.error(owner.createCommandSourceStack(), key, values);
        } else {
            Messages.info(owner.createCommandSourceStack(), key, values);
        }
    }

    private static Survey survey(NationSavedData data, Nation nation, long gameTime) {
        List<ChunkRef> cities = new ArrayList<>();
        List<Upkeep.CityPoint> centers = new ArrayList<>();
        for (CityRef city : nation.cities()) {
            IColony colony = MineColoniesCities.findByCity(city);
            if (colony == null) {
                continue;
            }
            cities.addAll(ColonyClaimCache.get().chunks(colony, gameTime));
            BlockPos center = colony.getCenter();
            centers.add(new Upkeep.CityPoint(
                    colony.getDimension().location().toString(),
                    center.getX() >> 4,
                    center.getZ() >> 4));
        }
        return new Survey(cities, centers);
    }

    private record Survey(List<ChunkRef> cities, List<Upkeep.CityPoint> centers) {
    }
}
