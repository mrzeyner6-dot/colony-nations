package com.mrzeyner.colonynations.compat.minecolonies;

import com.mojang.brigadier.context.CommandContext;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.territory.Upkeep;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * One pass per new overworld day: taxes, then upkeep. Happiness and separatism join this same pass later.
 * The operator command runs the pass again and does not move {@code lastUpkeepDay}.
 */
@EventBusSubscriber(modid = ColonyNations.MOD_ID)
public final class NationDaily {
    private static final long TICKS_PER_SECOND = 20L;

    private NationDaily() {
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.overworld().getGameTime() % TICKS_PER_SECOND != 0L) {
            return;
        }
        NationSavedData data = NationSavedData.get(server);
        Upkeep.Due due = Upkeep.due(data.lastUpkeepDay(), server.overworld().getDayTime());
        if (data.lastUpkeepDay().isEmpty() || due.charge()) {
            data.setLastUpkeepDay(due.recordedDay());
        }
        if (due.charge()) {
            run(server);
        }
    }

    public static int runCommand(CommandContext<CommandSourceStack> ctx) {
        run(ctx.getSource().getServer());
        return Messages.success(ctx.getSource(), "command.colony_nations.day.ran");
    }

    public static void run(MinecraftServer server) {
        NationTaxes.collect(server);
        NationUpkeep.run(server);
    }
}
