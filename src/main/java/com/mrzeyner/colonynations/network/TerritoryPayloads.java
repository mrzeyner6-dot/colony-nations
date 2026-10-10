package com.mrzeyner.colonynations.network;

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
                        TerritoryDeltaPayload::handle);
    }
}
