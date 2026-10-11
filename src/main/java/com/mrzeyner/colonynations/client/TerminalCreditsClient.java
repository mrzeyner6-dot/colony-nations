package com.mrzeyner.colonynations.client;

import java.util.List;

import com.mrzeyner.colonynations.network.TerminalCreditsPayload;

import net.minecraft.core.BlockPos;

/**
 * Latest buyer-credit lines for the open trade terminal. The dedicated server never reads this.
 */
public final class TerminalCreditsClient {
    private static BlockPos pos = BlockPos.ZERO;
    private static List<TerminalCreditsPayload.Line> lines = List.of();

    private TerminalCreditsClient() {
    }

    public static void handle(TerminalCreditsPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        pos = payload.pos();
        lines = payload.lines();
    }

    public static List<TerminalCreditsPayload.Line> lines(BlockPos at) {
        return at != null && at.equals(pos) ? lines : List.of();
    }
}
