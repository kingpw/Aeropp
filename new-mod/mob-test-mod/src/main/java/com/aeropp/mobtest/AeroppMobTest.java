package com.aeropp.mobtest;

import com.aeropp.mobtest.content.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(AeroppMobTest.MOD_ID)
public final class AeroppMobTest {
    public static final String MOD_ID = "aeropp_mobtest";

    public AeroppMobTest(IEventBus modBus) {
        ModEntities.ENTITY_TYPES.register(modBus);
        modBus.addListener(ModEntities::registerAttributes);
    }
}
