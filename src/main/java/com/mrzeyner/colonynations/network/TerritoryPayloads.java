package com.mrzeyner.colonynations.network;

import com.mrzeyner.colonynations.compat.minecolonies.NationMenuServer;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public final class TerritoryPayloads {
    private TerritoryPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1")
                .playToClient(
                        TerritorySnapshotPayload.TYPE,
                        TerritorySnapshotPayload.STREAM_CODEC,
                        TerritorySnapshotPayload::handle)
                .playToClient(
                        TerritoryDeltaPayload.TYPE,
                        TerritoryDeltaPayload.STREAM_CODEC,
                        TerritoryDeltaPayload::handle)
                .playToServer(
                        NationMenuRequestPayload.TYPE,
                        NationMenuRequestPayload.STREAM_CODEC,
                        NationMenuServer::request)
                .playToServer(
                        NationActionPayload.TYPE,
                        NationActionPayload.STREAM_CODEC,
                        NationMenuServer::action);
    }
}
