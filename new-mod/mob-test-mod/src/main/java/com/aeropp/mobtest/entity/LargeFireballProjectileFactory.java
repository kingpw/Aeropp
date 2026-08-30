package com.aeropp.mobtest.entity;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class LargeFireballProjectileFactory implements ProjectileFactory<LargeFireball> {
    @Override
    public LargeFireball create(Level level,
                                LivingEntity owner,
                                Vec3 direction,
                                int power) {
        return new LargeFireball(level, owner, direction, power);
    }
}
