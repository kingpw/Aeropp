package com.testmod.entity;

import com.testmod.Test_Mod;

import org.joml.Vector3f;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 高射炮固定炮台（testmod:flak_cannon）：单碰撞箱的地面攻城/防空敌对单位。
 *
 * <p>与大型飞行实体（多部件）不同，本实体<b>只有一个碰撞箱</b>：本体 2×2×2，炮塔本身就是
 * 实体本身。它<b>不水平移动</b>、<b>受重力</b>（悬空会落到地面、落地后站住），炮塔在
 * tick 里独立索敌——水平 100 格内、高度不限的比实体高的玩家 → 平滑转向（yaw/pitch 限速）
 * → 直瞄发射 {@link Flak_Shell_Entity}（直线高射炮弹，复用现有 flak_shell）。
 *
 * <p>开火节奏为高射速压制型（冷却 20 tick、威力 40），炮口相对中心上移（高射炮管在上方），
 * 仰角可达 90°（正上方防空）、俯角 65°（打地面）。
 *
 * <p>炮塔朝向经 {@link EntityDataAccessor}&lt;Vector3f&gt;（yaw°、pitch°、hp）同步到客户端，
 * 渲染用 {@link com.testmod.client.model.Flak_Cannon_Model}。
 */
public class Flak_Cannon_Entity extends Monster {

    // ===== 可调参数 =====
    /** 索敌射程（格）——防空特化：水平 100 格 */
    private static final double RANGE = 100.0D;
    /** 垂直索敌上限（格）：高出炮台 10000 格内的玩家都能锁定 */
    private static final double VERT_RANGE = 10000.0D;
    /** 超过该三维距离不做视线遮挡检查（超长射线卡顿保护；防空高视野） */
    private static final double SIGHT_DIST = 40.0D;
    /** 玩家需高出炮台中心线的距离（格）：只打"比实体高"的目标 */
    private static final double MIN_UP = -1D;
    /** 索敌重评估间隔（tick，1 秒） */
    private static final int SCAN_INTERVAL = 20;
    /** 炮塔转向速度（度/tick） */
    private static final float TURN = 6.0F;
    /** 开火判定：对准误差（度） */
    private static final float AIM_YAW = 10.0F, AIM_PITCH = 12.0F;
    /** 无目标扫描警戒幅度（度） */
    private static final float IDLE_SWEEP = 25.0F;
    /** 射界：仰角上限（防空，接近垂直）、俯角下限（打地面）；水平全向 360° */
    private static final float ARC_UP = 90.0F, ARC_DOWN = 65.0F;
    /** 炮口外移（格，沿弹道出碰撞箱） */
    private static final double MUZZLE = 1.8D;
    /** 出膛点高度偏移（格，正=中心上方；高射炮管在上部） */
    private static final double FIRE_DY = 0.8D;
    /** 发射冷却（tick）——高射速压制 */
    private static final int COOLDOWN = 20;
    /** 单发爆炸中心伤害（威力） */
    private static final float FLAK_POWER = 40F;
    /** 炮弹飞行初速（格/tick；flak_shell 默认 1.5，防空用更快） */
    private static final double FLAK_SPEED = 6.0D;
    /** 引信随机抖动（格）：目标会移动，引信在"目标距离 + 随机余量"处引爆，模拟定时引信 */
    private static final float FUSE_JITTER = 24.0F;
    /** 发射散布（度，直瞄后叠加的随机偏移，越大越散） */
    private static final float SPREAD_DEG = 3.0F;

    /** 炮塔状态同步（yaw°, pitch°, hp） */
    private static final EntityDataAccessor<Vector3f> DATA_TURRET =
            SynchedEntityData.defineId(Flak_Cannon_Entity.class, EntityDataSerializers.VECTOR3);

    private float yaw, pitch;
    private float syncedYaw, syncedPitch;
    private int cooldown;
    /** 炮塔自己的目标（独立索敌，每秒重评估） */
    private LivingEntity target;
    private int targetCooldown;

    // ===== 目标速度估计（提前量补偿用）=====
    /** 上次采样时的目标实体（目标变了就重置采样） */
    private LivingEntity lastTargetEntity;
    /** 上次采样位置 */
    private Vec3 lastTargetPos;
    /** 目标速度估计（格/tick，滑动平均） */
    private Vec3 targetVel;
    /** 采样间隔倒计时 */
    private int targetSampleCooldown;

    public Flak_Cannon_Entity(EntityType<? extends Flak_Cannon_Entity> type, Level level) {
        super(type, level);
        this.xpReward = 20;
    }

    /**
     * 固定炮台永不消散：Monster 原版规则会在玩家远离（>32 格概率性、>128 格直接）
     * 时 despawn——防空索敌 100 格的固定单位绝不能跑远了就消失。
     */
    @Override
    public void checkDespawn() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 80.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_TURRET, new Vector3f(0.0F, 0.0F, 80.0F));
    }

    /** 给渲染用：炮塔状态 (yaw°, pitch°, hp) */
    public Vector3f getTurretState() {
        return this.entityData.get(DATA_TURRET);
    }

    /** 炮塔中心（= 碰撞箱中心；本实体无部件，单箱中心即此处） */
    private Vec3 center() {
        return this.position().add(0.0D, this.getBbHeight() * 0.5D, 0.0D);
    }

    // ===== 固定炮台：不移动、推不动；受重力（悬空会落到地面，落地后静止）=====

    /** 静止：清空水平移动，保留竖直分量（受重力下落，落地后自然站住）。 */
    @Override
    public void travel(Vec3 input) {
        if (this.isEffectiveAi()) {
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(0.0D, v.y, 0.0D);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    // ===== 索敌 + 开火（全部在服务端 tick 内）=====

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide()) {
            return;
        }
        Vec3 c = center();
        // 每秒重评估索敌：只锁定「能命中」的目标（射程 + 视线），打不到就换/放弃
        if (--this.targetCooldown <= 0) {
            this.targetCooldown = SCAN_INTERVAL;
            this.target = pickTarget(this.target);
        }
        LivingEntity target = this.target;
        // 距离判定：水平 100 格 + 垂直上限 10000（高出炮台的玩家都能锁）
        boolean valid = target != null && target.isAlive()
                && horizDistSqr(target, c) <= RANGE * RANGE
                && target.getY() - this.getY() <= VERT_RANGE;
        double horiz = 0.0D, vert = 0.0D;
        boolean sight = false;
        if (valid) {
            // 提前量预测瞄准点（炮弹飞行时间内目标走到的位置）——转向也朝预测点
            Vec3 aim = predictLead(c, target);
            Vec3 dir = aim.subtract(c).normalize();
            horiz = horizAngle(dir);
            vert = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));
            // 远距不做视线遮挡检查（超长射线卡顿保护）
            sight = target.distanceToSqr(c) > SIGHT_DIST * SIGHT_DIST
                    || this.getSensing().hasLineOfSight(target);
            sampleTargetVelocity(target);
        }
        // 转向：有目标盯住（超出射界则贴边），无目标绕基准缓慢扫描警戒
        float wantYaw, wantPitch;
        if (valid) {
            wantYaw = (float) Mth.clamp(horiz, -180.0F, 180.0F);
            wantPitch = (float) Mth.clamp(vert, -ARC_DOWN, ARC_UP);
        } else {
            wantYaw = Mth.sin(this.tickCount * 0.015F) * IDLE_SWEEP;
            wantPitch = -5.0F;
        }
        this.yaw = approach(this.yaw, wantYaw, TURN);
        this.pitch = approach(this.pitch, wantPitch, TURN);
        if (Math.abs(this.yaw - this.syncedYaw) >= 1.0F || Math.abs(this.pitch - this.syncedPitch) >= 1.0F) {
            sync();
        }
        // 必须转到位才开火（炮口方向与炮管视觉一致）；flak 是直线弹，不放低打不到的射界
        boolean aimed = Math.abs(Mth.wrapDegrees((float) horiz - this.yaw)) <= AIM_YAW
                && Math.abs((float) vert - this.pitch) <= AIM_PITCH;
        boolean canFire = valid && sight && aimed
                && Math.abs(horiz) <= 180.0F && vert <= ARC_UP && vert >= -ARC_DOWN;
        if (!canFire) {
            this.cooldown = Math.min(this.cooldown, 10);
            return;
        }
        if (--this.cooldown <= 0) {
            fire(c);
            this.cooldown = COOLDOWN;
        }
    }

    /** 目标方向相对炮台基准朝向（实体 yRot 方向）的水平夹角（带符号，度） */
    private double horizAngle(Vec3 dir) {
        Vec3 base = baseDir();
        double a = Mth.atan2(dir.z, dir.x);
        double b = Mth.atan2(base.z, base.x);
        return Mth.wrapDegrees(Math.toDegrees(a - b));
    }

    /** 炮台基准方向（世界，水平）= 实体 yRot 方向（固定炮台朝向不变） */
    private Vec3 baseDir() {
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
    }

    /** 保持仍可命中的当前目标；否则在射程内挑最近的、能命中的目标；没有则 null */
    private LivingEntity pickTarget(LivingEntity current) {
        if (current != null && current.isAlive() && canHit(current)) {
            return current;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 c = center();
        AABB box = getBoundingBox().inflate(RANGE, VERT_RANGE, RANGE);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!canTarget(entity) || !canHit(entity)) {
                continue;
            }
            double dist = entity.distanceToSqr(c);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    /** 目标当前是否可被本炮台命中（水平射程 + 垂直上限 + 视线），供索敌筛选 */
    private boolean canHit(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        Vec3 c = center();
        if (horizDistSqr(target, c) > RANGE * RANGE) {
            return false;
        }
        if (target.getY() - this.getY() > VERT_RANGE) {
            return false;
        }
        // 远距离（尤其高空）不做视线遮挡检查：超长射线会拖垮每 tick 性能，
        // 且高空目标本就处于开放空域——防空炮视距离外目标为"可见"。
        return target.distanceToSqr(c) > SIGHT_DIST * SIGHT_DIST
                || this.getSensing().hasLineOfSight(target);
    }

    /** 目标到炮台中心的水平距离平方（防空：垂直高度不参与距离判定） */
    private double horizDistSqr(LivingEntity target, Vec3 c) {
        double dx = target.getX() - c.x, dz = target.getZ() - c.z;
        return dx * dx + dz * dz;
    }

    /** 索敌过滤：只打"比实体高的玩家"（防空特化）——玩家脚底须高于炮台中心线；不打怪物/动物/村民 */
    private boolean canTarget(LivingEntity entity) {
        if (entity == this || entity.isDeadOrDying() || entity.isSpectator()) {
            return false;
        }
        return entity instanceof Player player
                && player.getY() > this.getY() + this.getBbHeight() * 0.5D + MIN_UP;
    }

    /**
     * 提前量预测：迭代求解"炮弹飞行时间 = 目标走到预测点的时间"。
     * flak 是直线匀速弹道（初速 {@link #FLAK_SPEED}，无重力），飞行时间 = 距离/初速，
     * 迭代 6 次逼近目标移动轨迹上的命中点（目标速度用滑动平均估计）。
     */
    private Vec3 predictLead(Vec3 c, LivingEntity target) {
        Vec3 t = target.getEyePosition();
        Vec3 vel = this.targetVel != null ? this.targetVel : Vec3.ZERO;
        Vec3 aim = t;
        for (int i = 0; i < 6; i++) {
            double flight = aim.subtract(c).length() / FLAK_SPEED;
            aim = t.add(vel.scale(flight));
        }
        return aim;
    }

    /** 采样目标速度（格/tick，滑动平均）：每 4 tick 记录一次位置差；换目标则重置 */
    private void sampleTargetVelocity(LivingEntity target) {
        if (this.target != this.lastTargetEntity) {
            this.lastTargetEntity = this.target;
            this.lastTargetPos = this.target.position();
            this.targetVel = null;
            this.targetSampleCooldown = 4;
            return;
        }
        if (--this.targetSampleCooldown > 0) {
            return;
        }
        this.targetSampleCooldown = 4;
        if (this.lastTargetPos != null) {
            Vec3 vel = this.target.position().subtract(this.lastTargetPos).scale(0.25D);
            this.targetVel = this.targetVel == null ? vel : this.targetVel.scale(0.5D).add(vel.scale(0.5D));
        }
        this.lastTargetPos = this.target.position();
    }

    /** 发射一颗高射炮弹（提前量预测方向 + 小散布 + 较快初速） */
    private void fire(Vec3 c) {
        Vec3 aim = predictLead(c, this.target);
        Vec3 dir = aim.subtract(c).normalize();
        if (SPREAD_DEG > 0.0F) {
            double s = SPREAD_DEG * Mth.DEG_TO_RAD;
            dir = dir.add(
                    (this.getRandom().nextDouble() - 0.5D) * s * 2.0D,
                    (this.getRandom().nextDouble() - 0.5D) * s * 2.0D,
                    (this.getRandom().nextDouble() - 0.5D) * s * 2.0D
            ).normalize();
        }
        Vec3 muzzle = c.add(dir.scale(MUZZLE)).add(0.0D, FIRE_DY, 0.0D);
        Flak_Shell_Entity shell = new Flak_Shell_Entity(this.level(), this, dir);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        shell.setDamage(FLAK_POWER);
        // 定时引信：飞完"预测命中点距离 ± 随机抖动"后空中自动引爆（近炸，打飞行目标更致命）
        shell.setFuseDist((float) aim.distanceTo(c) - 0.7F * FUSE_JITTER + this.getRandom().nextFloat() * FUSE_JITTER);
        // 覆盖 flak_shell 默认 1.5 初速为 FLAK_SPEED（防空需要更快）
        shell.setDeltaMovement(shell.getDeltaMovement().scale(FLAK_SPEED / 1.5D));
        this.level().addFreshEntity(shell);
        this.playSound(SoundEvents.FIRECHARGE_USE, 1.5F, 0.7F + this.getRandom().nextFloat() * 0.3F);
    }

    private void sync() {
        this.entityData.set(DATA_TURRET, new Vector3f(this.yaw, this.pitch, this.getHealth()));
        this.syncedYaw = this.yaw;
        this.syncedPitch = this.pitch;
    }

    private static float approach(float current, float want, float maxStep) {
        return current + Mth.clamp(Mth.wrapDegrees(want - current), -maxStep, maxStep);
    }

    /** 自家炮弹的命中/爆炸不伤自身（flak 撞部件也会爆炸，需在此滤掉） */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Flak_Shell_Entity shell && shell.getOwner() == this) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** 被打爆时冒烟 + 爆炸粒子（固定炮台没有死亡动画，用粒子点缀） */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel server) {
            Vec3 c = center();
            server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 10, 0.8D, 0.8D, 0.8D, 0.0D);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.5D, c.z, 12, 0.6D, 0.4D, 0.6D, 0.03D);
        }
        super.die(source);
    }

    // ===== 音效（借铁傀儡金属质感，占位）=====
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
}
