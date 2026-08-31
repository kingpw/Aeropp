package com.testmod.entity;
import com.testmod.Test_Mod;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;

/**
 * 自定义投射物「炮弹」（testmod:shell）。
 *
 * 行为模仿恶魂火球：
 * - 飞行：继承 AbstractHurtingProjectile（每 tick 加速 + 0.95 惯性 + 拖尾）。
 * - 撞击方块或实体时爆炸。
 * - 命中实体时不额外直伤：爆炸中心就在命中点，爆炸会炸到该实体及周围。
 *
 * 2026-08 优化：爆炸半径与伤害拆成两个独立参数（都是可调值），
 * 不再让"范围"同时决定"单发伤害"。默认 4 参构造仍可用（伤害默认 = 半径），不破坏调用方式。
 */
public class ShellEntity extends AbstractHurtingProjectile {

    /** 爆炸半径（格）。伤害值见 {@link #damage}。 */
    private float explosionPower = 2F;
    /** 命中中心爆炸伤害（独立于半径，随距离线性衰减到半径处为 0）。 */
    private float damage = 100F;

    /** 每 tick 施加的向下重力（格/tick²）。越大下坠越快，参考原版箭矢约 0.05。 */
    private static final float GRAVITY = 0.05F;

    /**
     * 隔 tick 命中检测（减少检测量）：只在偶数 tick 跑一次命中检测。
     * ⚠ 1.21.1 编译期没有 ClipContext.Block.NONE（只在运行时补丁），方块射线无法干净跳过，
     * 所以：实体检测隔 tick（非检测 tick 全拒），方块检测保持原版每 tick（COLLIDER）。
     * 好处：墙体每 tick 都测（不穿墙），只有实体检测减半。实体隔 tick 的检测段只覆盖 1 tick 位移，
     * 隔 tick 后会留约 1 tick（≈2.5 格）的实体检测空档，高速炮弹可能穿过薄怪（主人已确认接受）。
     */
    private static final int DETECT_INTERVAL = 1; // 1 = 隔 1 tick（每 2 tick 检测一次实体）

    private boolean isDetectTick() {
        return (this.tickCount & DETECT_INTERVAL) == 0;
    }

    /** 重写 tick：在基类推进前给速度叠加一个向下重力分量，让炮弹沿弹道下坠。 */
    @Override
    public void tick() {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -GRAVITY, 0.0D));
        super.tick();
    }

    public ShellEntity(EntityType<? extends ShellEntity> type, Level level) {
        super(type, level);
    }

    /** 从射手向 dir 方向发射炮弹（工厂）。dir 会被归一化，并按默认初速射出。 */
    public ShellEntity(Level level, LivingEntity shooter, Vec3 dir, float explosionPower) {
        this(level, shooter, dir, explosionPower, Math.max(1.0F, explosionPower));
    }

    /** 爆炸半径与伤害独立版本：range＝爆炸半径，damage＝命中中心伤害。 */
    public ShellEntity(Level level, LivingEntity shooter, Vec3 dir, float explosionRadius, float damage) {
        super(Test_Mod.SHELL.get(), shooter, dir, level);
        this.explosionPower = explosionRadius;
        this.damage = damage;
        // 基类只给 0.1 起步加速，这里给足初速，避免飞起来软绵绵
        this.setDeltaMovement(dir.normalize().scale(2.5D));
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
        super.onHit(result); // 先触发 onHitEntity / onHitBlock（默认无副作用）
        if (!this.level().isClientSide) {
            boolean fire = EventHooks.canEntityGrief(this.level(), this.getOwner());
            // 爆炸伤害用自定义计算器：范围（半径）与伤害（中心值）解耦——
            // 伤害 = damage × (1 - 距离/半径)，半径处为 0，中心全额。
            this.level().explode(
                    this,
                    Explosion.getDefaultDamageSource(this.level(), this),
                    new ShellExplosionDamage(this.damage, this.explosionPower),
                    this.getX(), this.getY(), this.getZ(),
                    this.explosionPower,
                    fire,
                    Level.ExplosionInteraction.MOB
            );
            this.discard();
        }
    }

    /** 金属炮弹不自身燃烧（恶魂火球是 shouldBurn=true，我们关掉） */
    @Override
    protected boolean shouldBurn() {
        return false;
    }

    /**
     * 火光拖尾：把基类默认的烟雾拖尾换成火焰粒子。
     * 基类每 tick 只生成 1 颗拖尾粒子（20/s），成本与原本烟尾相同，不增性能开销。
     */
    @Override
    protected ParticleOptions getTrailParticle() {
        return ParticleTypes.FLAME;
    }

    // ===== NBT 保存爆炸威力 =====
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("ExplosionPower", this.explosionPower);
        tag.putFloat("Damage", this.damage);
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
    }

    /** 自定义爆炸伤害：伤害独立于半径（中心全额、距离线性衰减）。 */
    private static class ShellExplosionDamage extends ExplosionDamageCalculator {
        private final float damage;
        private final float radius;

        ShellExplosionDamage(float damage, float radius) {
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
