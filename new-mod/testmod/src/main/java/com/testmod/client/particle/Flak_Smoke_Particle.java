package com.testmod.client.particle;

import org.joml.Vector3f;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.DustParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 高射炮弹黑色烟雾：爆炸残留的黑色烟，缓升缓散、渐透明、约 1s（20 tick）消散。
 * 颜色黑色，数量克制，只在爆炸瞬间产生。
 */
public class Flak_Smoke_Particle extends DustParticle {

    private static final Vector3f COLOR = new Vector3f(0.07F, 0.07F, 0.08F);
    private static final float SCALE = 0.4F;
    /** 基础寿命 40 tick = 2 秒 */
    private static final int BASE_LIFETIME = 40;
    /** 快速膨胀期（tick）：前几 tick 快速变大，之后保持 */
    private static final int GROW_TICKS = 4;
    /** 烟团目标大小倍数（相对初始）：快速变大到这个值后保持 */
    private static final float TARGET_MULT = 3F;
    private final float initialSize;

    Flak_Smoke_Particle(ClientLevel level, double x, double y, double z,
                        double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, new DustParticleOptions(COLOR, SCALE), sprites);
        // 显式锁定初始烟团大小（DustParticle 默认缩放太小）
        this.quadSize = SCALE;
        // 寿命 = 2s + 0~0.5s（0~10 tick）随机波动，让烟自然错开消散
        this.setLifetime(BASE_LIFETIME + this.level.random.nextInt(21));
        this.initialSize = this.quadSize;
    }

    @Override
    public void tick() {
        // 无上升：轻微水平阻尼让粒子缓慢向四周扩散
        this.xd *= 0.95F;
        this.zd *= 0.95F;
        super.tick();
        float t = (float) this.age / (float) this.lifetime;
        // 烟团前几 tick 快速变大到目标值后保持，同时渐透明消散
        float grow = Math.min(1.0F, (float) this.age / (float) GROW_TICKS);
        this.quadSize = this.initialSize * (1.0F + (TARGET_MULT - 1.0F) * grow);
        this.alpha = Math.max(0.0F, 1.0F - t);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType options, ClientLevel level,
                                       double x, double y, double z,
                                       double xd, double yd, double zd) {
            return new Flak_Smoke_Particle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
