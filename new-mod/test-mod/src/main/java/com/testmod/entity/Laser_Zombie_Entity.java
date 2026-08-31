package com.testmod.entity;

import com.testmod.Test_Mod;
import com.testmod.item.Laser_Beam;
import com.testmod.network.Laser_Zombie_Fired_Payload;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.SkeletonHorse;
import net.minecraft.world.entity.animal.horse.ZombieHorse;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 尸界军（testmod:laser_zombie）：<b>原版僵尸 + 手持模组激光枪</b>。
 *
 * <p>继承 {@link Zombie} 保留全部原版能力（AI、近战、白天燃烧、属性、动画），额外增加：
 * 出生自带装备（激光枪 + 海龟壳 + 深绿色皮革胸甲，永不掉落），并像战舰一样<b>一轮三连发</b>
 * 激光——服务端 {@link Laser_Beam#computeBeam}（方块截断）+ {@link Laser_Beam#fire}（伤害+命中特效），
 * 光束视觉经 {@link Laser_Zombie_Fired_Payload} 发给客户端 {@code Laser_Beam.spawnBeam} 画。
 *
 * <p>索敌：攻击<b>所有非亡灵生物</b>（亡灵阵营——玩家/村民/动物/非亡灵怪物全打，同族亡灵不打）；
 * <b>每秒重选最近的合法目标</b>（不死盯一个）；<b>激光路径上有亡灵友军挡路时自动停火</b>；
 * 射程 40 格 + 视线；每轮 3 发（0.3 秒间隔）+ 轮间冷却 1 秒；带随机散布（0.03）命中更飘；伤害 16/发。
 * 性能：目标扫描每秒一次；友军检查只在开火瞬间做，其余 tick 零开销。
 */
public class Laser_Zombie_Entity extends Zombie {

    // ===== 可调参数 =====
    /** 激光射程（格） */
    private static final double RANGE = 36.0D;
    /** 索敌重评估间隔（tick，1 秒） */
    private static final int SCAN_INTERVAL = 20;
    /** 一轮发射次数（三连发） */
    private static final int BURST_COUNT = 3;
    /** 连发间隔（tick，0.3 秒/发） */
    private static final int BURST_INTERVAL = 4;
    /** 一轮结束后的冷却（tick，1 秒） */
    private static final int COOLDOWN = 40;
    /** 激光伤害 */
    private static final float LASER_DAMAGE = 12.0F;
    /** 弹道散布幅度（0.03 ≈ 80 格处最大偏差约 2.4 格） */
    private static final double SPREAD_AMOUNT = 0.03;
    /** 枪口前移（格，相对持枪手位置，略出枪口） */
    private static final double MUZZLE = 0.6D;
    /** 持枪手位置估算：手臂前伸（格） */
    private static final double HAND_FWD = 0.25D;
    /** 持枪手位置估算：右手侧偏移（格；符号反了改这里） */
    private static final double HAND_SIDE = 0.3D;
    /** 持枪手位置估算：眼位到手部竖直差（格） */
    private static final double HAND_DOWN = 0.4D;
    /** 皮革胸甲染色：深绿色 */
    private static final int ARMOR_COLOR = 0x1E4A1E;

    private int targetCooldown;
    /** 本轮剩余发射次数 */
    private int burstRemaining;
    /** 连发间隔倒计时 */
    private int burstDelay;
    /** 一轮结束后的冷却倒计时 */
    private int cooldown;

    public Laser_Zombie_Entity(EntityType<? extends Laser_Zombie_Entity> type, Level level) {
        super(type, level);
    }

    /** 属性：完全沿用原版僵尸（近战/速度都在） */
    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D);
    }

    /** 出生/复活时补装备：主手激光枪 + 海龟壳头盔 + 深绿色皮革胸甲（全套永不掉落） */
    private void ensureGear() {
        if (!this.getItemInHand(InteractionHand.MAIN_HAND).is(Test_Mod.LASER_GUN.get())) {
            this.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Test_Mod.LASER_GUN.get()));
            this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        }
        if (!this.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)) {
            this.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
            this.setDropChance(EquipmentSlot.HEAD, 0.0F);
        }
        if (!this.getItemBySlot(EquipmentSlot.CHEST).is(Items.LEATHER_CHESTPLATE)) {
            ItemStack chest = new ItemStack(Items.LEATHER_CHESTPLATE);
            chest.set(DataComponents.DYED_COLOR, new DyedItemColor(ARMOR_COLOR, false));   // 深绿色染色（1.21 组件 API）
            this.setItemSlot(EquipmentSlot.CHEST, chest);
            this.setDropChance(EquipmentSlot.CHEST, 0.0F);
        }
    }

    @Override
    public void tick() {
        super.tick();
        this.ensureGear();
        if (this.level().isClientSide()) {
            return;
        }
        this.fireControl();
    }

    /** 服务端：每秒重选最近的合法目标（不死盯）→ 面向目标 → 一轮三连发（友军挡路即停火） */
    private void fireControl() {
        // 每秒重选"最近"目标（不保持旧目标；扫描只在每秒一次，其余 tick 零开销）
        if (--this.targetCooldown <= 0) {
            this.targetCooldown = SCAN_INTERVAL;
            LivingEntity target = pickTarget();
            if (target != this.getTarget()) {
                this.setTarget(target);
                this.burstRemaining = 0;   // 换目标：放弃当前轮（下轮重新排）
                this.burstDelay = 0;
            }
        }
        LivingEntity target = this.getTarget();
        if (target == null) {
            this.burstRemaining = 0;
            return;
        }
        // 面向目标（僵尸身体/头部会转向；近战 goal 不受影响）
        this.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // 分阶段冷却：间隔 0.3 秒/发，轮间冷却 1 秒
        if (this.cooldown > 0) {
            this.cooldown--;
            return;
        }
        if (this.burstRemaining <= 0) {
            this.burstRemaining = BURST_COUNT;
            this.burstDelay = 0;
        }
        if (--this.burstDelay <= 0) {
            Vec3 from = gunPosition();
            // 友军挡路（激光路径上有亡灵友军更近）→ 停火：不开火也不推进连发，等友军让开/目标更换
            if (friendlyInLine(from, target)) {
                return;
            }
            this.fire(target, from);
            this.burstRemaining--;
            this.burstDelay = BURST_INTERVAL;
            if (this.burstRemaining <= 0) {
                this.cooldown = COOLDOWN;
            }
        }
    }

    /**
     * 友军停火检查（仅在开火瞬间执行，性能开销极小）：
     * 枪口 → 目标方向射线上，若存在比目标更近的亡灵友军（垂距 < 0.8 格可视为挡路）→ 停火。
     */
    private boolean friendlyInLine(Vec3 from, LivingEntity target) {
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        double targetDist = from.distanceTo(target.getEyePosition());
        AABB box = new AABB(from, from.add(dir.scale(targetDist))).inflate(1.0D);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (e == this || e == target || !e.isPickable() || e.isSpectator() || !isUndead(e)) {
                continue;
            }
            // 友军到射线垂距 < 0.8 且在目标之前 → 挡路
            Vec3 v = e.getEyePosition().subtract(from);
            double t = v.dot(dir);
            if (t > 0.0D && t < targetDist && v.subtract(dir.scale(t)).length() < 0.8D) {
                return true;
            }
        }
        return false;
    }

    /** 发射一束激光（带随机散布）：服务端伤害 + 发包给客户端画光束 */
    private void fire(LivingEntity target, Vec3 from) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 dir = target.getEyePosition().subtract(from).normalize();
        dir = Laser_Beam.spread(dir, this.getRandom(), SPREAD_AMOUNT);
        Vec3 muzzle = from.add(dir.scale(MUZZLE));
        Laser_Beam.Beam beam = Laser_Beam.computeBeam(this.level(), this, muzzle, dir);
        Laser_Beam.fire(server, this, beam, LASER_DAMAGE);
        // 光束视觉：发广播（start+dir+len），客户端本地画粒子
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(this, new Laser_Zombie_Fired_Payload(new float[]{
                (float) beam.start().x, (float) beam.start().y, (float) beam.start().z,
                (float) beam.dir().x, (float) beam.dir().y, (float) beam.dir().z,
                (float) beam.len()}));
    }

    /**
     * 持枪手位置估算（服务端拿不到模型骨骼，只能用偏移近似）：
     * 眼位 + 前伸 0.25 + 右手侧 0.3 + 下移 0.4（格）。
     * 侧向基准用实体 yRot 的右舷方向（与舰载炮塔 placePart 同公式）。
     */
    private Vec3 gunPosition() {
        Vec3 eye = this.getEyePosition();
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        Vec3 side = new Vec3(-Mth.cos(rad), 0.0D, -Mth.sin(rad));
        return eye.add(fwd.scale(HAND_FWD)).add(side.scale(HAND_SIDE)).add(0.0D, -HAND_DOWN, 0.0D);
    }

    /** 手动扫描最近的玩家（含创造模式——原版目标 goal 会滤掉创造玩家；排除观察者/自己） */
    private LivingEntity pickTarget() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = this.getBoundingBox().inflate(RANGE);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (canTarget(entity) && canHit(entity)) {
                double dist = this.distanceToSqr(entity);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = entity;
                }
            }
        }
        return best;
    }

    /** 索敌过滤：攻击<b>所有非亡灵生物</b>（亡灵阵营）——玩家/村民/动物/非亡灵怪物全打；
     *  亡灵家族（僵尸/骷髅/凋灵/亡灵马等）不打。观察者/死亡/自己除外。 */
    private boolean canTarget(LivingEntity entity) {
        if (entity == this || entity.isDeadOrDying() || entity.isSpectator()) {
            return false;
        }
        return !isUndead(entity);
    }

    /** 原版亡灵家族判断（1.21.1 已移除 MobType，用类型覆盖；僵尸/骷髅/亡灵马族） */
    private static boolean isUndead(LivingEntity entity) {
        return entity instanceof Zombie || entity instanceof AbstractSkeleton
                || entity instanceof ZombieHorse || entity instanceof SkeletonHorse;
    }

    /** 目标是否可命中（射程 + 视线） */
    private boolean canHit(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return false;
        }
        if (this.distanceToSqr(target) > RANGE * RANGE) {
            return false;
        }
        return this.getSensing().hasLineOfSight(target);
    }
}
