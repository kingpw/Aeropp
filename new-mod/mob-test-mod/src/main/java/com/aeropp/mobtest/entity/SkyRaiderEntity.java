package com.aeropp.mobtest.entity;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ghast-like hostile flyer. The vanilla Ghast move control is retained by
 * extending Ghast; the copied goals only replace the private fireball goal so
 * projectile creation goes through ProjectileFactory.
 */
public final class SkyRaiderEntity extends Ghast {
    private static final double ACCELERATION_DISTANCE = 16.0D;
    private static final double MAX_BONUS_DISTANCE = 96.0D;
    private static final double BASE_MAX_SPEED = 0.12D;
    private static final double BONUS_MAX_SPEED = 0.18D;
    private static final int PROJECTILE_POWER = 1;

    private final ProjectileFactory<? extends Entity> projectileFactory = new LargeFireballProjectileFactory();

    public SkyRaiderEntity(EntityType<? extends Ghast> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
            .add(Attributes.MAX_HEALTH, 30.0D)
            .add(Attributes.FOLLOW_RANGE, 100.0D)
            .add(Attributes.MOVEMENT_SPEED, 0.1D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(5, new RandomFloatAroundGoal(this));
        this.goalSelector.addGoal(7, new GhastLookGoal(this));
        this.goalSelector.addGoal(7, new ProjectileAttackGoal(this, projectileFactory));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
            this, Player.class, 10, true, false,
            player -> Math.abs(player.getY() - this.getY()) <= 4.0D
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            this.applyDistanceAcceleration();
        }
    }

    /** Adds a bounded chase impulse; it cannot grow velocity without limit. */
    private void applyDistanceAcceleration() {
        LivingEntity target = this.getTarget();
        if (target == null || !target.isAlive()) {
            return;
        }

        double distance = Math.sqrt(this.distanceToSqr(target));
        double distanceFactor = Mth.clamp(
            (distance - ACCELERATION_DISTANCE) / MAX_BONUS_DISTANCE,
            0.0D,
            1.0D
        );
        if (distanceFactor <= 0.0D) {
            return;
        }

        Vec3 toTarget = target.getEyePosition().subtract(this.getEyePosition());
        if (toTarget.lengthSqr() < 1.0E-6D) {
            return;
        }

        Vec3 velocity = this.getDeltaMovement().add(toTarget.normalize().scale(0.015D * distanceFactor));
        double maxSpeed = BASE_MAX_SPEED + BONUS_MAX_SPEED * distanceFactor;
        if (velocity.lengthSqr() > maxSpeed * maxSpeed) {
            velocity = velocity.normalize().scale(maxSpeed);
        }
        this.setDeltaMovement(velocity);
    }

    private static final class GhastLookGoal extends Goal {
        private final SkyRaiderEntity mob;

        private GhastLookGoal(SkyRaiderEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return true;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                Vec3 movement = this.mob.getDeltaMovement();
                this.mob.setYRot(-((float) Mth.atan2(movement.x, movement.z)) * Mth.RAD_TO_DEG);
                this.mob.yBodyRot = this.mob.getYRot();
            } else if (target.distanceToSqr(this.mob) < 4096.0D) {
                double x = target.getX() - this.mob.getX();
                double z = target.getZ() - this.mob.getZ();
                this.mob.setYRot(-((float) Mth.atan2(x, z)) * Mth.RAD_TO_DEG);
                this.mob.yBodyRot = this.mob.getYRot();
            }
        }
    }

    private static final class RandomFloatAroundGoal extends Goal {
        private final SkyRaiderEntity mob;

        private RandomFloatAroundGoal(SkyRaiderEntity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!this.mob.getMoveControl().hasWanted()) {
                return true;
            }
            double x = this.mob.getMoveControl().getWantedX() - this.mob.getX();
            double y = this.mob.getMoveControl().getWantedY() - this.mob.getY();
            double z = this.mob.getMoveControl().getWantedZ() - this.mob.getZ();
            double distanceSqr = x * x + y * y + z * z;
            return distanceSqr < 1.0D || distanceSqr > 3600.0D;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }

        @Override
        public void start() {
            RandomSource random = this.mob.getRandom();
            double x = this.mob.getX() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            double y = this.mob.getY() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            double z = this.mob.getZ() + (random.nextFloat() * 2.0F - 1.0F) * 16.0D;
            this.mob.getMoveControl().setWantedPosition(x, y, z, 1.0D);
        }
    }

    private static final class ProjectileAttackGoal extends Goal {
        private final SkyRaiderEntity mob;
        private final ProjectileFactory<? extends Entity> projectileFactory;
        private int chargeTime;

        private ProjectileAttackGoal(SkyRaiderEntity mob, ProjectileFactory<? extends Entity> projectileFactory) {
            this.mob = mob;
            this.projectileFactory = projectileFactory;
        }

        @Override
        public boolean canUse() {
            return this.mob.getTarget() != null;
        }

        @Override
        public void start() {
            this.chargeTime = 0;
        }

        @Override
        public void stop() {
            this.mob.setCharging(false);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }

            if (target.distanceToSqr(this.mob) < 4096.0D && this.mob.hasLineOfSight(target)) {
                Level level = this.mob.level();
                this.chargeTime++;
                if (this.chargeTime == 10 && !this.mob.isSilent()) {
                    level.levelEvent(null, 1015, this.mob.blockPosition(), 0);
                }
                if (this.chargeTime == 20) {
                    Vec3 view = this.mob.getViewVector(1.0F);
                    Vec3 direction = new Vec3(
                        target.getX() - (this.mob.getX() + view.x * 4.0D),
                        target.getY(0.5D) - (this.mob.getY(0.5D) + 0.5D),
                        target.getZ() - (this.mob.getZ() + view.z * 4.0D)
                    ).normalize();
                    if (!this.mob.isSilent()) {
                        level.levelEvent(null, 1016, this.mob.blockPosition(), 0);
                    }
                    Entity projectile = this.projectileFactory.create(
                        level, this.mob, direction, PROJECTILE_POWER
                    );
                    projectile.setPos(
                        this.mob.getX() + view.x * 4.0D,
                        this.mob.getY(0.5D) + 0.5D,
                        this.mob.getZ() + view.z * 4.0D
                    );
                    level.addFreshEntity(projectile);
                    this.chargeTime = -40;
                }
            } else if (this.chargeTime > 0) {
                this.chargeTime--;
            }
            this.mob.setCharging(this.chargeTime > 10);
        }
    }
}
