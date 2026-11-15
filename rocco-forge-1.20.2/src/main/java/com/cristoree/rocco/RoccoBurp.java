package com.cristoree.rocco;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModParticles;
import com.cristoree.rocco.registry.ModSounds;
import com.cristoree.rocco.voice.RoccoNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Tomar una Roco Cola (ROCCO-V13): refresca y, a los 2,5 segundos, sale un eructo gigante que empuja
// a los mobs que tenes adelante (sin hacerles daño). El viento y las hojas los dibuja cada cliente
// (RoccoBurpFx). Si Rocco anda cerca, reacciona.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoBurp
{
    private static final String TAG_BURP_AT = "rocco_burp_at";
    private static final int DELAY_TICKS = 50;
    private static final double RANGE = 6.0D;
    private static final double CONE_COS = Math.cos(Math.toRadians(40.0D));
    private static final double ROCCO_HEARS = 10.0D;

    private RoccoBurp() {}

    public static void drank(ServerPlayer player)
    {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.COLA_REFRESH.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        level.sendParticles(ModParticles.REFRESH_SPARKLE.get(), player.getX(), player.getY() + 1.0D, player.getZ(), 14, 0.4D, 0.5D, 0.4D, 0.02D);
        player.getPersistentData().putLong(TAG_BURP_AT, level.getGameTime() + DELAY_TICKS);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (data.contains(TAG_BURP_AT) && player.level().getGameTime() >= data.getLong(TAG_BURP_AT))
        {
            data.remove(TAG_BURP_AT);
            if (player.isAlive())
            {
                burp(player);
            }
        }
    }

    private static void burp(ServerPlayer player)
    {
        ServerLevel level = player.serverLevel();
        Vec3 look = player.getLookAngle();
        Vec3 ahead = player.getEyePosition().add(look.scale(2.0D));
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.PLAYER_BURP.get(), SoundSource.PLAYERS, 1.6F,
                0.95F + level.random.nextFloat() * 0.1F);
        level.playSound(null, ahead.x, ahead.y, ahead.z, ModSounds.BURP_GUST.get(), SoundSource.PLAYERS, 1.2F, 1.0F);

        for (LivingEntity mob : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                other -> other != player && other.isAlive() && !(other instanceof RoccoEntity) && !other.isSpectator()))
        {
            Vec3 to = mob.getBoundingBox().getCenter().subtract(player.getEyePosition());
            double distance = to.length();
            if (distance > RANGE || distance < 1.0E-3D || look.dot(to.normalize()) <= CONE_COS)
            {
                continue;
            }
            double force = (0.5D + 1.4D * (1.0D - distance / RANGE)) * (1.0D - Mth.clamp(mob.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D));
            mob.push(look.x * force, 0.3D + 0.2D * force, look.z * force);
            mob.hurtMarked = true;
        }

        for (RoccoEntity rocco : level.getEntitiesOfClass(RoccoEntity.class, player.getBoundingBox().inflate(ROCCO_HEARS), RoccoEntity::isAlive))
        {
            Vec3 to = rocco.getBoundingBox().getCenter().subtract(player.getEyePosition());
            rocco.reactToBurp(player, to.length() <= RANGE && to.lengthSqr() > 1.0E-6D && look.dot(to.normalize()) > CONE_COS, look);
        }

        RoccoNetwork.sendBurp(player, new RoccoNetwork.BurpFx(player.getId(), (float) look.x, (float) look.y, (float) look.z));
    }
}
