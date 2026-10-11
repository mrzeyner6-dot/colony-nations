package com.mrzeyner.colonynations.client;

import com.mrzeyner.colonynations.network.TerminalVisitPayload;
import com.mrzeyner.colonynations.trade.TerminalStation;

import net.minecraft.core.BlockPos;

/**
 * Latest station and train line for the open trade terminal. The dedicated server never reads this.
 */
public final class TerminalVisitClient {
    private static BlockPos pos = BlockPos.ZERO;
    private static TerminalVisitPayload visit = new TerminalVisitPayload(
            BlockPos.ZERO, false, "", false, "", "", TerminalStation.Kind.NONE.ordinal(), false);

    private TerminalVisitClient() {
    }

    public static void handle(TerminalVisitPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        pos = payload.pos();
        visit = payload;
    }

    public static TerminalVisitPayload visit(BlockPos at) {
        if (at != null && at.equals(pos)) {
            return visit;
        }
        return new TerminalVisitPayload(at == null ? BlockPos.ZERO : at, false, "", false, "", "", TerminalStation.Kind.NONE.ordinal(), false);
    }
}
