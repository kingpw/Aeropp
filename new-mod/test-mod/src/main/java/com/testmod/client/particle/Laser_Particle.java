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
 * 激光粒子：沿用原版 Dust 粒子的外观（颜色/大小与激光枪一致），
 * 在客户端 RegisterParticleProvidersEvent 里绑定后使用。
 */
public class Laser_Particle extends DustParticle {

    private static final Vector3f COLOR = new Vector3f(1.0F, 0.3F, 0.0F);
    /** 激光粒子大小（原 0.8，2026-08-30 改大 1.5 倍 → 1.2） */
    private static final float SCALE = 1.2F;
    /** 寿命（tick）：6 = 0.3 秒 */
    private static final int LIFETIME = 6;
    /** 初始大小：构造时 DustParticle 已按 scale 设好 quadSize，记下来供 tick 缩放 */
    private final float initialSize;

    Laser_Particle(ClientLevel level, double x, double y, double z,
                   double xd, double yd, double zd, SpriteSet sprites) {
        super(level, x, y, z, xd, yd, zd, new DustParticleOptions(COLOR, SCALE), sprites);
        this.setLifetime(LIFETIME);
        this.initialSize = this.quadSize;
    }

    /** 发光：粒子不受方块光照，黑夜/阴影/遮挡处也保持全亮（0xF000F0 = LightTexture.FULL_BRIGHT） */
    @Override
    public int getLightColor(float partialTicks) {
        return 0xF000F0;
    }

    /** 激光粒子不断变小直至消失：quadSize 随剩余寿命比例从初始值线性缩到 0 */
    @Override
    public void tick() {
        super.tick();
        this.quadSize = this.initialSize * (1.0F - (float) this.age / (float) this.lifetime);
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
            return new Laser_Particle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
