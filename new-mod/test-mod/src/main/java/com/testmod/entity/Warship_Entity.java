package com.testmod.entity;

import com.testmod.item.Laser_Beam;
import com.testmod.network.Warship_Laser_Payload;

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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 战舰（testmod:warship）：60 格长的大型飞行敌对生物（管线照抄亡灵天城）。
 *
 * <p>结构：纺锤形舰体（舯部 8.4 格宽 × 7 格高）+ 贯通甲板与上层建筑 + 4 具侧舷螺旋桨舱 +
 * 尾翼 + 底部吊舱；8 门双联装炮塔（{@link Twin_Turret}，舰艏/舰艉甲板各 1 + 两舷耳台各 2 + 舰腹前后各 1 倒挂）。
 * 本体 8×7×8 原版碰撞箱（物理碰撞 + 可被攻击；模型舰体中心 = 实体脚底高度）；
 * 14 个船体判定箱（12 段龙骨 6×6 + 2 段吊舱 3×3，扣本体血）+ 8 炮塔（各扣各的血）。
 *
 * <p>AI 与亡灵天城相同：原版 MoveControl + 旅行限速（1.0 格/s）、高度保持、
 * 逼近/环绕目标（EngageGoal）+ 恶魂式游荡（FloatAroundGoal）；索敌 = 玩家 + 怪物。
 * 炮塔各自独立索敌（每秒重评估，只锁定能命中的目标），船体只保留「最近的
 * 可攻击目标」作为机动目标。
 */
public class Warship_Entity extends FlyingMob implements Enemy {

    // ===== 可调参数 =====
    private static final double HOVER_MIN = 16.0D;
    /** 无目标时的巡航空域上限（离地） */
    private static final double HOVER_CEIL = 32.0D;
    private static final double TARGET_RANGE = 64.0D, TARGET_KEEP = 80.0D;
    /** 索敌重评估间隔（tick，1 秒） */
    private static final int SCAN_INTERVAL = 20;
    private static final double ENGAGE_DIST = 40.0D;
    /** 最大速度：1.0 格/s（万吨战舰比天城更笨重），travel 里硬限速 */
    private static final double MAX_SPEED = 1.0D / 20.0D;
    /** 龙骨判定箱：6×6×6，沿龙骨每 5 格一段（±27.5，共 12 段，铺满 60 格舰体） */
    private static final float KEEL_SIZE = 6.0F;
    /** 龙骨判定箱高度偏移：舰体视觉中心 = 实体脚底，部件中心对准舰体中心（−半箱高 3.5） */
    private static final double KEEL_DY = -3.5D;
    /** 吊舱判定箱：3×3×3，吊舱中心在舰体中心下方 4.625 格（相对实体中心 −8.125） */
    private static final float GONDOLA_SIZE = 3.0F;
    private static final double GONDOLA_DY = -8.125D;

    /** 炮塔数（新增炮塔 = 数组 +1 并加一行配置） */
    private static final int TURRET_COUNT = 8;

    // ===== 激光发射器（舰体前/中/后 3 处，40 格内挑一个目标，大散布，速射弹幕）=====
    private static final double LASER_RANGE = 40.0D;
    /** 激光发射点：沿龙骨前向偏移（+为舰首方向），-30/0/30 = 舰艉/舯部/舰艏（每发轮流一处） */
    private static final double[] LASER_EMIT_FWD = {-20.0D, 0.0D, 20.0D};
    /** 较大散布（向量偏移，0.08 ≈ 4.6°/轴，40 格处散开约 3.2 格） */
    private static final double LASER_SPREAD = 0.08D;
    /** 连发弹数：每 tick 一道激光，连打这么多发后冷却 */
    private static final int LASER_BURST = 10;
    /** 连发结束后的冷却（tick） */
    private static final int LASER_COOLDOWN_TICKS = 10;
    /** 本轮连发已发射数 */
    private int laserShots = 0;
    /** 下一发用的发射点索引（在 LASER_EMIT_FWD 内循环） */
    private int laserEmitterIndex = 0;
    /** 连发后的冷却剩余 */
    private int laserCooldown = 0;
    /** 每个火力点独立掌握自己的目标（各自索敌，可打不同目标） */
    private final LivingEntity[] laserTargets = new LivingEntity[LASER_EMIT_FWD.length];
    private static final EntityDataAccessor<Vector3f>[] DATA_TURRET = new EntityDataAccessor[TURRET_COUNT];
    static {
        for (int i = 0; i < TURRET_COUNT; i++) {
            DATA_TURRET[i] = SynchedEntityData.defineId(Warship_Entity.class, EntityDataSerializers.VECTOR3);
        }
    }

    private final WarshipPart[] hullParts;
    private final Twin_Turret<Warship_Entity>[] turrets;
    /** 渲染用挂点（由 mount 推导）：每行 {local x, local y, local z, baseYaw, flipped}，模型空间、相对父类实体 */
    private final float[][] turretRender;
    private final PartEntity<?>[] parts;
    private int targetScanCooldown;

    public Warship_Entity(EntityType<? extends Warship_Entity> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl(this);
        this.xpReward = 150;
        // 龙骨 12 段（fwd ±27.5 每 5 格一段，部件中心在舰体中心高度）+ 吊舱 2 段（fwd 6.5/11，挂在舰腹）
        this.hullParts = new WarshipPart[14];
        for (int i = 0; i < 12; i++) {
            this.hullParts[i] = new WarshipPart(this, KEEL_SIZE, KEEL_DY, 27.5D - i * 5.0D);
        }
        this.hullParts[12] = new WarshipPart(this, GONDOLA_SIZE, GONDOLA_DY, 4D);
        this.hullParts[13] = new WarshipPart(this, GONDOLA_SIZE, GONDOLA_DY, 8.0D);
        // 炮塔配置（每行一门，顺序与渲染一致；改 mount 数值即可，判定箱与模型会同步移动）：
        //   mount(前向偏移, 侧向距离, 垂直偏移, 基准朝向) —— 相对父类实体（格）：
        //     前向偏移 +为舰首方向；侧向距离 +右舷/−左舷（离中轴线的距离）；垂直偏移 +为上（相对实体中心）
        //     基准朝向 0=正前、+90=正右舷、−90=正左舷、180=正后（与渲染 BASE_YAW 同值）
        // 垂直偏移：舰艏/舰艉炮座在甲板炮位平台上（台顶 = 脚底 +3.875 格）→ dy=+1.375；
        // 舷侧耳台（台顶 = 脚底 +2.625 格）→ dy=+0.125；判定箱底面落在台面上；
        // 舰腹倒挂炮（flipped）：腹下炮座底面 = 脚底 −3.875 格 → dy=−8.375，判定箱顶面贴在座底
        // 其余参数用 Builder 默认值（射程 48、冷却 120 tick、威力 8.0×双发、血量 80），
        // 想自定义可链式追加 .arc().range().cooldown().power().hp()
        this.turrets = new Twin_Turret[] {
                Twin_Turret.of(this, 0, DATA_TURRET[0], this::canTarget).mount(18.6D, 0.0D, 1.5D, 0.0F).arc(120.0D, 80.0D, 30.0D).build(),    // 舰艏主炮
                Twin_Turret.of(this, 1, DATA_TURRET[1], this::canTarget).mount(-18.6D, 0.0D, 1.5D, 180.0F).arc(120.0D, 80.0D, 30.0D).build(),  // 舰艉主炮
                Twin_Turret.of(this, 2, DATA_TURRET[2], this::canTarget).mount(7.5D, 4D, 0.5D, 90.0F).arc(180.0D, 80.0D, 70.0D).build(),   // 右舷前炮
                Twin_Turret.of(this, 3, DATA_TURRET[3], this::canTarget).mount(-10.0D, 4D, 0.5D, 90.0F).arc(180.0D, 80.0D, 70.0D).build(),  // 右舷后炮
                Twin_Turret.of(this, 4, DATA_TURRET[4], this::canTarget).mount(7.5D, -4D, 0.5D, -90.0F).arc(180.0D, 80.0D, 70.0D).build(),  // 左舷前炮
                Twin_Turret.of(this, 5, DATA_TURRET[5], this::canTarget).mount(-10.0D, -4D, 0.5D, -90.0F).arc(180.0D, 80.0D, 70.0D).build(), // 左舷后炮
                Twin_Turret.of(this, 6, DATA_TURRET[6], this::canTarget).mount(12.5D, 0.0D, -9D, 0.0F).arc(120.0D, 30.0D, 110.0D).flipped().build(),   // 舰腹前炮（倒挂）
                Twin_Turret.of(this, 7, DATA_TURRET[7], this::canTarget).mount(-12.5D, 0.0D, -9D, 180.0F).arc(120.0D, 30.0D, 110.0D).flipped().build(),  // 舰腹后炮（倒挂）
        };
        // 渲染挂点由上面的 mount 数值自动推导（x=−侧向距离、y=1.501−实体中心−dy、z=−前向偏移、yaw=基准朝向、
        // flipped=倒挂标记），保证模型与判定箱始终同位置；实体中心用 getBbHeight() 实算，改实体尺寸也会自动跟随
        this.turretRender = new float[this.turrets.length][5];
        float bbHalf = this.getBbHeight() * 0.5F;
        for (int i = 0; i < this.turrets.length; i++) {
            double[] m = this.turrets[i].mountParams();
            this.turretRender[i] = new float[] {
                    (float) -m[1],
                    1.501F - bbHalf - (float) m[2],
                    (float) -m[0],
                    (float) m[3],
                    (float) m[4],
            };
        }
        this.parts = new PartEntity<?>[this.hullParts.length + this.turrets.length];
        int i = 0;
        for (PartEntity<?> part : this.hullParts) {
            this.parts[i++] = part;
        }
        for (Twin_Turret<Warship_Entity> turret : this.turrets) {
            this.parts[i++] = turret.part();
        }
        // 部件 id 必须是本体 id 的连续后继（NeoForge 修 MC-158205 的做法），
        // 否则客户端点击部件时服务端找不到对应部件。
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1200.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        for (EntityDataAccessor<Vector3f> data : DATA_TURRET) {
            builder.define(data, new Vector3f(0.0F, 0.0F, 80.0F));
        }
    }

    /** 给渲染用：第 i 门炮塔状态 (yaw°, pitch°, hp) */
    public Vector3f getTurretState(int index) {
        return this.entityData.get(DATA_TURRET[index]);
    }

    /** 给渲染用：各炮塔模型空间挂点 {local x, local y, local z, baseYaw, flipped}（相对父类实体，格；右舷 x 负、舰首 z 负、上 y 负） */
    public float[][] getTurretRender() {
        return this.turretRender;
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new FlyingPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(2, new EngageGoal(this));
        this.goalSelector.addGoal(5, new FloatAroundGoal(this));
    }

    /** 最大速度硬限速（1.0 格/s），覆盖一切速度来源（飞行/环绕/游荡） */
    @Override
    public void travel(Vec3 input) {
        if (this.isEffectiveAi()) {
            double speed = this.getDeltaMovement().length();
            if (speed > MAX_SPEED) {
                this.setDeltaMovement(this.getDeltaMovement().scale(MAX_SPEED / speed));
            }
        }
        super.travel(input);
    }

    @Override
    public void tick() {
        super.tick();
        this.updateParts();
        if (this.level().isClientSide()) {
            return;
        }
        // 自动上升防止落地：低于地面安全高度就停住下坠并抬升目标点；无目标时过高拉回（防一路漂高）
        if (this.tickCount % 5 == 0) {
            double groundY = this.scanGroundY();
            double dh = this.getY() - groundY;
            if (dh < HOVER_MIN) {
                // 逼近地面：先停掉向下速度（覆盖目标带来的下坠），再把目标点抬到安全高度
                Vec3 v = this.getDeltaMovement();
                if (v.y < 0.0D) {
                    this.setDeltaMovement(v.x, 0.0D, v.z);
                }
                this.moveControl.setWantedPosition(this.getX(), groundY + 20.0D, this.getZ(), 1.0D);
            } else if (this.getTarget() == null && dh > HOVER_CEIL) {
                this.moveControl.setWantedPosition(this.getX(), groundY + 20.0D, this.getZ(), 1.0D);
            }
        }
        // 索敌：每隔 1 秒重评估——只追「至少一门炮塔能命中」的目标，打不到就换/丢
        if (--this.targetScanCooldown <= 0) {
            this.targetScanCooldown = SCAN_INTERVAL;
            LivingEntity target = this.getTarget();
            if (target == null || !target.isAlive()
                    || this.distanceToSqr(target) > TARGET_KEEP * TARGET_KEEP
                    || !this.canHit(target)) {
                this.setTarget(this.findNearestAttackable());
            }
        }
        // 炮塔：各自每秒重评估索敌 + 转向 + 射界判定 + 开火；损毁则冒烟
        for (Twin_Turret<Warship_Entity> turret : this.turrets) {
            turret.tick();
        }
        // 激光速射：每 tick 一处发射点（轮流）发一道；每个火力点独立索敌（可打不同目标）；连满 10 发后冷却 10 tick
        if (this.laserCooldown > 0) {
            this.laserCooldown--;
        } else {
            int idx = this.laserEmitterIndex;
            LivingEntity laserTarget = this.refreshLaserTarget(idx);
            if (laserTarget != null) {
                fireOneLaser(idx, laserTarget);
                this.laserEmitterIndex = (idx + 1) % LASER_EMIT_FWD.length;
                if (++this.laserShots >= LASER_BURST) {
                    this.laserShots = 0;
                    this.laserCooldown = LASER_COOLDOWN_TICKS;
                }
            } else {
                this.laserShots = 0; // 该点无目标，重置连发计数
            }
        }
    }

    /** 最近的「可攻击」目标（玩家 + 怪物；射程 + 视线 + 射界全通过），没有则 null */
    private LivingEntity findNearestAttackable() {
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = this.getBoundingBox().inflate(TARGET_RANGE);
        for (LivingEntity entity : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!this.canTarget(entity) || !this.canHit(entity)) {
                continue;
            }
            double dist = this.distanceToSqr(entity);
            if (dist < bestDist) {
                bestDist = dist;
                best = entity;
            }
        }
        return best;
    }

    /** 当前目标是否至少有一门炮塔能命中 */
    private boolean canHit(LivingEntity target) {
        for (Twin_Turret<Warship_Entity> turret : this.turrets) {
            if (turret.canHit(target)) {
                return true;
            }
        }
        return false;
    }

    /** 部件跟随本体位置/朝向 */
    private void updateParts() {
        // 舰体/龙骨判定箱用 yBodyRot（与渲染主体/炮塔一致），否则判定箱会随生成的随机朝向偏到不同位置。
        float rad = this.yBodyRot * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        for (WarshipPart part : this.hullParts) {
            placePart(part, fwd.scale(part.fwdOffset), part.dy);
        }
        for (Twin_Turret<Warship_Entity> turret : this.turrets) {
            turret.placePart();
        }
    }

    private void placePart(PartEntity<?> part, Vec3 offset, double dy) {
        double x = this.getX() + offset.x, z = this.getZ() + offset.z;
        // 实体 getY()=脚底；PartEntity 的 AABB 以 setPos 为底边向上延伸，
        // 所以先算「实体中心 + dy」再减去部件半高，让箱子中心落在目标点上
        double y = this.getY() + this.getBbHeight() * 0.5D + dy
                - part.getDimensions(part.getPose()).height() * 0.5D;
        part.setPos(x, y, z);
        part.xo = part.xOld = x;
        part.yo = part.yOld = y;
        part.zo = part.zOld = z;
    }

    /** 部件受击：炮塔扣自己的耐久，龙骨/吊舱转给本体血量 */
    public boolean hurtPart(PartEntity<?> part, DamageSource source, float amount) {
        for (Twin_Turret<Warship_Entity> turret : this.turrets) {
            if (turret.partMatches(part)) {
                if (!turret.isDead()) {
                    turret.damage(amount);
                }
                return true;
            }
        }
        return this.hurt(source, amount);
    }

    /** 自家炮弹的命中/爆炸不伤自身（ShellEntity 撞部件也会爆炸，需在此滤掉） */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof ShellEntity shell && shell.getOwner() == this) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** 向下扫描地面高度（虚空/海上则返回自身高度） */
    private double scanGroundY() {
        Vec3 from = this.position();
        BlockHitResult hit = this.level().clip(new ClipContext(
                from, from.subtract(0.0D, 96.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().getY() + 1.0D : this.getY();
    }

    /** 索敌过滤：任何生物都打（玩家/怪物/动物/村民/中立等）；仅排除自己、已死、旁观者 */
    private boolean canTarget(LivingEntity entity) {
        if (entity == this || entity.isDeadOrDying() || entity.isSpectator()) {
            return false;
        }
        return true;
    }

    /** 某发射点的枪口位置（世界，沿龙骨前向偏移） */
    private Vec3 laserMuzzle(double fwdOffset) {
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(this.getX() - Mth.sin(rad) * fwdOffset,
                this.getY() + this.getBbHeight() * 0.5D,
                this.getZ() + Mth.cos(rad) * fwdOffset);
    }

    /** 指定发射点独立索敌：40 格内离该点最近的可攻击目标（不同点可打不同目标） */
    private LivingEntity pickLaserTargetNear(double fwdOffset) {
        Vec3 muzzle = laserMuzzle(fwdOffset);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        AABB box = new AABB(muzzle.x - LASER_RANGE, muzzle.y - LASER_RANGE, muzzle.z - LASER_RANGE,
                muzzle.x + LASER_RANGE, muzzle.y + LASER_RANGE, muzzle.z + LASER_RANGE);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box)) {
            if (!this.canTarget(e)) {
                continue;
            }
            double d = e.distanceToSqr(muzzle);
            if (d <= LASER_RANGE * LASER_RANGE && d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        return best;
    }

    /** 刷新某发射点的目标：当前目标还在射程内就沿用，否则（独立）换一个最近的 */
    private LivingEntity refreshLaserTarget(int i) {
        LivingEntity t = this.laserTargets[i];
        if (t != null && t.isAlive() && t.distanceToSqr(laserMuzzle(LASER_EMIT_FWD[i])) <= LASER_RANGE * LASER_RANGE) {
            return t;
        }
        t = pickLaserTargetNear(LASER_EMIT_FWD[i]);
        this.laserTargets[i] = t;
        return t;
    }

    /** 占位光束（len=0，客户端画光束时自动跳过）：编解码固定 7 float/条，空数组会导致解码越界断连 */
    private static final float[] EMPTY_BEAM = new float[]{0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F};

    /** 发一道激光：从指定发射点朝该点自己的目标开火（大散布），并把光束数据发给客户端画。 */
    private void fireOneLaser(int emitter, LivingEntity target) {
        if (!(this.level() instanceof ServerLevel server)) {
            return;
        }
        Vec3 muzzle = laserMuzzle(LASER_EMIT_FWD[emitter]);
        Vec3 dir = Laser_Beam.spread(target.getEyePosition().subtract(muzzle).normalize(), this.getRandom(), LASER_SPREAD);
        Laser_Beam.Beam beam = Laser_Beam.computeBeam(this.level(), this, muzzle, dir);
        Laser_Beam.fire(server, this, beam);
        float[] line = new float[]{(float) beam.start().x, (float) beam.start().y, (float) beam.start().z,
                (float) beam.dir().x, (float) beam.dir().y, (float) beam.dir().z, (float) beam.len()};
        PacketDistributor.sendToAllPlayers(new Warship_Laser_Payload(line, EMPTY_BEAM, EMPTY_BEAM));
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

    /** 本体碰撞箱可点击：打舰体中部 = 扣本体血（判定箱在部件上） */
    @Override
    public boolean isPickable() {
        return true;
    }

    /** 免疫自己造成的伤害 */
    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.getEntity() == this || super.isInvulnerableTo(source);
    }

    /** 万吨巨物推不动 */
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

    /** 坠毁：沿 60 格船身炸出一串爆炸粒子 */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel server) {
            float rad = this.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
            for (int i = -28; i <= 28; i += 4) {
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
        return this.getBoundingBox().inflate(32.0D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        for (int i = 0; i < this.turrets.length; i++) {
            tag.putFloat("TurretHp" + i, this.turrets[i].getHp());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("TurretHp0")) {
            for (int i = 0; i < this.turrets.length; i++) {
                this.turrets[i].setHp(tag.getFloat("TurretHp" + i));
                this.turrets[i].sync();
            }
        }
    }

    // ============================================================
    //  部件实体：只参与攻击判定，不参与物理碰撞（本体箱负责物理碰撞）
    // ============================================================
    private static class WarshipPart extends Airship_Part<Warship_Entity> {

        final double dy, fwdOffset;

        WarshipPart(Warship_Entity parent, float size, double dy, double fwdOffset) {
            super(parent, size, size, false);
            this.dy = dy;
            this.fwdOffset = fwdOffset;
        }

        @Override
        protected boolean hurtPart(DamageSource source, float amount) {
            return this.getParent().hurtPart(this, source, amount);
        }
    }

    // ============================================================
    //  游荡：恶魂式随机漂浮（高度保持逻辑在 tick 里）
    // ============================================================
    private static class FloatAroundGoal extends Goal {
        private final Warship_Entity mob;

        FloatAroundGoal(Warship_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null || this.mob.getMoveControl().hasWanted()) {
                return false;
            }
            // 游荡高度限制在离地 17~48 格的空域带内（防一路向上漂）
            double ground = this.mob.scanGroundY();
            double x = this.mob.getX() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 24.0F;
            double y = Mth.clamp(this.mob.getY() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 8.0F,
                    ground + HOVER_MIN + 1.0D, ground + 48.0D);
            double z = this.mob.getZ() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 24.0F;
            this.mob.getMoveControl().setWantedPosition(x, y, z, 1.0D);
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return false;
        }
    }

    // ============================================================
    //  交战：远处逼近目标上空；近处环绕 + 悬停
    // ============================================================
    private static class EngageGoal extends Goal {
        private final Warship_Entity mob;

        EngageGoal(Warship_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
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
            double hoverY = Math.max(target.getY() + 14.0D, this.mob.scanGroundY() + HOVER_MIN);
            if (this.mob.distanceToSqr(target) > ENGAGE_DIST * ENGAGE_DIST) {
                // 逼近目标上空
                this.mob.moveControl.setWantedPosition(target.getX(), hoverY, target.getZ(), 1.0D);
            } else {
                // 环绕 + 垂直保持 + 向心收敛（修大圈：距离偏大就向内收，保持在理想环绕半径附近）
                this.mob.moveControl.setWantedPosition(this.mob.getX(), hoverY, this.mob.getZ(), 1.0D);
                Vec3 toTarget = new Vec3(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
                double distSqr = toTarget.lengthSqr();
                if (distSqr > 1.0E-4D) {
                    double dist = Math.sqrt(distSqr);
                    Vec3 unit = toTarget.scale(1.0D / dist);              // 指向目标（水平）
                    Vec3 orbit = unit.yRot((float) (Math.PI / 2.0D));     // 切向（环绕）
                    double ideal = ENGAGE_DIST * 0.6D;                    // 理想环绕半径
                    // 距离偏大 → 加向内分量把它拉回环上；偏小 → 向外推（防贴脸/逃逸）
                    Vec3 move = orbit.add(unit.scale((dist - ideal) * 0.03D));
                    Vec3 hv = move.normalize().scale(MAX_SPEED);
                    Vec3 v = this.mob.getDeltaMovement();
                    this.mob.setDeltaMovement(hv.x, v.y, hv.z);
                }
            }
        }
    }
}
