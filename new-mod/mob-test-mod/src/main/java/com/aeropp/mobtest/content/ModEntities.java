package com.aeropp.mobtest.content;

import com.aeropp.mobtest.AeroppMobTest;
import com.aeropp.mobtest.entity.SkyRaiderEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
        DeferredRegister.create(Registries.ENTITY_TYPE, AeroppMobTest.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<SkyRaiderEntity>> SKY_RAIDER =
        ENTITY_TYPES.register("sky_raider", () -> EntityType.Builder
            .of(SkyRaiderEntity::new, MobCategory.MONSTER)
            .sized(4.0F, 4.0F)
            .fireImmune()
            .canSpawnFarFromPlayer()
            .clientTrackingRange(10)
            .updateInterval(3)
            .build("aeropp_mobtest:sky_raider"));

    private ModEntities() {
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(SKY_RAIDER.get(), SkyRaiderEntity.createAttributes().build());
    }
}
