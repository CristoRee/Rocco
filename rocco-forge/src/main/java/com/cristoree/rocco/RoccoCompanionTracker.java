package com.cristoree.rocco;

import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

// Mantiene a Rocco junto a su jugador. Sin esto, al morir el jugador reaparecia lejos (o en
// otra dimension) y Rocco quedaba atras: el goal de seguimiento no lo encontraba y nunca se
// teleportaba.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoCompanionTracker
{
    public static final String TAG_ROCCO_UUID = "rocco_uuid";
    // Donde lo vio por ultima vez y si te venia siguiendo: sirve para ir a buscarlo cuando su
    // zona del mundo no esta cargada (entraste al mundo en otro lado, te tepeaste lejos...).
    public static final String TAG_ROCCO_POS = "rocco_pos";
    public static final String TAG_ROCCO_DIM = "rocco_dim";
    public static final String TAG_ROCCO_FOLLOWING = "rocco_following";

    // Llamadas en curso: se carga el chunk donde estaba hasta que aparece, y se lo trae.
    private record Recall(UUID player, ServerLevel level, net.minecraft.world.level.ChunkPos chunk, int ticksLeft, boolean announce) {}
    private static final java.util.List<Recall> RECALLS = new java.util.ArrayList<>();

    // Forge pide un "validador" para los chunks que dejamos cargados. Al abrir el mundo se
    // sueltan todos: cada Rocco vuelve a pedir el suyo apenas se mueve.
    public static void registerChunkLoading()
    {
        net.minecraftforge.common.world.ForgeChunkManager.setForcedChunkLoadingCallback(RoccoMod.MODID, (level, helper) ->
        {
            helper.getEntityTickets().keySet().forEach(helper::removeAllTickets);
            helper.getBlockTickets().keySet().forEach(helper::removeAllTickets);
        });
    }

    // "Rocco, veni": lo trae aunque este lejisimo o en una zona descargada. true si ya llego.
    public static boolean recall(ServerPlayer player, boolean announce)
    {
        RoccoEntity rocco = find(player);
        if (rocco != null)
        {
            bringToPlayer(player);
            return true;
        }

        CompoundTag data = player.getPersistentData();
        MinecraftServer server = player.getServer();
        if (!data.contains(TAG_ROCCO_POS) || server == null)
        {
            return false;
        }

        ResourceLocationHolder dim = ResourceLocationHolder.of(data.getString(TAG_ROCCO_DIM));
        ServerLevel level = dim == null ? null : server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, dim.location()));
        if (level == null)
        {
            return false;
        }

        BlockPos last = BlockPos.of(data.getLong(TAG_ROCCO_POS));
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(last);
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(level, RoccoMod.MODID, player.getUUID(),
                chunk.x, chunk.z, true, true);
        RECALLS.removeIf(recall -> recall.player().equals(player.getUUID()));
        RECALLS.add(new Recall(player.getUUID(), level, chunk, 200, announce));
        return false;
    }

    // Espera a que el chunk termine de cargar a Rocco y lo trae. Si en 10 segundos no aparece,
    // se rinde y suelta el chunk.
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.ServerTickEvent event)
    {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || RECALLS.isEmpty())
        {
            return;
        }

        MinecraftServer server = event.getServer();
        java.util.List<Recall> next = new java.util.ArrayList<>();
        for (Recall recall : RECALLS)
        {
            ServerPlayer player = server.getPlayerList().getPlayer(recall.player());
            RoccoEntity rocco = player == null ? null : find(player);

            if (player != null && rocco != null)
            {
                bringToPlayer(player);
                if (recall.announce())
                {
                    rocco.sayLine("teleport_arrived");
                }
                release(recall);
            }
            else if (player == null || recall.ticksLeft() <= 0)
            {
                release(recall);
            }
            else
            {
                next.add(new Recall(recall.player(), recall.level(), recall.chunk(), recall.ticksLeft() - 1, recall.announce()));
            }
        }
        RECALLS.clear();
        RECALLS.addAll(next);
    }

    private static void release(Recall recall)
    {
        net.minecraftforge.common.world.ForgeChunkManager.forceChunk(recall.level(), RoccoMod.MODID, recall.player(),
                recall.chunk().x, recall.chunk().z, false, true);
    }

    // Al entrar al mundo: si te venia siguiendo y quedo lejos (o en una zona descargada), lo trae.
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.getBoolean(TAG_ROCCO_FOLLOWING) || !data.contains(TAG_ROCCO_POS))
        {
            return;
        }

        RoccoEntity rocco = find(player);
        double teleport = com.cristoree.rocco.config.RoccoCommonConfig.TELEPORT_DISTANCE.get();
        boolean far = rocco == null || rocco.level() != player.level()
                || (teleport > 0.0D && rocco.distanceToSqr(player) > teleport * teleport);
        if (far && teleport > 0.0D)
        {
            recall(player, false);
        }
    }

    // Lee "minecraft:overworld" sin explotar si el texto vino roto.
    private record ResourceLocationHolder(net.minecraft.resources.ResourceLocation location)
    {
        @Nullable
        static ResourceLocationHolder of(String text)
        {
            net.minecraft.resources.ResourceLocation parsed = net.minecraft.resources.ResourceLocation.tryParse(text);
            return parsed == null ? null : new ResourceLocationHolder(parsed);
        }
    }

    public static void remember(ServerPlayer player, RoccoEntity rocco)
    {
        player.getPersistentData().putUUID(TAG_ROCCO_UUID, rocco.getUUID());
        rocco.setOwnerId(player.getUUID());
    }

    // Anota donde murio el jugador para que Rocco pueda contarselo despues.
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }

        RoccoEntity rocco = find(player);
        if (rocco == null)
        {
            return;
        }

        BlockPos where = player.blockPosition();
        String dimension = player.level().dimension().location().toString();
        long day = player.level().getDayTime() / 24000L;

        rocco.rememberDeath(where, dimension, day);
        RoccoMod.LOGGER.info("[Rocco] {} murio en {} ({})", player.getGameProfile().getName(), where, dimension);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            bringOrRecall(player);
            RoccoEntity rocco = event.isEndConquered() ? null : find(player);
            if (rocco != null)
            {
                rocco.react("react_respawn");
            }
        }
    }

    // Tambien al cruzar un portal: si no, se queda del otro lado.
    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            bringOrRecall(player);
        }
    }

    // Si esta cargado lo trae ya; si su zona se descargo (por ejemplo, moriste lejisimo), carga
    // donde lo vio por ultima vez y lo trae en cuanto aparece. Asi nunca queda perdido del otro lado.
    private static void bringOrRecall(ServerPlayer player)
    {
        if (find(player) != null)
        {
            bringToPlayer(player);
        }
        else if (player.getPersistentData().getBoolean(TAG_ROCCO_FOLLOWING))
        {
            recall(player, false);
        }
    }

    // El UUID se conserva al morir solo si se copia a mano al jugador nuevo.
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        CompoundTag old = event.getOriginal().getPersistentData();
        if (old.hasUUID(TAG_ROCCO_UUID))
        {
            event.getEntity().getPersistentData().putUUID(TAG_ROCCO_UUID, old.getUUID(TAG_ROCCO_UUID));
        }
    }

    public static void bringToPlayer(ServerPlayer player)
    {
        RoccoEntity rocco = find(player);
        if (rocco == null)
        {
            return;
        }

        ServerLevel target = player.serverLevel();
        BlockPos spot = RoccoSpawner.findGround(target, player.blockPosition());

        double x = spot.getX() + 0.5D;
        double y = spot.getY();
        double z = spot.getZ() + 0.5D;

        // El orden importa: cambiar de dimension crea una instancia nueva a partir del NBT,
        // asi que lo que se toque despues del teleport se pierde. Primero se ordena el estado.
        // Si quedo sentado o dormido, traerlo asi pareceria que sigue perdido.
        rocco.standUp();
        rocco.setFollowing(true);
        rocco.stopTravelling();
        rocco.getNavigation().stop();

        if (rocco.level() == target)
        {
            rocco.teleportTo(x, y, z);
        }
        else
        {
            // Conserva inventario, dueño y memoria de muerte: todo eso viaja en el NBT.
            rocco.teleportTo(target, x, y, z, Set.<RelativeMovement>of(), player.getYRot(), 0.0F);
        }

        RoccoMod.LOGGER.info("[Rocco] Traido junto a {} en {}", player.getGameProfile().getName(), spot);
    }

    // Lo busca por UUID en todas las dimensiones, asi lo encuentra este donde este.
    @Nullable
    public static RoccoEntity find(ServerPlayer player)
    {
        CompoundTag data = player.getPersistentData();
        if (!data.hasUUID(TAG_ROCCO_UUID))
        {
            return findNearby(player);
        }

        UUID id = data.getUUID(TAG_ROCCO_UUID);
        MinecraftServer server = player.getServer();
        if (server == null)
        {
            return null;
        }

        for (ServerLevel level : server.getAllLevels())
        {
            Entity found = level.getEntity(id);
            if (found instanceof RoccoEntity rocco && rocco.isAlive())
            {
                return rocco;
            }
        }

        // Guardado viejo o Rocco perdido: se intenta con el que tenga al lado.
        return findNearby(player);
    }

    @Nullable
    private static RoccoEntity findNearby(ServerPlayer player)
    {
        RoccoEntity closest = null;
        double best = Double.MAX_VALUE;

        for (RoccoEntity candidate : player.serverLevel().getEntitiesOfClass(
                RoccoEntity.class, player.getBoundingBox().inflate(64.0D), LivingEntity::isAlive))
        {
            boolean mine = candidate.getOwnerId() == null
                    || candidate.getOwnerId().equals(player.getUUID());

            double distance = candidate.distanceToSqr(player);
            if (mine && distance < best)
            {
                best = distance;
                closest = candidate;
            }
        }

        return closest;
    }

    private RoccoCompanionTracker() {}
}
