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
 * 双联装舰载炮塔组件（独立于 {@link Ship_Turret}）：双联炮管、齐射两发 testmod:shell，
 * 其余能力同单管炮塔——独立旋转（yaw/pitch 限速平滑）、独立血量、独立冷却、按参数设定射界，
 * 自行持有并摆放自己的判定箱（{@link PartEntity}，只吃攻击不吃物理碰撞）。
 *
 * <p>用法（链式配置，挂载位置为<b>相对父类实体</b>的前向/侧向/高度偏移 + 基准朝向）：
 * <pre>{@code
 * Twin_Turret.of(this, 0, DATA_TURRET[0], this::canTarget)
 *         .mount(18.6D, 0.0D, 1.375D, 0.0F)   // fwd, sideDist, dy, baseAngDeg
 *         .build();                            // 其余参数都有默认值
 * }</pre>
 * 宿主实体持有 {@code Twin_Turret<T>[]}，每 tick 依次调用 {@link #placePart()} 与
 * {@link #tick()}。状态经宿主实体的 {@link EntityDataAccessor}&lt;Vector3f&gt;
 * （yaw°, pitch°, hp）同步到客户端。渲染用 Twin_Turret_Model.renderAt(...)（client.model 包）。
 */
public class Twin_Turret<T extends Mob> {

    /** 判定箱尺寸（2×2×2 格，罩住 1.5×1.25×1.25 格的双联炮塔本体） */
    private static final float PART_SIZE = 2.0F;
    /** 炮塔转向速度（度/tick） */
    private static final float TURN = 5.0F;
    /** 开火判定：对准误差（度） */
    private static final float AIM_YAW = 10.0F, AIM_PITCH = 12.0F;
    /** 无目标扫描警戒幅度 */
    private static final float IDLE_SWEEP = 25.0F;
    /** 炮口外移（格，出判定箱；双联炮管长 2.5 格，从口部前方出膛） */
    private static final double MUZZLE = 2.0D;
    /** 双管横向间距之半（格，模型炮管中心离中轴 ±6.5 单位 = ±0.4 格） */
    private static final double BARREL_OFF = 0.4D;
    /** 出膛点高度偏移（格，相对判定箱中心）：正挂炮管在中心上方 0.25 格（主人实测校准） */
    private static final double FIRE_DY = 0.25D;
    /** 倒挂（flipped）出膛点高度偏移：倒挂判定箱有 +1 反向校准，炮管垂在中心下方 0.75 格（= −FIRE_DY − 0.5） */
    private static final double FLIPPED_FIRE_DY = -FIRE_DY - 0.5D;

    /** 索敌重评估间隔（tick，1 秒） */
    private static final int TARGET_SCAN_INTERVAL = 20;

    // ===== 弹道预判（与 ShellEntity 的物理一致：初速 2.5、重力 0.05、加速 0.1、阻尼 0.95）=====
    private static final double SHELL_SPEED = 2.5D;
    private static final double SHELL_GRAVITY = 0.05D;
    private static final double SHELL_ACCEL = 0.1D;
    private static final double SHELL_INERTIA = 0.95D;
    /** 弹道模拟的 tick 数上限（覆盖默认 48 格射程） */
    private static final int SIM_TICKS = 240;

    private final T parent;
    private final EntityDataAccessor<Vector3f> data;
    private final Predicate<LivingEntity> targetFilter;
    private final SkyPart part;
    private final double fwdOffset, sideDist, dy;
    private final int index;
    /** 基准朝向（度，相对舰首方向）：0=正前、+90=右舷、−90=左舷；与渲染端 BASE_YAW 数值一致 */
    private final float baseAngDeg;
    /** 倒挂挂载（舰腹炮塔）：渲染翻转 180°，判定箱顶面贴在挂载点上 */
    private final boolean flipped;
    private final double arcH, arcUp, arcDown, range;
    private final int cooldownTicks;
    private final float shellPower, maxHp;
    /** 发射散布（度）：随机加减在弹道上，越大越散 */
    private final float spreadDeg;

    private float yaw, pitch, hp;
    private float syncedYaw, syncedPitch;
    private int cooldown;
    /** 炮塔自己的目标（独立索敌，每秒重评估） */
    private LivingEntity target;
    private int targetCooldown;

    private Twin_Turret(T parent, int index, EntityDataAccessor<Vector3f> data,
                        Predicate<LivingEntity> targetFilter,
                        double fwdOffset, double sideDist, double dy, float baseAngDeg, boolean flipped,
                        double arcH, double arcUp, double arcDown,
                        double range, int cooldownTicks, float shellPower, float maxHp, float spreadDeg) {
        this.parent = parent;
        this.index = index;
        this.data = data;
        this.targetFilter = targetFilter;
        this.fwdOffset = fwdOffset;
        this.sideDist = sideDist;
        this.dy = dy;
        this.baseAngDeg = baseAngDeg;
        this.flipped = flipped;
        this.arcH = arcH;
        this.arcUp = arcUp;
        this.arcDown = arcDown;
        this.range = range;
        this.cooldownTicks = cooldownTicks;
        this.shellPower = shellPower;
        this.maxHp = maxHp;
        this.spreadDeg = spreadDeg;
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

        // 参数默认值（双联炮塔比单管更耐打、齐射火力翻倍所以冷却略长），最简用法 = of(...).mount(...).build()
        private double fwdOffset, sideDist, dy;
        private float baseAngDeg = 90.0F;
        private boolean flipped = false;
        private double arcH = 60.0D, arcUp = 60.0D, arcDown = 70.0D, range = 48.0D;
        private int cooldownTicks = 120;
        private float shellPower = 4.0F, maxHp = 100.0F;
        /** 发射散布（度），默认 1.5 —— 弹道预判瞄准后的随机偏移 */
        private float spreadDeg = 1.5F;

        Builder(T parent, int index, EntityDataAccessor<Vector3f> data, Predicate<LivingEntity> targetFilter) {
            this.parent = parent;
            this.index = index;
            this.data = data;
            this.targetFilter = targetFilter;
        }

        /** 挂载位置（相对父类实体，格）：fwdOffset=前向偏移（+为舰首方向）、sideDist=离中轴线的
         *  有向距离（数轴式，+右舷/−左舷）、dy=垂直偏移（+为上）。
         *  baseAngDeg=基准朝向（度，相对舰首方向）：0=正前、+90=正右舷、−90=正左舷、180=正后；
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

        /** 倒挂挂载（舰腹炮塔）：渲染翻转 180° 挂在挂点下方；dy 应使判定箱顶面贴在挂载点上 */
        public Builder<T> flipped() {
            this.flipped = true;
            return this;
        }

        /** 射程（格），默认 48 */
        public Builder<T> range(double range) {
            this.range = range;
            return this;
        }

        /** 冷却（tick），默认 120（齐射两发，比单管略长） */
        public Builder<T> cooldown(int cooldownTicks) {
            this.cooldownTicks = cooldownTicks;
            return this;
        }

        /** 炮弹威力（每发），默认 8.0 */
        public Builder<T> power(float shellPower) {
            this.shellPower = shellPower;
            return this;
        }

        /** 炮弹血量不在这里（见 hp()）；炮塔血量，默认 80 */
        public Builder<T> hp(float maxHp) {
            this.maxHp = maxHp;
            return this;
        }

        /** 发射散布（度），默认 1.5 */
        public Builder<T> spread(float spreadDeg) {
            this.spreadDeg = spreadDeg;
            return this;
        }

        public Twin_Turret<T> build() {
            return new Twin_Turret<>(this.parent, this.index, this.data, this.targetFilter,
                    this.fwdOffset, this.sideDist, this.dy, this.baseAngDeg, this.flipped,
                    this.arcH, this.arcUp, this.arcDown,
                    this.range, this.cooldownTicks, this.shellPower, this.maxHp, this.spreadDeg);
        }
    }

    public PartEntity<T> part() {
        return this.part;
    }

    /** 挂载参数（fwd, sideDist, dy, baseAngDeg, flipped?1:0）——供宿主把判定箱位置换算成渲染挂点 */
    public double[] mountParams() {
        return new double[] {this.fwdOffset, this.sideDist, this.dy, this.baseAngDeg, this.flipped ? 1.0D : 0.0D};
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
     *  宿主 getY()=脚底；PartEntity 的 AABB 以 setPos 为底边，故放置点 = 宿主中心 + dy − 部件半高。
     *  末尾 ±1 是主人实测校准的视觉补偿：正挂 −1 下移；倒挂（flipped）炮塔垂向相反，取 +1 反向补偿。 */
    public void placePart() {
        // 判定箱摆放用 yBodyRot（渲染主体与炮塔模型都用它），保证判定箱与模型朝向参考完全一致。
        // （用 getYRot() 会因生成时朝向随机 / 飞行实体 yBodyRot 不同步，导致每次生成的错位都不固定。）
        float rad = this.parent.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        // 右舷 = 舰首方向绕 y 右转 90°，与 fwd 同用 yBodyRot（侧向距离参与摆放，
        // 不能再走 sideDir()——那里面是 getYRot()，两个基准不一致会让 fwd/side 不正交 → 判定箱侧向错位）
        Vec3 side = new Vec3(-Mth.cos(rad), 0.0D, -Mth.sin(rad));
        double x = this.parent.getX() + fwd.x * this.fwdOffset + side.x * this.sideDist;
        double y = this.parent.getY() + this.parent.getBbHeight() * 0.5D + this.dy - PART_SIZE * 0.5D
                + (this.flipped ? 1.0D : -1.0D);
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
                // 倒挂炮塔塔身垂在下方，冒烟位置跟着反向
                double smokeY = c.y + (this.flipped ? -1.0D : 1.0D);
                server.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, smokeY, c.z, 2, 0.4D, 0.2D, 0.4D, 0.01D);
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
            // 弹道预判瞄准：只补偿重力下坠（不考虑目标移动提前量），迭代抬高仰角让弧形弹道落在目标上
            Vec3 aimDir = predictAim(c, target);
            horiz = horizAngle(aimDir);
            vert = Math.toDegrees(Math.asin(Mth.clamp(aimDir.y, -1.0D, 1.0D)));
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

    /**
     * 弹道预判瞄准（只补偿重力下坠，不考虑目标移动提前量）：
     * 直瞄目标当前位置，但迭代抬高"瞄准点"，使炮弹的弧形弹道落在目标上。
     * 与 {@link ShellEntity} 的物理保持一致（初速 2.5、重力 0.05、加速 0.1、阻尼 0.95）。
     */
    private Vec3 predictAim(Vec3 origin, LivingEntity target) {
        Vec3 tp = target.getEyePosition();
        double drop = 0.0D;
        for (int iter = 0; iter < 8; iter++) {
            Vec3 aimDir = tp.add(0.0D, drop, 0.0D).subtract(origin);
            Vec3 closest = closestTrajectoryPoint(origin, aimDir, tp);
            double err = tp.y - closest.y;   // 弹道偏低 → err>0 → 抬高瞄准点
            drop += err * 0.5D;              // 半步进防振荡
            if (Math.abs(err) < 0.05D) {
                return aimDir.normalize();
            }
        }
        return tp.add(0.0D, drop, 0.0D).subtract(origin).normalize();
    }

    /** 模拟炮弹弹道（复制 ShellEntity 的 tick 物理），返回离目标最近的点。 */
    private Vec3 closestTrajectoryPoint(Vec3 origin, Vec3 aim, Vec3 tp) {
        Vec3 p = origin;
        Vec3 v = aim.normalize().scale(SHELL_SPEED);
        Vec3 best = origin;
        double bestD = Double.MAX_VALUE;
        for (int t = 0; t < SIM_TICKS; t++) {
            v = v.add(0.0D, -SHELL_GRAVITY, 0.0D);
            p = p.add(v);
            v = v.add(v.normalize().scale(SHELL_ACCEL)).scale(SHELL_INERTIA);
            double d = p.distanceToSqr(tp);
            if (d < bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
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

    /** 齐射两颗 testmod 炮弹：左右炮管各一发（炮口横向错开 ±BARREL_OFF，出膛点在判定箱外避免自伤）。
     *  弹道用预判方向（下坠补偿），再叠加随机散布。 */
    private void fire(LivingEntity target, Vec3 c) {
        // 弹道预判：迭代抬高仰角，让弧形弹道落在目标当前位置（只补偿下坠，无提前量）
        Vec3 dir = predictAim(c, target);
        // 发射随机散布：在弹道方向上加随机小偏移（度 → 归一化）
        if (this.spreadDeg > 0.0F) {
            double s = this.spreadDeg * Mth.DEG_TO_RAD;
            dir = dir.add(
                    (this.parent.getRandom().nextDouble() - 0.5D) * s * 2.0D,
                    (this.parent.getRandom().nextDouble() - 0.5D) * s * 2.0D,
                    (this.parent.getRandom().nextDouble() - 0.5D) * s * 2.0D
            ).normalize();
        }
        // 炮管横向：水平面内垂直于弹道的方向（双管左右舷错开）
        Vec3 lateral = new Vec3(-dir.z, 0.0D, dir.x);
        if (lateral.lengthSqr() < 1.0E-6D) {
            lateral = sideDir();
        }
        lateral = lateral.normalize();
        // 倒挂时炮管垂在判定箱中心下方，用独立的倒挂出膛偏移（简单取反会偏高 0.5 格）
        double fireDy = this.flipped ? FLIPPED_FIRE_DY : FIRE_DY;
        for (int barrel = -1; barrel <= 1; barrel += 2) {
            Vec3 muzzle = c.add(dir.scale(MUZZLE)).add(lateral.scale(BARREL_OFF * barrel)).add(0.0D, fireDy, 0.0D);
            ShellEntity shell = new ShellEntity(this.parent.level(), this.parent, dir, this.shellPower);
            shell.setPos(muzzle.x, muzzle.y, muzzle.z);
            this.parent.level().addFreshEntity(shell);
        }
        this.parent.playSound(SoundEvents.FIRECHARGE_USE, 2.0F, 0.6F + this.parent.getRandom().nextFloat() * 0.2F);
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
            super(Twin_Turret.this.parent, PART_SIZE, PART_SIZE, false);
        }

        @Override
        protected boolean hurtPart(DamageSource source, float amount) {
            if (Twin_Turret.this.isDead()) {
                return false;
            }
            Twin_Turret.this.damage(amount);
            return true;
        }
    }
}
