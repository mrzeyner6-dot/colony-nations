package com.mrzeyner.colonynations.compat.minecolonies.mixin;

import com.minecolonies.api.colony.IColony;
import com.minecolonies.api.colony.IColonyManager;
import com.minecolonies.core.network.messages.server.CreateColonyMessage;

import com.mrzeyner.colonynations.compat.minecolonies.NationColonyGate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * MineColonies refuses a second colony inside {@code CreateColonyMessage.onExecute}
 * when {@code getIColonyByOwner} is non-null. The server flag {@code allowinfinitecolonies}
 * only toggles the abandon button in the client town-hall screen and does not skip this check,
 * so the API is not enough.
 */
@Mixin(CreateColonyMessage.class)
public abstract class CreateColonyMessageMixin {
    @Shadow
    private BlockPos townHall;

    @Inject(method = "onExecute", at = @At("HEAD"), cancellable = true)
    private void rejectFoundingOffNationLand(IPayloadContext context, ServerPlayer player, CallbackInfo ci) {
        if (NationColonyGate.cancelFounding(player.level(), player, townHall)) {
            ci.cancel();
        }
    }

    @Redirect(
            method = "onExecute",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/minecolonies/api/colony/IColonyManager;getIColonyByOwner(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;)Lcom/minecolonies/api/colony/IColony;"
            )
    )
    private IColony allowNationOwnerToFoundAnotherColony(IColonyManager manager, Level world, Player player) {
        return NationColonyGate.blockingColony(manager, world, player, townHall, false);
    }
}
