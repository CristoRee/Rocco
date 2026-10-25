package com.cristoree.rocco.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

// Las cinco particulas del mate y la magia, con el comportamiento que pide la guia de animacion.
public final class RoccoParticles
{
    private static final int FULL_BRIGHT = 15728880;

    // Florcitas que salen de la cabeza mientras toma mate: suben despacio con vaiven y se apagan.
    public static final class BlissFlower extends TextureSheetParticle
    {
        private final float sway;
        private final float startSize;

        BlissFlower(ClientLevel level, double x, double y, double z, SpriteSet sprites)
        {
            super(level, x, y, z);
            pickSprite(sprites);
            this.lifetime = 30 + this.random.nextInt(11);
            this.startSize = 0.12F + this.random.nextFloat() * 0.06F;
            this.quadSize = this.startSize;
            this.sway = this.random.nextFloat() * Mth.TWO_PI;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = 0.0D;
            this.yd = 0.02D;
            this.zd = 0.0D;
            this.roll = this.oRoll = (this.random.nextFloat() - 0.5F) * 0.6F;
        }

        @Override
        public void tick()
        {
            super.tick();
            float life = (float) this.age / this.lifetime;
            this.xd = Mth.sin(this.sway + this.age * 0.25F) * 0.006D;
            this.zd = Mth.cos(this.sway + this.age * 0.25F) * 0.006D;
            this.yd = 0.02D;
            this.oRoll = this.roll;
            this.roll += 0.03F;
            this.quadSize = this.startSize * (1.0F - life * 0.5F);
            this.alpha = life > 0.6F ? 1.0F - (life - 0.6F) / 0.4F : 1.0F;
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // Vapor del mate: sube, se abre y se vuelve transparente.
    public static final class MateSteam extends TextureSheetParticle
    {
        private final SpriteSet sprites;

        MateSteam(ClientLevel level, double x, double y, double z, SpriteSet sprites)
        {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 20 + this.random.nextInt(11);
            this.quadSize = 0.08F;
            this.gravity = -0.01F;
            this.hasPhysics = false;
            this.xd = (this.random.nextFloat() - 0.5F) * 0.01D;
            this.yd = 0.025D;
            this.zd = (this.random.nextFloat() - 0.5F) * 0.01D;
            this.alpha = 0.6F;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick()
        {
            super.tick();
            setSpriteFromAge(this.sprites);
            float life = (float) this.age / this.lifetime;
            this.quadSize = 0.08F + life * 0.12F;
            this.alpha = 0.6F * (1.0F - life);
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // Runas que salen de las manos hacia adelante en espiral, brillando, y se desvanecen.
    public static final class MagicRune extends TextureSheetParticle
    {
        private final double baseX;
        private final double baseZ;
        private final float phase;

        MagicRune(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites)
        {
            super(level, x, y, z);
            pickSprite(sprites);
            this.lifetime = 20;
            this.quadSize = 0.1F + this.random.nextFloat() * 0.04F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.baseX = dx;
            this.baseZ = dz;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.roll = this.oRoll = this.random.nextFloat() * Mth.TWO_PI;
        }

        @Override
        public void tick()
        {
            super.tick();
            float angle = this.phase + this.age * 0.6F;
            // Hacia adelante, girando alrededor de esa direccion.
            this.xd = this.baseX + Mth.cos(angle) * 0.02D;
            this.zd = this.baseZ + Mth.sin(angle) * 0.02D;
            this.yd = Mth.sin(angle * 0.7F) * 0.01D;
            this.oRoll = this.roll;
            this.roll += 0.15F;
            float life = (float) this.age / this.lifetime;
            this.alpha = 1.0F - life;
        }

        @Override
        protected int getLightColor(float partialTick)
        {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // Destellos: duran un pestañeo, de grandes a un punto.
    public static final class MagicSpark extends TextureSheetParticle
    {
        private final SpriteSet sprites;

        MagicSpark(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites)
        {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 8 + this.random.nextInt(5);
            this.quadSize = 0.07F + this.random.nextFloat() * 0.05F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = dx + (this.random.nextFloat() - 0.5F) * 0.03D;
            this.yd = dy + (this.random.nextFloat() - 0.5F) * 0.03D;
            this.zd = dz + (this.random.nextFloat() - 0.5F) * 0.03D;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick()
        {
            super.tick();
            setSpriteFromAge(this.sprites);
            this.xd *= 0.85D;
            this.yd *= 0.85D;
            this.zd *= 0.85D;
        }

        @Override
        protected int getLightColor(float partialTick)
        {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // El anillo de magia del final del encantamiento: crece y se apaga.
    public static final class MagicBurst extends TextureSheetParticle
    {
        private final SpriteSet sprites;

        MagicBurst(ClientLevel level, double x, double y, double z, SpriteSet sprites)
        {
            super(level, x, y, z);
            this.sprites = sprites;
            this.lifetime = 8;
            this.quadSize = 0.25F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = 0.0D;
            this.yd = 0.0D;
            this.zd = 0.0D;
            setSpriteFromAge(sprites);
        }

        @Override
        public void tick()
        {
            super.tick();
            setSpriteFromAge(this.sprites);
            float life = (float) this.age / this.lifetime;
            this.quadSize = 0.25F + life * 0.5F;
            this.alpha = 1.0F - life * 0.8F;
        }

        @Override
        protected int getLightColor(float partialTick)
        {
            return FULL_BRIGHT;
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ---------- fabricas que registra el cliente ----------

    public record BlissFlowerProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new BlissFlower(level, x, y, z, this.sprites);
        }
    }

    public record MateSteamProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new MateSteam(level, x, y, z, this.sprites);
        }
    }

    public record MagicRuneProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new MagicRune(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }

    public record MagicSparkProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new MagicSpark(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }

    public record MagicBurstProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new MagicBurst(level, x, y, z, this.sprites);
        }
    }

    private RoccoParticles() {}
}
