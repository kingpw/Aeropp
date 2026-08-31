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
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 亡灵天城（testmod:undead_sky_city）：大型蒸汽风格敌对飞行实体。
 *
 * <p>结构：气囊 20×5×5（方块拼椭圆）+ 吊舱纺锤（二维剖面纵向拉伸）+ 每侧 3 门共 6 门炮塔。
 * 本体 6×8×6 原版碰撞箱（物理碰撞 + 可被攻击）；
 * 12 个 PartEntity 判定箱：气囊 4 段 + 吊舱首尾 2 段（扣本体血）+ 6 炮塔（各扣各的血）。
 *
 * <p>AI：用原版 MoveControl + 旅行限速（1.5 格/s）、高度保持（tick 里扫描地面）、
 * 逼近/环绕目标（EngageGoal）+ 恶魂式游荡（FloatAroundGoal）；索敌 = 玩家 + 怪物。
 * **炮塔各自独立索敌（每秒重评估，只锁定能命中的目标）**，船体只保留「最近的
 * 可攻击目标」作为机动目标（逼近/环绕用）。炮塔逻辑全部在可复用组件 {@link Ship_Turret} 里。
 */
public class Undead_Sky_City_Entity extends FlyingMob implements Enemy {

    // ===== 可调参数 =====
    private static final double HOVER_MIN = 12.0D;
    /** 无目标时的巡航空域上限（离地） */
    private static final double HOVER_CEIL = 24.0D;
    private static final double TARGET_RANGE = 64.0D, TARGET_KEEP = 80.0D;
    /** 索敌重评估间隔（tick，1 秒） */
    private static final int SCAN_INTERVAL = 20;
    private static final double ENGAGE_DIST = 30.0D;
    /** 最大速度：1.5 格/s（主人设定），travel 里硬限速 */
    private static final double MAX_SPEED = 1.5D / 20.0D;
    /** 气囊判定箱：5×5×5，中心世界 8（贴合新模型气囊 5.5~10.5） */
    private static final float GASBAG_SIZE = 5.0F;
    private static final double GASBAG_DY = 5.5D;
    /** 吊舱首尾判定箱偏移（与本体箱拼出约 10 格长） */
    private static final double HULL_EXT = 2.5D;

    /** 炮塔数（新增炮塔 = 数组 +1 并加一行配置） */
    private static final int TURRET_COUNT = 7;
    private static final EntityDataAccessor<Vector3f>[] DATA_TURRET = new EntityDataAccessor[TURRET_COUNT];
    static {
        for (int i = 0; i < TURRET_COUNT; i++) {
            DATA_TURRET[i] = SynchedEntityData.defineId(Undead_Sky_City_Entity.class, EntityDataSerializers.VECTOR3);
        }
    }

    private final SkyCityPart[] hullParts;
    private final Ship_Turret<Undead_Sky_City_Entity>[] turrets;
    /** 渲染用挂点（由 TURRET_MOUNT 推导）：每行 {local x, local y, local z, baseYaw}，模型空间、相对父类实体 */
    private final float[][] turretRender;
    private final PartEntity<?>[] parts;
    private int targetScanCooldown;

    public Undead_Sky_City_Entity(EntityType<? extends Undead_Sky_City_Entity> type, Level level) {
        super(type, level);
        this.moveControl = new MoveControl(this);
        this.xpReward = 50;
        // 气囊 4 段（±7.5/±2.5，dy +5.5）+ 吊舱首尾 2 段（±2.5，dy +1.5，贴合新模型吊舱 2.5~5.5）
        this.hullParts = new SkyCityPart[] {
                new SkyCityPart(this, GASBAG_SIZE, GASBAG_SIZE, GASBAG_DY, 7.5D),
                new SkyCityPart(this, GASBAG_SIZE, GASBAG_SIZE, GASBAG_DY, 2.5D),
                new SkyCityPart(this, GASBAG_SIZE, GASBAG_SIZE, GASBAG_DY, -2.5D),
                new SkyCityPart(this, GASBAG_SIZE, GASBAG_SIZE, GASBAG_DY, -7.5D),
                new SkyCityPart(this, 4.0F, 3.0F, 1.5D, HULL_EXT),
                new SkyCityPart(this, 4.0F, 3.0F, 1.5D, -HULL_EXT),
        };
        // 炮塔配置（每行一门，顺序与模型一致；改 mount 数值即可，判定箱与模型会同步移动）：
        //   mount(前向偏移, 侧向距离, 垂直偏移, 基准朝向) —— 相对父类实体（格）：
        //     前向偏移 +为舰首方向；侧向距离 +右舷/−左舷（离中轴线的距离）；垂直偏移 +为上；
        //     基准朝向 0=正前、+90=正右舷、−90=正左舷、±45=舷侧斜向（与渲染 BASE_YAW 同值）
        // 其余参数用 Builder 默认值（射界 ±60/60/70°、射程 48、冷却 100 tick、威力 8.0、血量 50），
        // 想自定义可链式追加 .arc().range().cooldown().power().hp()
        this.turrets = new Ship_Turret[] {
                Ship_Turret.of(this, 0, DATA_TURRET[0], this::canTarget).mount(3.5D, 1.0D, 0D, 45.0F).build(),   // 右舷前炮
                Ship_Turret.of(this, 1, DATA_TURRET[1], this::canTarget).mount(0.0D, 2.0D, 0D, 90.0F).build(),    // 右舷中炮
                Ship_Turret.of(this, 2, DATA_TURRET[2], this::canTarget).mount(-3.5D, 1.0D, 0D, 135.0F).build(),  // 右舷后炮
                Ship_Turret.of(this, 3, DATA_TURRET[3], this::canTarget).mount(3.5D, -1.0D, 0D, -45.0F).build(),  // 左舷前炮
                Ship_Turret.of(this, 4, DATA_TURRET[4], this::canTarget).mount(0.0D, -2.0D, 0D, -90.0F).build(),  // 左舷中炮
                Ship_Turret.of(this, 5, DATA_TURRET[5], this::canTarget).mount(-3.5D, -1.0D, 0D, -135.0F).build(), // 左舷后炮
                Ship_Turret.of(this, 6, DATA_TURRET[6], this::canTarget).mount(8.5D, 0.0D, 2.0D, 0.0F).build(),      // 舰艏炮
        };
        // 渲染挂点由上面的 mount 数值自动推导（x=−侧向距离、y=1.501−实体中心−dy、z=−前向偏移、yaw=基准朝向），
        // 保证模型与判定箱始终同位置；实体中心用 getBbHeight() 实算，改实体尺寸也会自动跟随
        this.turretRender = new float[this.turrets.length][4];
        float bbHalf = this.getBbHeight() * 0.5F;
        for (int i = 0; i < this.turrets.length; i++) {
            double[] m = this.turrets[i].mountParams();
            this.turretRender[i] = new float[] {
                    (float) -m[1],
                    1.501F - bbHalf - (float) m[2],
                    (float) -m[0],
                    (float) m[3],
            };
        }
        this.parts = new PartEntity<?>[this.hullParts.length + this.turrets.length];
        int i = 0;
        for (PartEntity<?> part : this.hullParts) {
            this.parts[i++] = part;
        }
        for (Ship_Turret<Undead_Sky_City_Entity> turret : this.turrets) {
            this.parts[i++] = turret.part();
        }
        // 部件 id 必须是本体 id 的连续后继（NeoForge 修 MC-158205 的做法），
        // 否则客户端点击部件时服务端找不到对应部件。
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                .add(Attributes.FLYING_SPEED, 0.3D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        for (EntityDataAccessor<Vector3f> data : DATA_TURRET) {
            builder.define(data, new Vector3f(0.0F, 0.0F, 50.0F));
        }
    }

    /** 给渲染用：第 i 门炮塔状态 (yaw°, pitch°, hp) */
    public Vector3f getTurretState(int index) {
        return this.entityData.get(DATA_TURRET[index]);
    }

    /** 给渲染用：各炮塔模型空间挂点 {local x, local y, local z, baseYaw}（相对父类实体，格；右舷 x 负、舰首 z 负、上 y 负） */
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

    /** 最大速度硬限速（1.5 格/s），覆盖一切速度来源（飞行/环绕/游荡） */
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
        // 高度保持：过低抬升；无目标时过高拉回（防止一直往上漂）
        if (this.tickCount % 5 == 0) {
            double groundY = this.scanGroundY();
            double dh = this.getY() - groundY;
            if (dh < HOVER_MIN || (this.getTarget() == null && dh > HOVER_CEIL)) {
                this.moveControl.setWantedPosition(this.getX(), groundY + 16.0D, this.getZ(), 1.0D);
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
        for (Ship_Turret<Undead_Sky_City_Entity> turret : this.turrets) {
            turret.tick();
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
        for (Ship_Turret<Undead_Sky_City_Entity> turret : this.turrets) {
            if (turret.canHit(target)) {
                return true;
            }
        }
        return false;
    }

    /** 部件跟随本体位置/朝向 */
    private void updateParts() {
        float rad = this.getYRot() * Mth.DEG_TO_RAD;
        Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
        for (SkyCityPart part : this.hullParts) {
            placePart(part, fwd.scale(part.fwdOffset), part.dy);
        }
        for (Ship_Turret<Undead_Sky_City_Entity> turret : this.turrets) {
            turret.placePart();
        }
    }

    private void placePart(PartEntity<?> part, Vec3 offset, double dy) {
        double x = this.getX() + offset.x, z = this.getZ() + offset.z;
        // 实体 getY()=脚底；PartEntity 的 AABB 也以 setPos 为底边向上延伸，
        // 所以先算「实体中心 + dy」再减去部件半高，让箱子中心落在目标点上
        double y = this.getY() + this.getBbHeight() * 0.5D + dy
                - part.getDimensions(part.getPose()).height() * 0.5D - 1.5D;
        part.setPos(x, y, z);
        part.xo = part.xOld = x;
        part.yo = part.yOld = y;
        part.zo = part.zOld = z;
    }

    /** 部件受击：炮塔扣自己的耐久，气囊/吊舱转给本体血量 */
    public boolean hurtPart(PartEntity<?> part, DamageSource source, float amount) {
        for (Ship_Turret<Undead_Sky_City_Entity> turret : this.turrets) {
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
                from, from.subtract(0.0D, 64.0D, 0.0D),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos().getY() + 1.0D : this.getY();
    }

    /** 索敌过滤：玩家 + 怪物（主人拍板：不打动物/村民） */
    private boolean canTarget(LivingEntity entity) {
        if (entity == this || entity.isDeadOrDying() || entity.isSpectator()) {
            return false;
        }
        return entity instanceof Player
                || (entity instanceof Mob mob && mob.getType().getCategory() == MobCategory.MONSTER);
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

    /** 本体碰撞箱可点击：打吊舱中部 = 扣本体血（判定箱在部件上） */
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

    // ===== 音效（借用铁傀儡的金属质感，P6 再打磨）=====
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

    /** 坠毁：沿船身炸出一串爆炸粒子 */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel server) {
            float rad = this.getYRot() * Mth.DEG_TO_RAD;
            Vec3 fwd = new Vec3(-Mth.sin(rad), 0.0D, Mth.cos(rad));
            for (int i = -10; i <= 10; i += 2) {
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
        return this.getBoundingBox().inflate(12.0D);
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
    private static class SkyCityPart extends Airship_Part<Undead_Sky_City_Entity> {

        final double dy, fwdOffset;

        SkyCityPart(Undead_Sky_City_Entity parent, float width, float height, double dy, double fwdOffset) {
            super(parent, width, height, false);
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
        private final Undead_Sky_City_Entity mob;

        FloatAroundGoal(Undead_Sky_City_Entity mob) {
            this.mob = mob;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (this.mob.getTarget() != null || this.mob.getMoveControl().hasWanted()) {
                return false;
            }
            // 游荡高度限制在离地 13~40 格的空域带内（防一路向上漂）
            double ground = this.mob.scanGroundY();
            double x = this.mob.getX() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 24.0F;
            double y = Mth.clamp(this.mob.getY() + (this.mob.getRandom().nextFloat() * 2.0F - 1.0F) * 8.0F,
                    ground + HOVER_MIN + 1.0D, ground + 40.0D);
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
    //  交战：远处逼近目标上空；近处环绕（Hinderburg 同款 Orbit）+ 悬停
    // ============================================================
    private static class EngageGoal extends Goal {
        private final Undead_Sky_City_Entity mob;

        EngageGoal(Undead_Sky_City_Entity mob) {
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
            double hoverY = Math.max(target.getY() + 10.0D, this.mob.scanGroundY() + HOVER_MIN);
            if (this.mob.distanceToSqr(target) > ENGAGE_DIST * ENGAGE_DIST) {
                // 逼近目标上空
                this.mob.moveControl.setWantedPosition(target.getX(), hoverY, target.getZ(), 1.0D);
            } else {
                // 环绕 + 垂直保持（水平速度直接给，垂直交给 moveControl）
                this.mob.moveControl.setWantedPosition(this.mob.getX(), hoverY, this.mob.getZ(), 1.0D);
                Vec3 toTarget = new Vec3(target.getX() - this.mob.getX(), 0.0D, target.getZ() - this.mob.getZ());
                if (toTarget.lengthSqr() > 4.0D) {
                    Vec3 orbit = toTarget.normalize().yRot((float) (Math.PI / 2.0D));
                    Vec3 v = this.mob.getDeltaMovement();
                    this.mob.setDeltaMovement(orbit.x * MAX_SPEED, v.y, orbit.z * MAX_SPEED);
                }
            }
        }
    }
}
