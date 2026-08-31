package com.testmod.entity;

import com.testmod.Test_Mod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 自定义投射物「高射炮弹」（testmod:flak_shell）。
 * 由 Flak_Shell_Item 右键投掷发射（参考雪球），直线飞行（不加重力下坠），
 * 撞击方块或实体即爆炸（爆炸粒子 + 破坏方块），伤害随距离线性衰减。
 * 爆炸逻辑与 ShellEntity 一致（自写伤害计算器，范围/伤害解耦）。
 */
public class Flak_Shell_Entity extends AbstractHurtingProjectile {

    /** 隔 tick 命中检测（减少实体遍历消耗）：只在偶数 tick 跑一次实体命中检测。 */
    private static final int DETECT_INTERVAL = 1;

    private boolean isDetectTick() {
        return (this.tickCount & DETECT_INTERVAL) == 0;
    }

    /** 默认爆炸半径（格）。伤害见 {@link #damage}，独立于半径。 */
    private float explosionPower = 3F;
    /** 爆炸中心伤害（随距离线性衰减到半径处为 0）。 */
    private float damage = 40F;
    /** 定时引信飞行距离（格）：累计飞行超过该值即空中自动引爆；<0 = 未启用（仅撞击爆炸）。 */
    private float fuseDist = -1.0F;
    /** 已累计飞行距离（格，服务端 tick 维护） */
    private double travelled;
    private Vec3 lastPos;

    public Flak_Shell_Entity(EntityType<? extends Flak_Shell_Entity> type, Level level) {
        super(type, level);
    }

    /** 玩家投掷入口（参考雪球）：沿初速方向发射，初速由调用方 shootFromRotation 决定 */
    public Flak_Shell_Entity(Level level, LivingEntity shooter, Vec3 dir) {
        super(Test_Mod.FLAK_SHELL.get(), shooter, dir, level);
        this.setDeltaMovement(dir.normalize().scale(1.5D));
    }

    public float getExplosionPower() {
        return this.explosionPower;
    }

    public void setExplosionPower(float power) {
        this.explosionPower = power;
    }

    public float getDamage() {
        return this.damage;
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    /** 启用定时引信：飞行累计距离超过 dist 格自动空中引爆（模拟防空定时引信/近炸）。 */
    public void setFuseDist(float dist) {
        this.fuseDist = dist;
    }

    // ===== 定时引信：飞行距离累计，到时空中自动爆炸 =====
    @Override
    public void tick() {
        if (!this.level().isClientSide && this.fuseDist >= 0.0F) {
            if (this.lastPos != null) {
                this.travelled += this.lastPos.distanceTo(this.position());
            }
            this.lastPos = this.position();
        }
        super.tick();
        // 引信到时且还没被撞击引爆（discard 后 isRemoved=true 会跳过）：当前位置空中爆炸
        if (!this.level().isClientSide && this.fuseDist >= 0.0F && !this.isRemoved() && this.travelled >= this.fuseDist) {
            this.explodeNow();
            this.discard();
        }
    }

    // ===== 撞击处理：撞方块或实体都爆炸 =====
    // 命中实体时不额外做直伤：爆炸中心就在命中点，爆炸本身会炸到该实体及周围。

    /** 命中过滤：隔 tick 非检测 tick 全拒（省实体遍历）；不撞发射者自己及其多部件。 */
    @Override
    protected boolean canHitEntity(Entity target) {
        if (!isDetectTick()) {
            return false;
        }
        if (target == this.getOwner()) {
            return false;
        }
        if (target instanceof PartEntity<?> part && part.getParent() == this.getOwner()) {
            return false;
        }
        return super.canHitEntity(target);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!this.level().isClientSide) {
            explodeNow();
            this.discard();
        }
    }

    /** 爆炸（撞击或近炸共用）：破坏方块 + 爆炸粒子 + 黑色烟雾，不自身 discard */
    private void explodeNow() {
        // 爆炸伤害用自定义计算器：范围（半径）与伤害（中心值）解耦——
        // 伤害 = damage × (1 - 距离/半径)，半径处为 0，中心全额。
        // 用 12 参重载：大小爆炸粒子都设为 EXPLOSION（普通小爆炸），fire=false 不产生火焰；
        // 破坏方块仍由 MOB 交互的 mobGriefing 规则控制。
        this.level().explode(
                this,
                Explosion.getDefaultDamageSource(this.level(), this),
                new FlakExplosionDamage(this.damage, this.explosionPower),
                this.getX(), this.getY(), this.getZ(),
                this.explosionPower,
                false,
                Level.ExplosionInteraction.MOB,
                ParticleTypes.EXPLOSION,
                ParticleTypes.EXPLOSION,
                SoundEvents.GENERIC_EXPLODE
        );
        // 爆炸中心放大：count=0 + 偏移当尺寸，发一个 1.5 倍大的爆炸闪光（maxSpeed 必须 1.0）
        ((ServerLevel) this.level()).sendParticles(
                ParticleTypes.EXPLOSION,
                this.getX(), this.getY(), this.getZ(),
                0, 0.5D, 0.0D, 0.0D, 1.0D
        );
        // 爆炸残留黑色烟雾约 2s：黑烟缓扩散、渐透明（数量克制）
        ((ServerLevel) this.level()).sendParticles(
                Test_Mod.FLAK_SMOKE_PARTICLE.get(),
                this.getX(), this.getY(), this.getZ(),
                12, 0.5D, 1D, 1D, 1D
        );
    }

    /** 金属炮弹不自身燃烧（恶魂火球是 shouldBurn=true，我们关掉） */
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    /** 关闭尾迹粒子：基类每 tick 生成拖尾，这里返回 null（基类已判空），不要痕迹 */
    @Override
    protected ParticleOptions getTrailParticle() {
        return null;
    }

    // ===== NBT 保存爆炸威力 =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", this.explosionPower);
        tag.putFloat("Damage", this.damage);
        tag.putFloat("FuseDist", this.fuseDist);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("ExplosionPower", 99)) {
            this.explosionPower = tag.getFloat("ExplosionPower");
        }
        if (tag.contains("Damage", 99)) {
            this.damage = tag.getFloat("Damage");
        }
        if (tag.contains("FuseDist", 99)) {
            this.fuseDist = tag.getFloat("FuseDist");
        }
    }

    /** 自定义爆炸伤害：伤害独立于半径（中心全额、距离线性衰减）。 */
    private static class FlakExplosionDamage extends ExplosionDamageCalculator {
        private final float damage;
        private final float radius;

        FlakExplosionDamage(float damage, float radius) {
            this.damage = damage;
            this.radius = radius;
        }

        @Override
        public float getEntityDamageAmount(Explosion explosion, Entity target) {
            double r = this.radius;
            double distSqr = target.distanceToSqr(explosion.center());
            if (distSqr >= r * r) {
                return 0.0F;
            }
            double dist = Math.sqrt(distSqr);
            float falloff = 1.0F - (float) (dist / r);
            return this.damage * Math.max(0.0F, falloff);
        }
    }
}
