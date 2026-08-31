package com.testmod.entity;

import java.util.EnumSet;

import org.joml.Vector3f;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 大型怪物飞艇：12×4×4 的大型敌对飞行要塞。
 *
 * <p>碰撞判定全部交给部件实体（末影龙同款 {@link PartEntity} 机制）：
 * 3 段 4×4×4 舰体沿朝向间隔 4 格拼出 12×4×4，左右两侧各一个 2×2×2 独立炮塔。
 * 本体 AABB 只用于追踪/剔除，{@link #isPickable()} 为 false（伤害只能打在部件上）。
 *
 * <p>炮塔：每侧独立计时，3 秒一轮、一轮 2 发（间隔 5 tick）、发射原版大火球；
 * 有射界限制（外侧 ±75°、俯仰 +30°/−60°、48 格、需视线），模型随目标转动，
 * 独立 60 点耐久，被打坏后该侧永久停火并冒烟。
 */
public class Large_Monster_Airship_Entity extends FlyingMob implements Enemy {

    // ===== 可调参数（集中在此，方便实测微调）=====
    /** 舰体尺寸（格）：12 长 × 4 宽 × 4 高，由 3 段部件拼出 */
    private static final float HULL_W = 4.0F, HULL_H = 4.0F;
    private static final double HULL_SPACING = 4.0D;
    private static final float TURRET_SIZE = 2.0F;
    /** 炮塔中心距舰体中心的侧向距离（2 格 = 炮塔一半嵌进舰体，模拟半圆炮塔） */
    private static final double TURRET_SIDE = 2.0D;
    private static final double TURRET_Y = 0.875D;
    private static final float TURRET_MAX_HP = 60.0F;

    private static final int ROUND_COOLDOWN = 60;
    private static final int SHOTS_PER_ROUND = 2;
    private static final int SHOT_GAP = 5;
    private static final int EXPLOSION_POWER = 1;
    private static final double FIRE_RANGE = 48.0D;
    private static final double ARC_H = 75.0D, ARC_UP = 30.0D, ARC_DOWN = 60.0D;
    private static final float TURRET_TURN = 4.5F;
    private static final float HULL_TURN = 0.3F;
    private static final double HOVER_MIN = 12.0D;
    private static final double ENGAGE_DIST = 30.0D;
    /** 索敌范围 / 脱战范围 */
    private static final double TARGET_RANGE = 64.0D, TARGET_KEEP = 80.0D;
    /** 待机时炮塔扫描摆动幅度 */
    private static final float IDLE_SWEEP = 35.0F;

    private static final EntityDataAccessor<Vector3f> DATA_STAR =
            SynchedEntityData.defineId(Large_Monster_Airship_Entity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_PORT =
            SynchedEntityData.defineId(Large_Monster_Airship_Entity.class, EntityDataSerializers.VECTOR3);

    private final AirshipPart hullFore, hullMid, hullAft;
    private final Turret starboard, port;
    private final PartEntity<?>[] parts;
    /** 上一 tick 本体位置，用于把甲板上的玩家带着走 */
    private double prevX, prevZ;

    public Large_Monster_Airship_Entity(EntityType<? extends Large_Monster_Airship_Entity> type, Level level) {
        super(type, level);
        this.moveControl = new AirshipMoveControl(this);
        this.xpReward = 50;
        this.hullFore = new AirshipPart(this, HULL_W, HULL_H);
        this.hullMid = new AirshipPart(this, HULL_W, HULL_H);
        this.hullAft = new AirshipPart(this, HULL_W, HULL_H);
        this.starboard = new Turret(true);
        this.port = new Turret(false);
        this.parts = new PartEntity<?>[]{this.hullFore, this.hullMid, this.hullAft, this.starboard.part, this.port.part};
        // 部件 id 必须是本体 id 的连续后继（NeoForge 修 MC-158205 的做法），
        // 否则客户端点击炮塔时服务端找不到对应部件。
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 360.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.35D)
                .add(Attributes.FLYING_SPEED, 0.35D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_STAR, new Vector3f(0.0F, 0.0F, TURRET_MAX_HP));
        builder.define(DATA_PORT, new Vector3f(0.0F, 0.0F, TURRET_MAX_HP));
    }

    /** 给渲染用：炮塔状态 (yaw°, pitch°, hp) */
    public Vector3f getTurretState(boolean starboardSide) {
        return this.entityData.get(starboardSide ? DATA_STAR : DATA_PORT);
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new EngageGoal(this));
        this.goalSelector.addGoal(5, new FloatAroundGoal(this));
        // 用 getNearestPlayer 手动索敌：NearestAttackableTargetGoal 走战斗目标条件
        // （canBeSeenAsEnemy / mustSee / 难度等），创造模式玩家会被挡掉打不起来。
        // 中型怪物飞艇当年就踩过这个坑，这里沿用同一方案。
        this.targetSelector.addGoal(1, new TargetNearestPlayerGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        this.updateParts();
        if (this.level().isClientSide()) {
            return;
        }
        if (this.tickCount == 1) {
            this.prevX = this.getX();
            this.prevZ = this.getZ();
        } else {
            this.carryStandingPlayers();
        }
        // 高度保持：离地不足 12 格就抬升
        if (this.tickCount % 5 == 0) {
            double groundY = this.scanGroundY();
            if (this.getY() - groundY < HOVER_MIN) {
                this.moveControl.setWantedPosition(this.getX(), groundY + HOVER_MIN, this.getZ(), 1.0D);
            }
        }
        // 炮塔：转向 + 射击（无目标时归位），损毁则冒烟
        LivingEntity target = this.getTarget();
        this.starboard.tick(target);
        this.port.tick(target);
    }

    /** 把站在舰体甲板上的玩家带着走，并让玩家不掉下去（模拟实体碰撞箱） */
    private void carryStandingPlayers() {
        double dx = this.getX() - this.prevX;
        double dz = this.getZ() - this.prevZ;
        this.prevX = this.getX();
        this.prevZ = this.getZ();
        for (AirshipPart part : new AirshipPart[]{this.hullFore, this.hullMid, this.hullAft}) {
            AABB bb = part.getBoundingBox();
            AABB top = new AABB(bb.minX, bb.maxY, bb.minZ, bb.maxX, bb.maxY + 0.4D, bb.maxZ);
            for (Player player : this.level().getEntitiesOfClass(Player.class, top)) {
                if (player.isPassenger() || player.getAbilities().flying) {
                    continue;
                }
                double dy = bb.maxY - player.getY();
                player.move(MoverType.PISTON, new Vec3(dx, dy, dz));
                player.setOnGround(true);
                player.fallDistance = 0.0F;
            }
        }
    }

    /** 部件跟随本体位置/朝向 */
    private void updateParts() {
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        placePart(this.hullFore, fwd.scale(HULL_SPACING), 0.0D);
        placePart(this.hullMid, Vec3.ZERO, 0.0D);
        placePart(this.hullAft, fwd.scale(-HULL_SPACING), 0.0D);
        placePart(this.starboard.part, this.starboard.outward().scale(TURRET_SIDE), TURRET_Y);
        placePart(this.port.part, this.port.outward().scale(TURRET_SIDE), TURRET_Y);
    }

    private void placePart(PartEntity<?> part, Vec3 offset, double dy) {
        double x = this.getX() + offset.x, y = this.getY() + dy, z = this.getZ() + offset.z;
        part.setPos(x, y, z);
        part.xo = part.xOld = x;
        part.yo = part.yOld = y;
        part.zo = part.zOld = z;
    }

    /** 部件受击：炮塔扣自己的耐久，舰体转给本体血量 */
    public boolean hurtPart(AirshipPart part, DamageSource source, float amount) {
        Turret turret = part == this.starboard.part ? this.starboard : (part == this.port.part ? this.port : null);
        if (turret == null) {
            return this.hurt(source, amount);
        }
        if (turret.isDead()) {
            return false;
        }
        turret.damage(amount);
        return true;
    }

    /** 向下扫描地面高度（虚空/海上则返回自身高度） */
    private double scanGroundY() {
        Vec3 from = this.position();
        BlockHitResult hit = this.level().clip(new ClipContext(
                from, from.subtract(0.0D, 64.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().getY() + 1.0D : this.getY();
    }

    // ===== 多部件实体接口（NeoForge）=====
    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return this.parts;
    }

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < this.parts.length; i++) {
            this.parts[i].setId(id + i + 1);
        }
    }

    /** 本体不可点击：所有伤害必须打在部件上 */
    @Override
    public boolean isPickable() {
        return false;
    }

    /** 免疫自己造成的伤害（自家火球擦到船体/爆炸溅射不该自伤） */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.getEntity() == this || super.isInvulnerableTo(source);
    }

    /** 万吨巨舰推不动 */
    @Override
    public boolean isPushable() {
        return false;
    }

    // ===== 音效（借用铁傀儡的金属质感）=====
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.IRON_GOLEM_STEP;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.IRON_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.IRON_GOLEM_DEATH;
    }

    /** 坠毁：沿舰体炸出一串爆炸粒子 */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel server) {
            float rad = this.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
            for (int i = -6; i <= 6; i += 2) {
                Vec3 p = this.position().add(fwd.scale(i)).add(0.0D, 2.0D, 0.0D);
                server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            server.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.GENERIC_EXPLODE.value(), this.getSoundSource(), 4.0F, 0.5F);
        }
        super.die(source);
    }

    /** 巨型模型防止近距离被剔除 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(10.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("TurretStarHp", this.starboard.hp);
        tag.putFloat("TurretPortHp", this.port.hp);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TurretStarHp")) {
            this.starboard.hp = tag.getFloat("TurretStarHp");
            this.port.hp = tag.getFloat("TurretPortHp");
            this.starboard.sync();
            this.port.sync();
        }
    }

    private static float approach(float current, float want, float maxStep) {
        return current + Mth.clamp(Mth.wrapDegrees(want - current), -maxStep, maxStep);
    }

    // ============================================================
    //  炮塔（服务端逻辑 + 同步状态）
    // ============================================================
    private class Turret {
        private final boolean starboardSide;
        private final AirshipPart part;
        private float yaw, pitch, hp = TURRET_MAX_HP;
        private float syncedYaw, syncedPitch;
        private int cooldown = ROUND_COOLDOWN, shotsLeft, gap;

        Turret(boolean starboardSide) {
            this.starboardSide = starboardSide;
            this.part = new AirshipPart(Large_Monster_Airship_Entity.this, TURRET_SIZE, TURRET_SIZE);
        }

        boolean isDead() {
            return this.hp <= 0.0F;
        }

        /** 炮塔外侧法向（世界坐标，水平） */
        Vec3 outward() {
            float rad = getYRot() * Mth.DEG_TO_RAD;
            double s = this.starboardSide ? 1.0D : -1.0D;
            return new Vec3(-Mth.cos(rad) * s, 0.0D, -Mth.sin(rad) * s);
        }

        /** 炮塔中心（世界坐标） */
        Vec3 center() {
            return position().add(outward().scale(TURRET_SIDE)).add(0.0D, TURRET_Y + TURRET_SIZE * 0.5D, 0.0D);
        }

        void damage(float amount) {
            this.hp = Math.max(0.0F, this.hp - amount);
            sync();
            playSound(SoundEvents.ANVIL_PLACE, 0.8F, 1.5F);
            if (isDead()) {
                Vec3 c = center();
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 8, 0.9D, 0.9D, 0.9D, 0.0D);
                }
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
            }
        }

        void sync() {
            entityData.set(this.starboardSide ? DATA_STAR : DATA_PORT, new Vector3f(this.yaw, this.pitch, this.hp));
            this.syncedYaw = this.yaw;
            this.syncedPitch = this.pitch;
        }

        void tick(LivingEntity target) {
            if (isDead()) {
                if (tickCount % 4 == 0 && level() instanceof ServerLevel server) {
                    Vec3 c = center();
                    server.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0D, c.z, 2, 0.4D, 0.2D, 0.4D, 0.01D);
                }
                return;
            }
            Vec3 c = center();
            boolean valid = target != null && target.isAlive()
                    && target.distanceToSqr(c) <= FIRE_RANGE * FIRE_RANGE;
            double horiz = 0.0D, vert = 0.0D;
            boolean sight = false;
            if (valid) {
                Vec3 dir = target.getEyePosition().subtract(c).normalize();
                horiz = horizAngle(dir);
                vert = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));
                sight = getSensing().hasLineOfSight(target);
            }
            // 目标角度：有目标就盯住（超出射界则贴着射界边缘），没目标就缓慢扫描警戒
            float wantYaw, wantPitch;
            if (valid) {
                wantYaw = (float) Mth.clamp(horiz, -ARC_H, ARC_H);
                wantPitch = (float) Mth.clamp(vert, -ARC_DOWN, ARC_UP);
            } else {
                wantYaw = Mth.sin((tickCount + (this.starboardSide ? 0 : 70)) * 0.015F) * IDLE_SWEEP;
                wantPitch = -8.0F;
            }
            this.yaw = approach(this.yaw, wantYaw, TURRET_TURN);
            this.pitch = approach(this.pitch, wantPitch, TURRET_TURN);
            if (Math.abs(this.yaw - this.syncedYaw) >= 1.0F || Math.abs(this.pitch - this.syncedPitch) >= 1.0F) {
                sync();
            }
            // 必须真的转到位才开火（炮口方向与炮管视觉一致）
            boolean aimed = Math.abs(Mth.wrapDegrees((float) horiz - this.yaw)) <= 10.0F
                    && Math.abs(Mth.wrapDegrees((float) vert - this.pitch)) <= 12.0F;
            boolean canFire = valid && sight && aimed
                    && Math.abs(horiz) <= ARC_H && vert <= ARC_UP && vert >= -ARC_DOWN;
            if (!canFire) {
                this.shotsLeft = 0;
                this.cooldown = Math.min(this.cooldown, 10);
                return;
            }
            if (this.shotsLeft > 0) {
                if (--this.gap <= 0) {
                    fire(target, c);
                    this.shotsLeft--;
                    this.gap = SHOT_GAP;
                }
            } else if (--this.cooldown <= 0) {
                fire(target, c);
                this.shotsLeft = SHOTS_PER_ROUND - 1;
                this.gap = SHOT_GAP;
                this.cooldown = ROUND_COOLDOWN;
            }
        }

        /** 目标方向相对外侧法向的水平夹角（带符号，度） */
        private double horizAngle(Vec3 dir) {
            Vec3 out = outward();
            double a = Mth.atan2(dir.z, dir.x);
            double b = Mth.atan2(out.z, out.x);
            return Mth.wrapDegrees(Math.toDegrees(a - b));
        }

        /** 发射一颗原版大火球（炮口在碰撞箱外，避免自伤） */
        private void fire(LivingEntity target, Vec3 c) {
            Vec3 dir = target.getEyePosition().subtract(c).normalize();
            Vec3 muzzle = c.add(dir.scale(1.8D));
            LargeFireball ball = new LargeFireball(level(), Large_Monster_Airship_Entity.this, dir, EXPLOSION_POWER);
            ball.setPos(muzzle.x, muzzle.y, muzzle.z);
            level().addFreshEntity(ball);
            playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.7F + random.nextFloat() * 0.2F);
        }
    }

    // ============================================================
    //  部件实体：参与物理碰撞（玩家撞到舰体/炮塔会被挡住，本体 isPushable=false 不会被推走）
    // ============================================================
    public static class AirshipPart extends Airship_Part<Large_Monster_Airship_Entity> {

        public AirshipPart(Large_Monster_Airship_Entity parent, float width, float height) {
            super(parent, width, height, true);
        }

        @Override
        protected boolean hurtPart(DamageSource source, float amount) {
            return this.getParent().hurtPart(this, source, amount);
        }
    }

    // ============================================================
    //  移动控制：限速转向的飞行（巨舰 6°/s，不会瞬间转头）
    // ============================================================
    private static class AirshipMoveControl extends FlyingMoveControl {

        AirshipMoveControl(Mob mob) {
            super(mob, 20, true);
        }

        @Override
        public void tick() {
            if (this.operation != MoveControl.Operation.MOVE_TO) {
                this.mob.setYya(0.0F);
                this.mob.setZza(0.0F);
                return;
            }
            this.operation = MoveControl.Operation.WAIT;
            this.mob.setNoGravity(true);
            double dx = this.wantedX - this.mob.getX();
            double dy = this.wantedY - this.mob.getY();
            double dz = this.wantedZ - this.mob.getZ();
            if (dx * dx + dy * dy + dz * dz < 2.5000003E-7F) {
                this.mob.setYya(0.0F);
                this.mob.setZza(0.0F);
                return;
            }
            float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), want, HULL_TURN));
            this.mob.yHeadRot = this.mob.getYRot();
            this.mob.setYBodyRot(this.mob.getYRot());
            float speed = (float) (this.speedModifier * this.mob.getAttributeValue(Attributes.FLYING_SPEED));
            this.mob.setSpeed(speed);
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dy) > 1.0E-5F || Math.abs(horiz) > 1.0E-5F) {
                this.mob.setXRot(0.0F);
                this.mob.setYya(dy > 0.0D ? speed : -speed);
            }
        }
    }

    // ============================================================
    //  AI：索敌 / 漂浮游荡 / 交战（机身转向 + 保持距离）
    // ============================================================
    /**
     * 索敌：直接取最近的玩家（含创造模式，方便测试）。
     * 不用 {@code NearestAttackableTargetGoal}——它的战斗目标条件会把创造模式玩家滤掉。
     */
    private static class TargetNearestPlayerGoal extends Goal {
        private final Large_Monster_Airship_Entity mob;

        TargetNearestPlayerGoal(Large_Monster_Airship_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            Player player = this.mob.level().getNearestPlayer(this.mob, TARGET_RANGE);
            if (player != null && player.isAlive() && !player.isSpectator()) {
                this.mob.setTarget(player);
                return true;
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive() && !target.isSpectator()
                    && this.mob.distanceToSqr(target) <= TARGET_KEEP * TARGET_KEEP;
        }

        @Override
        public void stop() {
            this.mob.setTarget(null);
        }
    }

    private static class FloatAroundGoal extends Goal {
        private final Large_Monster_Airship_Entity mob;

        FloatAroundGoal(Large_Monster_Airship_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null) {
                return false;
            }
            MoveControl control = this.mob.getMoveControl();
            if (control.hasWanted()) {
                return false;
            }
            double x = this.mob.getX() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 24.0F;
            double y = this.mob.getY() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 8.0F;
            double z = this.mob.getZ() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 24.0F;
            control.setWantedPosition(x, y, z, 1.0D);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    private static class EngageGoal extends Goal {
        private final Large_Monster_Airship_Entity mob;

        EngageGoal(Large_Monster_Airship_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = this.mob.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void tick() {
            LivingEntity target = this.mob.getTarget();
            if (target == null) {
                return;
            }
            // 机身缓慢转向，让目标落进侧舷射界（正对目标时两侧炮都打不到）
            float toTarget = (float) (Mth.atan2(target.getZ() - this.mob.getZ(), target.getX() - this.mob.getX())
                    * Mth.RAD_TO_DEG) - 90.0F;
            float broadside = toTarget - 90.0F;
            float newRot = approach(this.mob.getYRot(), broadside, HULL_TURN);
            this.mob.setYRot(newRot);
            this.mob.yHeadRot = newRot;
            this.mob.setYBodyRot(newRot);

            MoveControl control = this.mob.getMoveControl();
            double distSqr = this.mob.distanceToSqr(target);
            if (distSqr > ENGAGE_DIST * ENGAGE_DIST) {
                double wantedY = Math.max(target.getY() + 8.0D, this.mob.scanGroundY() + HOVER_MIN);
                control.setWantedPosition(target.getX(), wantedY, target.getZ(), 1.0D);
            } else if (control.hasWanted()) {
                control.setWantedPosition(this.mob.getX(), this.mob.getY(), this.mob.getZ(), 0.0D);
            }
        }
    }
}
