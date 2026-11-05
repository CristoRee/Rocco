package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.registry.ModParticles;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

// El eructo que se ve: 1,2 segundos de viento y hojas saliendo de la boca en un cono, la nube del
// principio, hojas que se arrancan de los arboles que estan en el cono, y un temblor de camara
// chiquito para el que eructa.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID, value = Dist.CLIENT)
public final class RoccoBurpFx
{
    private static final int LIFE_TICKS = 24;
    private static final int SHAKE_TICKS = 12;
    private static final double RANGE = 6.0D;
    private static final double CONE = Math.tan(Math.toRadians(25.0D));

    private static final class Burp
    {
        final int entityId;
        final Vec3 look;
        int age;

        Burp(int entityId, Vec3 look)
        {
            this.entityId = entityId;
            this.look = look;
        }
    }

    private static final List<Burp> ACTIVE = new ArrayList<>();
    private static int shakeTicks;

    public static void start(RoccoNetwork.BurpFx msg)
    {
        Minecraft minecraft = Minecraft.getInstance();
        ACTIVE.add(new Burp(msg.entityId(), new Vec3(msg.x(), msg.y(), msg.z())));
        if (minecraft.player != null && minecraft.player.getId() == msg.entityId())
        {
            shakeTicks = SHAKE_TICKS;
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.isPaused())
        {
            if (level == null)
            {
                ACTIVE.clear();
                shakeTicks = 0;
            }
            return;
        }
        if (shakeTicks > 0)
        {
            shakeTicks--;
        }
        ACTIVE.removeIf(burp ->
        {
            Entity entity = level.getEntity(burp.entityId);
            if (entity == null || burp.age++ >= LIFE_TICKS)
            {
                return true;
            }
            emit(level, entity, burp);
            return false;
        });
    }

    private static void emit(ClientLevel level, Entity entity, Burp burp)
    {
        RandomSource random = level.random;
        Vec3 mouth = entity.getEyePosition().add(0.0D, -0.15D, 0.0D).add(burp.look.scale(0.4D));
        if (burp.age == 1)
        {
            for (int i = 0; i < 3; i++)
            {
                add(level, ModParticles.BURP_PUFF.get(), mouth, cone(random, burp.look).scale(0.15D));
            }
            leavesInCone(level, entity, burp, random);
        }
        for (int i = 0; i < 6; i++)
        {
            add(level, ModParticles.BURP_WIND.get(), mouth, cone(random, burp.look).scale(0.8D + random.nextDouble() * 0.3D));
        }
        for (int i = 0; i < 2; i++)
        {
            add(level, ModParticles.BURP_LEAF.get(), mouth, cone(random, burp.look).scale(0.5D + random.nextDouble() * 0.4D));
        }
    }

    // Solo particulas: no se rompe nada.
    private static void leavesInCone(ClientLevel level, Entity entity, Burp burp, RandomSource random)
    {
        BlockPos center = entity.blockPosition();
        int radius = (int) RANGE;
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius)))
        {
            Vec3 to = Vec3.atCenterOf(pos).subtract(entity.getEyePosition());
            double distance = to.length();
            if (distance < 1.0D || distance > RANGE || burp.look.dot(to.normalize()) < 0.75D || !level.getBlockState(pos).is(BlockTags.LEAVES))
            {
                continue;
            }
            add(level, ModParticles.BURP_LEAF.get(), Vec3.atCenterOf(pos), cone(random, burp.look).scale(0.3D + random.nextDouble() * 0.3D));
            if (++found >= 24)
            {
                return;
            }
        }
    }

    private static Vec3 cone(RandomSource random, Vec3 look)
    {
        Vec3 out = look.add((random.nextDouble() - 0.5D) * 2.0D * CONE, (random.nextDouble() - 0.5D) * 2.0D * CONE,
                (random.nextDouble() - 0.5D) * 2.0D * CONE);
        return out.normalize();
    }

    private static void add(ClientLevel level, ParticleOptions particle, Vec3 at, Vec3 velocity)
    {
        level.addParticle(particle, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event)
    {
        if (shakeTicks <= 0)
        {
            return;
        }
        float strength = shakeTicks / (float) SHAKE_TICKS;
        float time = (shakeTicks - (float) event.getPartialTick()) * 1.7F;
        event.setRoll(event.getRoll() + Mth.sin(time) * 1.5F * strength);
        event.setPitch(event.getPitch() + Mth.cos(time * 1.3F) * 1.0F * strength);
    }

    private RoccoBurpFx() {}
}
