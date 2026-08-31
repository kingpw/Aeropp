package com.testmod.entity;

import java.util.function.Predicate;

import org.joml.Vector3f;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 可复用舰载炮塔组件：独立旋转（yaw/pitch 限速平滑）、独立血量、独立冷却、按参数设定射界，
 * 自行持有并摆放自己的判定箱（{@link PartEntity}，只吃攻击不吃物理碰撞），发射 testmod:shell。
 *
 * <p>用法（链式配置，挂载位置为<b>相对父类实体</b>的前向/侧向/高度偏移 + 基准朝向）：
 * <pre>{@code
 * Ship_Turret.of(this, 0, DATA_TURRET[0], this::canTarget)
 *         .mount(3.0D, 1.5D, 0.5D, 45.0F)   // fwd, sideDist, dy, baseAngDeg
 *         .build();                         // 其余参数都有默认值
 * }</pre>
 * 宿主实体持有 {@code Ship_Turret<T>[]}，每 tick 依次调用 {@link #placePart()} 与
 * {@link #tick()}（目标由炮塔自行索敌）。状态经宿主实体的
 * {@link EntityDataAccessor}&lt;Vector3f&gt;（yaw°, pitch°, hp）同步到客户端。
 *
 * <p>渲染调用 {@code Ship_Turret_Model.renderAt(...)}（client.model 包），
 * 传同样的相对位置即可让炮塔和判定箱对齐；新增炮塔 = 数组 +1 并加几行配置，其余零改动。
 */
public class Ship_Turret<T extends Mob> {

    /** 判定箱尺寸（2×2×2 格，略大于 1 格炮塔本体） */
    private static final float PART_SIZE = 2.0F;
    /** 炮塔转向速度（度/tick） */
    private static final float TURN = 5.0F;
    /** 开火判定：对准误差（度） */
    private static final float AIM_YAW = 10.0F, AIM_PITCH = 12.0F;
    /** 无目标扫描警戒幅度 */
    private static final float IDLE_SWEEP = 25.0F;
    /** 炮口外移（格，出判定箱、贴炮口环；炮管 0.6× 缩短后同步收小） */
    private static final double MUZZLE = 1.5D;
    /** 出膛点高度偏移（格，负=降低）：只影响炮弹生成位置，不影响判定箱/模型/瞄准 */
    private static final double FIRE_DY = -0.5D;

    /** 索敌重评估间隔（tick，1 秒） */
    private static final int TARGET_SCAN_INTERVAL = 20;

    private final T parent;
    private final EntityDataAccessor<Vector3f> data;
    private final Predicate<LivingEntity> targetFilter;
    private final SkyPart part;
    private final double fwdOffset, sideDist, dy;
    private final int index;
    /** 基准朝向（度，相对舰首方向）：0=正前、+90=右舷、−90=左舷；与渲染端 BASE_YAW 数值一致 */
    private final float baseAngDeg;
    private final double arcH, arcUp, arcDown, range;
    private final int cooldownTicks;
    private final float shellPower, maxHp;

    private float yaw, pitch, hp;
    private float syncedYaw, syncedPitch;
    private int cooldown;
    /** 炮塔自己的目标（独立索敌，每秒重评估） */
    private LivingEntity target;
    private int targetCooldown;

    private Ship_Turret(T parent, int index, EntityDataAccessor<Vector3f> data,
                       Predicate<LivingEntity> targetFilter,
                       double fwdOffset, double sideDist, double dy, float baseAngDeg,
                       double arcH, double arcUp, double arcDown,
                       double range, int cooldownTicks, float shellPower, float maxHp) {
        this.parent = parent;
        this.index = index;
        this.data = data;
        this.targetFilter = targetFilter;
        this.fwdOffset = fwdOffset;
        this.sideDist = sideDist;
        this.dy = dy;
        this.baseAngDeg = baseAngDeg;
        this.arcH = arcH;
        this.arcUp = arcUp;
        this.arcDown = arcDown;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.shellPower = shellPower;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.cooldown = 20 + index * 7;   // 初始错峰，避免齐射
        this.part = new SkyPart();
    }

    /** 简化入口：只传父实体、序号、同步数据、索敌过滤，其余参数链式配置 */
    public static <T extends Mob> Builder<T> of(T parent, int index, EntityDataAccessor<Vector3f> data,
                                                Predicate<LivingEntity> targetFilter) {
        return new Builder<>(parent, index, data, targetFilter);
    }

    /** 炮塔配置器：挂载位置为相对父类实体的偏移，便于在不同实体上复用 */
    public static class Builder<T extends Mob> {

        private final T parent;
        private final int index;
        private final EntityDataAccessor<Vector3f> data;
        private final Predicate<LivingEntity> targetFilter;

        // 参数默认值即旧版亡灵天城常用配置，最简用法 = of(...).mount(...).build()
        private double fwdOffset, sideDist, dy;
        private float baseAngDeg = 90.0F;
        private double arcH = 60.0D, arcUp = 60.0D, arcDown = 70.0D, range = 48.0D;
        private int cooldownTicks = 100;
        private float shellPower = 8.0F, maxHp = 50.0F;

        Builder(T parent, int index, EntityDataAccessor<Vector3f> data, Predicate<LivingEntity> targetFilter) {
            this.parent = parent;
            this.index = index;
            this.data = data;
            this.targetFilter = targetFilter;
        }

        /** 挂载位置（相对父类实体，格）：fwdOffset=前向偏移（+为舰首方向）、sideDist=离中轴线的
         *  有向距离（数轴式，+右舷/−左舷）、dy=垂直偏移（+为上）。
         *  baseAngDeg=基准朝向（度，相对舰首方向）：0=正前、+90=正右舷、−90=正左舷、±45=舷侧斜向；
         *  与渲染端 BASE_YAW 数值一致，独立于 sideDist（位置与朝向可各自设定）。 */
        public Builder<T> mount(double fwdOffset, double sideDist, double dy, float baseAngDeg) {
            this.fwdOffset = fwdOffset;
            this.sideDist = sideDist;
            this.dy = dy;
            this.baseAngDeg = baseAngDeg;
            return this;
        }

        /** 射界（度）：水平 ±horiz / 仰角 up / 俯角 down，默认 ±60 / 60 / 70 */
        public Builder<T> arc(double horiz, double up, double down) {
            this.arcH = horiz;
            this.arcUp = up;
            this.arcDown = down;
            return this;
        }

        /** 射程（格），默认 48 */
        public Builder<T> range(double range) {
            this.range = range;
            return this;
        }

        /** 冷却（tick），默认 100 */
        public Builder<T> cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        /** 炮弹威力，默认 8.0 */
        public Builder<T> power(float shellPower) {
            this.shellPower = shellPower;
            return this;
        }

        /** 炮塔血量，默认 50 */
        public Builder<T> hp(float maxHp) {
            this.maxHp = maxHp;
            return this;
        }

        public Ship_Turret<T> build() {
            return new Ship_Turret<>(this.parent, this.index, this.data, this.targetFilter,
                    this.fwdOffset, this.sideDist, this.dy, this.baseAngDeg,
                    this.arcH, this.arcUp, this.arcDown,
                    this.range, this.cooldownTicks, this.shellPower, this.maxHp);
        }
    }

    public PartEntity<T> part() {
        return this.part;
    }

    /** 挂载参数（fwd, sideDist, dy, baseAngDeg）——供宿主把判定箱位置换算成渲染挂点 */
    public double[] mountParams() {
        return new double[] {this.fwdOffset, this.sideDist, this.dy, this.baseAngDeg};
    }

    public boolean partMatches(PartEntity<?> part) {
        return this.part == part;
    }

    public boolean isDead() {
        return this.hp <= 0.0F;
    }

    public float getHp() {
        return this.hp;
    }

    public void setHp(float hp) {
        this.hp = hp;
    }

    /** 基准方向（世界，水平）：舰首方向绕 y 转 deg 度（0=正前、+90=右舷、−90=左舷） */
    public Vec3 dir(float deg) {
        float rad = (this.parent.getYRot() + deg) * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
    }

    /** 右舷方向（世界，水平）；sideDist 带符号乘它：正=右舷、负=左舷（数轴式） */
    public Vec3 sideDir() {
        return dir(90.0F);
    }

    /** 瞄准基准方向（世界，水平） */
    public Vec3 baseDir() {
        return dir(this.baseAngDeg);
    }

    /** 炮塔中心（世界坐标，= 判定箱 AABB 的中心；setPos 是 AABB 底边） */
    public Vec3 center() {
        return this.part.getBoundingBox().getCenter();
    }

    /** 按宿主位置/朝向摆放判定箱（照抄末影龙：setPos 后写回新旧坐标防插值拉丝）。
     *  宿主 getY()=脚底；PartEntity 的 AABB 以 setPos 为底边，故放置点 = 宿主中心 + dy − 部件半高。 */
    public void placePart() {
        float rad = this.parent.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        Vec3 side = sideDir();
        double x = this.parent.getX() + fwd.x * this.fwdOffset + side.x * this.sideDist;
        double y = this.parent.getY() + this.parent.getBbHeight() * 0.5D + this.dy - PART_SIZE * 0.5D;
        double z = this.parent.getZ() + fwd.z * this.fwdOffset + side.z * this.sideDist;
        this.part.setPos(x, y, z);
        this.part.xo = this.part.xOld = x;
        this.part.yo = this.part.yOld = y;
        this.part.zo = this.part.zOld = z;
    }

    /** 每 tick：每秒重评估自己的目标 → 转向 + 射界判定 + 冷却开火；损毁则冒烟 */
    public void tick() {
        if (isDead()) {
            if (this.parent.tickCount % 4 == 0 && this.parent.level() instanceof ServerLevel server) {
                Vec3 c = center();
                server.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 1.0D, c.z, 2, 0.4D, 0.2D, 0.4D, 0.01D);
            }
            return;
        }
        // 每秒重评估索敌：只锁定「能命中」的目标（射程 + 视线 + 射界），打不到就换/放弃
        if (--this.targetCooldown <= 0) {
            this.targetCooldown = TARGET_SCAN_INTERVAL;
            this.target = pickTarget(this.target);
        }
        LivingEntity target = this.target;
        Vec3 c = center();
        boolean valid = target != null && target.isAlive() && target.distanceToSqr(c) <= this.range * this.range;
        double horiz = 0.0D, vert = 0.0D;
        boolean sight = false;
        if (valid) {
            Vec3 dir = target.getEyePosition().subtract(c).normalize();
            horiz = horizAngle(dir);
            vert = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));
            sight = this.parent.getSensing().hasLineOfSight(target);
        }
        // 目标角度：yaw 状态是「相对基准朝向的角」（0 = 朝向基准方向）；
        // 有目标就盯住（超出射界则贴住射界边缘），没目标绕基准缓慢扫描警戒
        float wantYaw, wantPitch;
        if (valid) {
            wantYaw = (float) Mth.clamp(horiz, -this.arcH, this.arcH);
            wantPitch = (float) Mth.clamp(vert, -this.arcDown, this.arcUp);
        } else {
            wantYaw = Mth.sin((this.parent.tickCount + this.index * 37) * 0.015F) * IDLE_SWEEP;
            wantPitch = -8.0F;
        }
        this.yaw = approach(this.yaw, wantYaw, TURN);
        this.pitch = approach(this.pitch, wantPitch, TURN);
        if (Math.abs(this.yaw - this.syncedYaw) >= 1.0F || Math.abs(this.pitch - this.syncedPitch) >= 1.0F) {
            sync();
        }
        // 必须真的转到位才开火（炮口方向与炮管视觉一致）
        boolean aimed = Math.abs(Mth.wrapDegrees((float) horiz - this.yaw)) <= AIM_YAW
                && Math.abs(Mth.wrapDegrees((float) vert - this.pitch)) <= AIM_PITCH;
        boolean canFire = valid && sight && aimed
                && Math.abs(horiz) <= this.arcH && vert <= this.arcUp && vert >= -this.arcDown;
        if (!canFire) {
            this.cooldown = Math.min(this.cooldown, 10);
            return;
        }
        if (--this.cooldown <= 0) {
            fire(target, c);
            this.cooldown = this.cooldownTicks;
        }
    }

    /** 目标方向相对瞄准基准的水平夹角（带符号，度） */
    private double horizAngle(Vec3 dir) {
        Vec3 base = baseDir();
        double a = Mth.atan2(dir.z, dir.x);
        double b = Mth.atan2(base.z, base.x);
        return Mth.wrapDegrees(Math.toDegrees(a - b));
    }

    /** 保持仍可命中的当前目标；否则在射程内挑最近的、能命中的目标；没有则 null */
    private LivingEntity pickTarget(LivingEntity current) {
        if (current != null && current.isAlive() && canHit(current)) {
            return current;
        }
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        Vec3 c = center();
        AABB box = new AABB(c.x - this.range, c.y - this.range, c.z - this.range,
                c.x + this.range, c.y + this.range, c.z + this.range);
        for (LivingEntity entity : this.parent.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!this.targetFilter.test(entity) || !canHit(entity)) {
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

    /** 目标当前是否可被本炮塔命中（射程 + 视线 + 射界），供索敌筛选，不消耗冷却 */
    public boolean canHit(LivingEntity target) {
        if (isDead() || target == null || !target.isAlive()) {
            return false;
        }
        Vec3 c = center();
        if (target.distanceToSqr(c) > this.range * this.range) {
            return false;
        }
        if (!this.parent.getSensing().hasLineOfSight(target)) {
            return false;
        }
        Vec3 dir = target.getEyePosition().subtract(c).normalize();
        double horiz = horizAngle(dir);
        double vert = Math.toDegrees(Math.asin(Mth.clamp(dir.y, -1.0D, 1.0D)));
        return Math.abs(horiz) <= this.arcH && vert <= this.arcUp && vert >= -this.arcDown;
    }

    /** 发射一颗 testmod 炮弹（炮口在判定箱外，避免自伤） */
    private void fire(LivingEntity target, Vec3 c) {
        Vec3 dir = target.getEyePosition().subtract(c).normalize();
        // 出膛点 = 判定箱中心 + 弹道方向出膛 + 高度偏移（FIRE_DY：只压低炮弹出膛位置，炮塔模型与判定箱不动）
        Vec3 muzzle = c.add(dir.scale(MUZZLE)).add(0.0D, FIRE_DY, 0.0D);
        ShellEntity shell = new ShellEntity(this.parent.level(), this.parent, dir, this.shellPower);
        shell.setPos(muzzle.x, muzzle.y, muzzle.z);
        this.parent.level().addFreshEntity(shell);
        this.parent.playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.7F + this.parent.getRandom().nextFloat() * 0.2F);
    }

    /** 中弹：扣耐久；打空则爆炸损毁（永久停火 + 冒烟） */
    public void damage(float amount) {
        if (isDead()) {
            return;
        }
        this.hp = Math.max(0.0F, this.hp - amount);
        sync();
        this.parent.playSound(SoundEvents.ANVIL_PLACE, 0.8F, 1.5F);
        if (isDead()) {
            Vec3 c = center();
            if (this.parent.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 8, 0.9D, 0.9D, 0.9D, 0.0D);
            }
            this.parent.playSound(SoundEvents.GENERIC_EXPLODE.value(), 2.0F, 0.7F);
        }
    }

    public void sync() {
        this.parent.getEntityData().set(this.data, new Vector3f(this.yaw, this.pitch, this.hp));
        this.syncedYaw = this.yaw;
        this.syncedPitch = this.pitch;
    }

    private static float approach(float current, float want, float maxStep) {
        return current + Mth.clamp(Mth.wrapDegrees(want - current), -maxStep, maxStep);
    }

    // ============================================================
    //  炮塔判定箱：只吃攻击判定，不吃物理碰撞
    // ============================================================
    private class SkyPart extends Airship_Part<T> {

        SkyPart() {
            super(Ship_Turret.this.parent, PART_SIZE, PART_SIZE, false);
        }

        @Override
        protected boolean hurtPart(DamageSource source, float amount) {
            if (Ship_Turret.this.isDead()) {
                return false;
            }
            Ship_Turret.this.damage(amount);
            return true;
        }
    }
}
