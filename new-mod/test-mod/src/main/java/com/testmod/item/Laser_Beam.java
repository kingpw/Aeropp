package com.testmod.item;

import com.testmod.Test_Mod;

import java.util.Optional;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * 「发射激光」模块：光束几何 + 服务端伤害 + 命中特效 + 客户端光束粒子。
 * 任何实体（物品/炮塔/怪物）给一个起点和方向就能发射；枪口位置（如玩家持枪手偏移）由调用方算好传进来。
 * 视觉与伤害共用同一个 Beam 对象，保证同一条线、不穿墙。
 */
public final class Laser_Beam {

    private Laser_Beam() {
    }

    private static final int MAX_DISTANCE = 80;   // 射程（格）
    private static final double STEP = 0.1;       // 光束粒子间距
    private static final float DAMAGE = 20.0F;    // 命中伤害
    /**
     * 弹道散布系数：方向向量加 ±SPREAD 的随机偏移再归一化（0 = 完全精准）。
     * 0.006 ≈ 最大偏差约 0.5°，80 格处偏移不到 1 格。想更散就调大。
     */
    private static final double SPREAD = 0.006;
    /**
     * 命中点爆炸的尺寸参数（原版 HugeExplosionParticle：实际尺寸 = 2.0 × (1 − 此值 × 0.5)）。
     * 原版小爆炸 = 1.0（尺寸 1 格）；调大此值爆炸更小（1.8 → 0.2 格），调小更大（0 → 2 格）；必须 < 2。
     * ⚠ count=0 时客户端实际收到的尺寸参数 = 偏移量 × maxSpeed（ClientPacketListener.handleParticleEvent），
     * 所以 sendParticles 最后一个参数必须是 1.0，传 0 会把尺寸乘没。
     */
    private static final double HIT_EXPLOSION_SIZE = 1.5;

    /** 光束参数：start=起点（枪口），dir=方向（单位向量），len=方块截断后的长度，wallHit=撞墙点（没撞为 null） */
    public record Beam(Vec3 start, Vec3 dir, double len, Vec3 wallHit) {
    }

    /**
     * 给方向加随机散布（服务端调用）。⚠ 散布结果必须同一条线地发给客户端画光束
     * （见 Laser_Fired_Payload 携带 dir），别让客户端自己再散布一次，否则视觉和判定分线。
     */
    public static Vec3 spread(Vec3 dir, RandomSource random) {
        return spread(dir, random, SPREAD);
    }

    /** 指定散布幅度的版本（战舰大型激光用较大散布调用） */
    public static Vec3 spread(Vec3 dir, RandomSource random, double amount) {
        return dir.add(
                (random.nextDouble() - 0.5) * amount * 2,
                (random.nextDouble() - 0.5) * amount * 2,
                (random.nextDouble() - 0.5) * amount * 2
        ).normalize();
    }

    /** 算光束：从 from 沿 dir 发射，方块截断 */
    public static Beam computeBeam(Level level, Entity shooter, Vec3 from, Vec3 dir) {
        Vec3 end = from.add(dir.scale(MAX_DISTANCE));
        // COLLIDER：只检测真正的碰撞箱，草/花等无碰撞方块不挡激光（OUTLINE 会被草拦截）；石头/泥土等有碰撞仍会挡
        ClipContext context = new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter);
        HitResult hitResult = level.clip(context);
        double len = MAX_DISTANCE;
        Vec3 wallHit = null;
        if (hitResult.getType() != HitResult.Type.MISS) {
            len = Math.min(MAX_DISTANCE, hitResult.getLocation().distanceTo(from) + 0.5);
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                wallHit = hitResult.getLocation();
            }
        }
        return new Beam(from, dir, len, wallHit);
    }

    /** 服务端：沿光束结算伤害 + 命中点爆炸特效（粒子+音效），伤害用默认 {@link #DAMAGE} */
    public static void fire(ServerLevel level, Entity shooter, Beam beam) {
        fire(level, shooter, beam, DAMAGE);
    }

    /** 服务端：沿光束结算伤害 + 命中点爆炸特效（粒子+音效，广播给附近所有人）；伤害可自定义 */
    public static void fire(ServerLevel level, Entity shooter, Beam beam, float damage) {
        // 开火音效（原版音效复合：舒尔壳能量弹射 + 烟花高频脆响 ≈ 激光"pew"；音量/音调可随意调）
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.4F);
        level.playSound(null, shooter.getX(), shooter.getY(), shooter.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.35F, 2.0F);

        Vec3 end = beam.start().add(beam.dir().scale(beam.len()));
        // 自实现命中检测：level.getEntities 不遍历多部件实体的 PartEntity（天城的白箱/炮塔箱），
        // 手动逐个展开判定箱；最大命中距离 = 激光实际长度（平方比较）
        EntityHitResult entityHit = raycastEntity(level, shooter, beam.start(), end, beam.len() * beam.len());
        if (entityHit != null) {
            DamageSource src = shooter instanceof Player p
                    ? p.damageSources().playerAttack(p)
                    : (shooter instanceof LivingEntity le ? le.damageSources().mobAttack(le) : shooter.damageSources().generic());
            Entity hit = entityHit.getEntity();
            if (hit instanceof PartEntity<?> part) {
                // 命中多部件判定箱：part.hurt 转发给宿主 hurtPart，分部件扣血
                part.hurt(src, damage);
                // 激光无视无敌帧（部件伤害最终落到宿主的 LivingEntity）
                if (part.getParent() instanceof LivingEntity lp) {
                    lp.invulnerableTime = 0;
                }
            } else if (hit instanceof LivingEntity target) {
                target.hurt(src, damage);
                // 激光无视无敌帧：每次命中都实打实掉血
                target.invulnerableTime = 0;
            }
        }
        // 命中点爆发出一个极小的爆炸（命中实体或光束撞墙都触发）：单颗 EXPLOSION，尺寸见 HIT_EXPLOSION_SIZE
        Vec3 hitPos = entityHit != null ? entityHit.getLocation() : beam.wallHit();
        if (hitPos != null) {
            level.sendParticles(ParticleTypes.EXPLOSION, hitPos.x, hitPos.y, hitPos.z, 0, HIT_EXPLOSION_SIZE, 0.0D, 0.0D, 1.0D);
            level.playSound(null, hitPos.x, hitPos.y, hitPos.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
        }
    }

    /** 客户端：画整条光束粒子（自定义激光粒子：寿命 6 tick 快速消散，不留长残影） */
    public static void spawnBeam(Level level, Beam beam) {
        for (double d = 0; d < beam.len(); d += STEP) {
            Vec3 pos = beam.start().add(beam.dir().scale(d));
            // 轻微随机偏移让激光更自然
            level.addParticle(
                    Test_Mod.LASER_PARTICLE.get(),
                    pos.x + (level.random.nextDouble() - 0.5) * 0.1,
                    pos.y + (level.random.nextDouble() - 0.5) * 0.1,
                    pos.z + (level.random.nextDouble() - 0.5) * 0.1,
                    0.0, 0.0, 0.0
            );
        }
    }

    /** 射线实体命中：普通实体 + 多部件实体的判定箱（PartEntity），返回最近命中（超距为 null）。
     *  排除射击者自己的部件（多部件射击者如战舰，防止激光打到自己）。 */
    private static EntityHitResult raycastEntity(Level level, Entity shooter, Vec3 start, Vec3 end, double maxDistSqr) {
        AABB path = new AABB(start, end).inflate(1.0D);
        EntityHitResult best = null;
        double[] bestD = {maxDistSqr};
        for (Entity e : level.getEntities(shooter, path, e2 ->
                e2 != shooter && e2.isPickable() && !e2.isSpectator()
                        && !(e2 instanceof PartEntity<?> pe && pe.getParent() == shooter))) {
            best = closer(best, bestD, start, end, e);
            // 多部件实体：其 PartEntity 判定箱不在常规遍历里，手动逐个展开检测（跳过 shooter 自己的部件）
            if (e.isMultipartEntity()) {
                for (Entity part : e.getParts()) {
                    if (part.isPickable() && part != shooter
                            && !(part instanceof PartEntity<?> pp && pp.getParent() == shooter)) {
                        best = closer(best, bestD, start, end, part);
                    }
                }
            }
        }
        return best;
    }

    /** 比较并更新最近命中（bestD[0] = 当前最近平方距离） */
    private static EntityHitResult closer(EntityHitResult current, double[] bestD, Vec3 start, Vec3 end, Entity e) {
        AABB aabb = e.getBoundingBox().inflate(e.getPickRadius());
        Vec3 v;
        if (aabb.contains(start)) {
            v = start;
        } else {
            Optional<Vec3> hit = aabb.clip(start, end);
            if (hit.isEmpty()) {
                return current;
            }
            v = hit.get();
        }
        double d = start.distanceToSqr(v);
        if (d < bestD[0]) {
            bestD[0] = d;
            return new EntityHitResult(e, v);
        }
        return current;
    }
}
