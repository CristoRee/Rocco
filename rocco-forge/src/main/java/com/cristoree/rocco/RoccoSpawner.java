package com.cristoree.rocco;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Rocco aparece dormido como planta la primera vez que el jugador entra al mundo.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoSpawner
{
    private static final String TAG_SPAWNED = "rocco_spawned";

    private static final int[][] OFFSETS = {
            {2, 0}, {0, 2}, {-2, 0}, {0, -2},
            {3, 2}, {-3, 2}, {3, -2}, {-3, -2}
    };

    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        if (!RoccoCommonConfig.ENABLED.get())
        {
            return;
        }

        CompoundTag data = player.getPersistentData();
        if (data.getBoolean(TAG_SPAWNED))
        {
            return;
        }

        if (!(player.level() instanceof ServerLevel level))
        {
            return;
        }

        if (spawnPlant(level, player))
        {
            data.putBoolean(TAG_SPAWNED, true);
        }
    }

    // getPersistentData no sobrevive a la muerte por si solo; sin esto Rocco volveria a brotar.
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event)
    {
        if (event.getOriginal().getPersistentData().getBoolean(TAG_SPAWNED))
        {
            event.getEntity().getPersistentData().putBoolean(TAG_SPAWNED, true);
        }
    }

    private static boolean spawnPlant(ServerLevel level, ServerPlayer player)
    {
        RoccoEntity rocco = ModEntities.ROCCO.get().create(level);
        if (rocco == null)
        {
            return false;
        }

        BlockPos spot = findGround(level, player.blockPosition());

        rocco.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        rocco.setRoccoState(RoccoState.PLANT);
        rocco.setYBodyRot(player.getYRot() + 180.0F);

        level.addFreshEntity(rocco);

        // Queda anotado de quien es, para poder encontrarlo despues aunque cambie de dimension.
        RoccoCompanionTracker.remember(player, rocco);

        RoccoMod.LOGGER.info("[Rocco] Brotado en {} para {}", spot, player.getGameProfile().getName());
        return true;
    }

    public static BlockPos findGround(ServerLevel level, BlockPos origin)
    {
        for (int[] offset : OFFSETS)
        {
            BlockPos column = origin.offset(offset[0], 0, offset[1]);
            for (int dy = 2; dy >= -4; dy--)
            {
                BlockPos candidate = column.offset(0, dy, 0);
                BlockPos below = candidate.below();

                boolean standable = level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
                boolean room = level.getBlockState(candidate).isAir()
                        && level.getBlockState(candidate.above()).isAir();

                if (standable && room)
                {
                    return candidate;
                }
            }
        }

        return origin;
    }

    private RoccoSpawner() {}
}
