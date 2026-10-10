package com.mrzeyner.colonynations.compat.minecolonies;

import java.util.UUID;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.command.Messages;
import com.mrzeyner.colonynations.data.NationSavedData;
import com.mrzeyner.colonynations.nation.CityRef;
import com.mrzeyner.colonynations.nation.Nation;
import com.mrzeyner.colonynations.nation.NationException;

import com.minecolonies.api.IMinecoloniesAPI;
import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.eventbus.events.colony.ColonyCreatedModEvent;
import com.minecolonies.api.eventbus.events.colony.ColonyDeletedModEvent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Attaches MineColonies colonies to the owner's nation.
 */
public final class NationColonyEvents {
    private NationColonyEvents() {
    }

    public static void register() {
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyCreatedModEvent.class, NationColonyEvents::onCreated);
        IMinecoloniesAPI.getInstance().getEventBus().subscribe(ColonyDeletedModEvent.class, NationColonyEvents::onDeleted);
    }

    private static void onCreated(ColonyCreatedModEvent event) {
        IColony colony = event.getColony();
        UUID owner = colony.getPermissions().getOwner();
        MinecraftServer server = server(colony);
        if (owner == null || server == null) {
            return;
        }
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            ignored(data, colony);
            return;
        }
        Nation nation = data.registry().byOwner(owner).orElse(null);
        if (nation == null) {
            return;
        }
        try {
            CityRef city = MineColoniesCities.cityRef(colony);
            data.registry().addColony(nation.id(), city);
            data.setDirty();
            CityTerritory.onCityJoined(server, data, colony, server.overworld().getGameTime());
        } catch (NationException exception) {
            ColonyNations.LOGGER.warn(
                    "Could not add colony {} in {} to nation {}",
                    colony.getID(),
                    colony.getDimension().location(),
                    nation.name());
            return;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player != null) {
            Messages.event(
                    player,
                    "command.colony_nations.city.auto",
                    colony.getName(),
                    colony.getID(),
                    nation.name());
        }
    }

    private static void onDeleted(ColonyDeletedModEvent event) {
        IColony colony = event.getColony();
        MinecraftServer server = server(colony);
        if (server == null) {
            return;
        }
        NationSavedData data = NationSavedData.get(server);
        if (data.unreadable()) {
            ignored(data, colony);
            return;
        }
        CityRef city = MineColoniesCities.cityRef(colony);
        Nation nation = data.registry().byColony(city).orElse(null);
        if (nation == null) {
            return;
        }
        data.registry().removeColony(nation.id(), city);
        data.setDirty();
        TerritorySync.onCityLeft(server, data, colony, server.overworld().getGameTime());
    }

    private static void ignored(NationSavedData data, IColony colony) {
        ColonyNations.LOGGER.warn(
                "Ignored a change to colony {} because nation data version {} could not be read.",
                colony.getID(),
                data.unreadVersion());
    }

    private static MinecraftServer server(IColony colony) {
        Level level = colony.getWorld();
        if (level instanceof ServerLevel serverLevel) {
            return serverLevel.getServer();
        }
        return null;
    }
}
