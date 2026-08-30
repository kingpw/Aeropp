package com.aeropp.mobtest.client;

import com.aeropp.mobtest.AeroppMobTest;
import com.aeropp.mobtest.content.ModEntities;
import net.minecraft.client.renderer.entity.GhastRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = AeroppMobTest.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SKY_RAIDER.get(), GhastRenderer::new);
    }
}
