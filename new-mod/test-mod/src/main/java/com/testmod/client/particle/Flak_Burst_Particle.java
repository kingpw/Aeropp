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
 * 高射炮弹破片火花：爆炸时向四周迸射的金黄火花。
 * 发光（全亮）、短寿命、带重力下坠、不断缩小；数量克制，只在爆炸瞬间产生。
 */
public class Flak_Burst_Particle extends DustParticle {

    private static final Vector3f COLOR = new Vector3f(1.0F, 0.65F, 0.1F);
    private static final float SCALE = 0.3F;
    /** 寿命（tick）：16 ≈ 0.8 秒，破片较明显又快速散去 */
    private static final int LIFETIME = 16;
    /** 破片重力下坠（格/tick²），像碎屑自然下落 */
    private static final double GRAVITY = 0.06;
    private final float initialSize;

    Flak_Burst_Particle(ClientLevel level, double x, double y, double z,
                        double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, new DustParticleOptions(COLOR, SCALE), sprites);
        // 显式锁定火花大小：DustParticle 默认按 scale×0.1 算，太小看不清；这里直接给想要的大小
        this.quadSize = SCALE;
        this.setLifetime(LIFETIME);
        this.initialSize = this.quadSize;
    }

    @Override
    public void tick() {
        // 破片下坠（在基类 move 前生效）
        this.yd -= GRAVITY;
        super.tick();
        // 火花不断变小直至消失
        this.quadSize = this.initialSize * (1.0F - (float) this.age / (float) this.lifetime);
    }

    /** 发光：破片不受方块光照，黑夜/阴影里也全亮 */
    @Override
    public int getLightColor(float partialTicks) {
        return 0xF000F0;
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
            return new Flak_Burst_Particle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
