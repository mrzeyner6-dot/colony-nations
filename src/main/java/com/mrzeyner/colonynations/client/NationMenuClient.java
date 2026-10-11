package com.mrzeyner.colonynations.client;

import com.mrzeyner.colonynations.ColonyNations;
import com.mrzeyner.colonynations.network.NationMenuRequestPayload;
import com.mrzeyner.colonynations.network.NationOverviewPayload;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

@EventBusSubscriber(modid = ColonyNations.MOD_ID, value = Dist.CLIENT)
public final class NationMenuClient {
    private NationMenuClient() {
    }

    public static void handle(NationOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            NationOverviewPayload.Overview overview = payload.overview() == null
                    ? NationOverviewPayload.Overview.none()
                    : payload.overview();
            if (minecraft.screen instanceof NationMenuScreen menu) {
                menu.apply(overview);
                return;
            }
            if (payload.open()) {
                minecraft.setScreen(new NationMenuScreen(overview));
            }
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        while (NationKeys.OPEN_MENU.consumeClick()) {
            if (minecraft.player != null && minecraft.screen == null) {
                PacketDistributor.sendToServer(new NationMenuRequestPayload(true));
            }
        }
    }
}
