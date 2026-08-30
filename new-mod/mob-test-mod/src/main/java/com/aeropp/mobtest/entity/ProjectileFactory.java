package com.aeropp.mobtest.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

@FunctionalInterface
public interface ProjectileFactory<P extends Entity> {
    P create(Level level, LivingEntity owner, Vec3 direction, int power);
}
