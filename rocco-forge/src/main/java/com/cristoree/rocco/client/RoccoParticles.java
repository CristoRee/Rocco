package com.cristoree.rocco.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

// Las particulas de Rocco, con el comportamiento que pide la guia de animacion de cada una.
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

    // ---------- las del vuelo, el agua, el barco y los festejos ----------

    // Como se comporta cada una: casi todas son variaciones de lo mismo (sube o cae, crece o se
    // achica, se desvanece al final), asi que comparten una sola clase con estos ajustes.
    // fadeFrom: desde que parte de la vida se empieza a desvanecer (0 = desde el principio).
    // grow: cuanto cambia el tamaño a lo largo de la vida (1 = igual, 2 = el doble al final).
    public record Look(int minLife, int maxLife, float size, float grow, float gravity, boolean byAge,
                       float fadeFrom, boolean bright, float spin, float sway, double drag)
    {
        public static final Look LEAF_SPARKLE = new Look(15, 15, 0.09F, 0.7F, 0.0F, true, 0.4F, false, 0.0F, 0.0F, 0.96D);
        public static final Look LEAF_BURST = new Look(20, 30, 0.1F, 0.9F, 0.035F, false, 0.7F, false, 0.25F, 0.0F, 0.94D);
        public static final Look SPEED_LEAF = new Look(8, 12, 0.11F, 0.8F, 0.0F, false, 0.5F, false, 0.1F, 0.0F, 0.9D);
        public static final Look WIND_STREAK = new Look(6, 10, 0.22F, 1.0F, 0.0F, true, 0.5F, false, 0.0F, 0.0F, 0.92D);
        public static final Look WIND_PUFF = new Look(10, 10, 0.12F, 2.2F, 0.0F, true, 0.3F, false, 0.0F, 0.0F, 0.85D);
        public static final Look WIND_TEAR = new Look(8, 8, 0.06F, 0.8F, 0.0F, false, 0.5F, false, 0.0F, 0.0F, 0.9D);
        public static final Look LAND_DUST = new Look(12, 16, 0.12F, 1.8F, -0.002F, true, 0.4F, false, 0.0F, 0.0F, 0.86D);
        public static final Look DUCK_POP = new Look(8, 8, 0.1F, 0.6F, 0.0F, true, 0.5F, true, 0.0F, 0.0F, 0.82D);
        public static final Look WATER_DROP = new Look(15, 15, 0.07F, 1.0F, 0.06F, false, 0.7F, false, 0.0F, 0.0F, 0.98D);
        public static final Look THINK_DOT = new Look(30, 30, 0.09F, 1.6F, 0.0F, true, 0.75F, false, 0.0F, 0.0F, 1.0D);
        public static final Look CLAP_SPARK = new Look(8, 8, 0.08F, 0.7F, 0.0F, true, 0.4F, true, 0.0F, 0.0F, 0.85D);
        public static final Look CONFETTI = new Look(40, 40, 0.08F, 1.0F, 0.04F, false, 0.75F, false, 0.3F, 0.0F, 0.95D);
        public static final Look MUSIC_NOTE = new Look(30, 30, 0.12F, 1.0F, 0.0F, false, 0.73F, false, 0.0F, 0.02F, 1.0D);
        public static final Look DANCE_SPARKLE = new Look(12, 12, 0.08F, 0.8F, 0.0F, true, 0.5F, true, 0.0F, 0.0F, 0.8D);
        public static final Look PETAL = new Look(30, 30, 0.07F, 1.0F, 0.01F, false, 0.7F, false, 0.08F, 0.03F, 0.97D);
    }

    public static final class Tuned extends TextureSheetParticle
    {
        private final SpriteSet sprites;
        private final Look look;
        private final float startSize;
        private final float phase;

        Tuned(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, Look look)
        {
            super(level, x, y, z);
            this.sprites = sprites;
            this.look = look;
            this.lifetime = look.minLife() + (look.maxLife() > look.minLife() ? this.random.nextInt(look.maxLife() - look.minLife() + 1) : 0);
            this.startSize = look.size() * (0.85F + this.random.nextFloat() * 0.3F);
            this.quadSize = this.startSize;
            this.gravity = look.gravity();
            this.friction = (float) look.drag();
            this.hasPhysics = false;
            this.xd = dx;
            this.yd = dy;
            this.zd = dz;
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.roll = this.oRoll = look.spin() > 0.0F ? this.random.nextFloat() * Mth.TWO_PI : 0.0F;
            if (look.byAge())
            {
                setSpriteFromAge(sprites);
            }
            else
            {
                pickSprite(sprites);
            }
        }

        @Override
        public void tick()
        {
            super.tick();
            if (this.look.byAge())
            {
                setSpriteFromAge(this.sprites);
            }
            float life = Math.min(1.0F, (float) this.age / this.lifetime);
            this.quadSize = this.startSize * (1.0F + (this.look.grow() - 1.0F) * life);
            if (this.look.sway() > 0.0F)
            {
                this.xd += Mth.sin(this.phase + this.age * 0.35F) * this.look.sway() * 0.1D;
            }
            this.oRoll = this.roll;
            this.roll += this.look.spin();
            this.alpha = life > this.look.fadeFrom() ? 1.0F - (life - this.look.fadeFrom()) / (1.0F - this.look.fadeFrom()) : 1.0F;
        }

        @Override
        protected int getLightColor(float partialTick)
        {
            return this.look.bright() ? FULL_BRIGHT : super.getLightColor(partialTick);
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    public record TunedProvider(SpriteSet sprites, Look look) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new Tuned(level, x, y, z, dx, dy, dz, this.sprites, this.look);
        }
    }

    // Mariposita del jardincito: un color al azar (dos cuadros cada uno) que aletea cada 3 ticks,
    // sube despacio dando vueltas suaves y se desvanece al final.
    public static final class Butterfly extends TextureSheetParticle
    {
        private final SpriteSet sprites;
        private final int color;
        private final float phase;

        Butterfly(ClientLevel level, double x, double y, double z, SpriteSet sprites)
        {
            super(level, x, y, z);
            this.sprites = sprites;
            this.color = this.random.nextInt(3);
            this.phase = this.random.nextFloat() * Mth.TWO_PI;
            this.lifetime = 60 + this.random.nextInt(21);
            this.quadSize = 0.09F;
            this.gravity = 0.0F;
            this.hasPhysics = false;
            this.xd = 0.0D;
            this.yd = 0.015D;
            this.zd = 0.0D;
            setSprite(sprites.get(this.color * 2, 5));
        }

        @Override
        public void tick()
        {
            super.tick();
            float life = (float) this.age / this.lifetime;
            this.xd = Mth.sin(this.phase + this.age * 0.15F) * 0.02D;
            this.zd = Mth.cos(this.phase + this.age * 0.15F) * 0.02D;
            this.yd = 0.015D;
            setSprite(this.sprites.get(this.color * 2 + (this.age / 3) % 2, 5));
            this.alpha = life > 0.75F ? 1.0F - (life - 0.75F) / 0.25F : 1.0F;
        }

        @Override
        public ParticleRenderType getRenderType()
        {
            return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
        }
    }

    // ---------- fabricas que registra el cliente ----------

    public record ButterflyProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType>
    {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz)
        {
            return new Butterfly(level, x, y, z, this.sprites);
        }
    }

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
