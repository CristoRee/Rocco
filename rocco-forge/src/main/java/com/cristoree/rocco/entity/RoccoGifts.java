package com.cristoree.rocco.entity;

import com.cristoree.rocco.ai.OfflineRocco;
import com.cristoree.rocco.ai.personality.Relationship;
import com.cristoree.rocco.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

// Los dos regalos de Rocco (ROCCO-V13), una sola vez por jugador. La Semillita, la primera vez que
// son amigos: con ella se fabrica la primera mochila. La Flor de la Amistad, dos dias de juego
// juntos despues de que el jugador mato a los tres jefes: con ella se fabrica la ultima.
// Si no hay lugar en el inventario no dice nada: reintenta cada 30 segundos.
public final class RoccoGifts
{
    public static final String TAG_SEED = "rocco_gift_seed";
    public static final String TAG_FLOWER = "rocco_gift_flower";
    public static final String TAG_FLOWER_TICKS = "rocco_flower_ticks";

    private static final double SEED_REACH = 6.0D;
    private static final double FLOWER_REACH = 5.0D;
    private static final double TOGETHER = 32.0D;
    private static final int FLOWER_WAIT_TICKS = 48_000;
    private static final int RETRY_TICKS = 20 * 30;
    private static final int LINES = 3;

    // Tick de cada linea de la flor (0,3 s, 4,8 s y 6 s).
    private static final int[] FLOWER_LINE_TICKS = {6, 96, 120};

    private final RoccoEntity rocco;
    private int retryTicks;

    RoccoGifts(RoccoEntity rocco)
    {
        this.rocco = rocco;
    }

    // Una vez por segundo, con el dueño en el mismo mundo.
    void tick(ServerPlayer owner)
    {
        CompoundTag data = owner.getPersistentData();
        boolean near = this.rocco.distanceToSqr(owner) < TOGETHER * TOGETHER;
        boolean flowerDue = false;
        if (!data.getBoolean(TAG_FLOWER) && killedAllBosses(owner))
        {
            int waited = data.getInt(TAG_FLOWER_TICKS);
            if (near && waited < FLOWER_WAIT_TICKS)
            {
                data.putInt(TAG_FLOWER_TICKS, waited + 20);
            }
            flowerDue = waited >= FLOWER_WAIT_TICKS;
        }

        this.retryTicks = Math.max(0, this.retryTicks - 20);
        if (this.retryTicks > 0 || !calm(owner))
        {
            return;
        }
        if (!data.getBoolean(TAG_SEED) && this.rocco.relationship().atLeast(Relationship.FRIEND)
                && this.rocco.distanceToSqr(owner) <= SEED_REACH * SEED_REACH)
        {
            if (hasRoom(owner))
            {
                giveSeed(owner);
            }
            else
            {
                this.retryTicks = RETRY_TICKS;
            }
        }
        else if (flowerDue && this.rocco.level().isDay() && !this.rocco.level().isRaining()
                && this.rocco.distanceToSqr(owner) <= FLOWER_REACH * FLOWER_REACH && owner.onGround()
                && !this.rocco.hostileNearbyWithin(16.0D))
        {
            if (hasRoom(owner))
            {
                giveFlower(owner);
            }
            else
            {
                this.retryTicks = RETRY_TICKS;
            }
        }
    }

    static boolean killedAllBosses(ServerPlayer player)
    {
        return killed(player, EntityType.ENDER_DRAGON) && killed(player, EntityType.WITHER) && killed(player, EntityType.WARDEN);
    }

    private static boolean killed(ServerPlayer player, EntityType<?> type)
    {
        return player.getStats().getValue(Stats.ENTITY_KILLED.get(type)) > 0;
    }

    // Un momento tranquilo: libre, sin pelear, sin que le esten hablando y con el jugador despierto.
    private boolean calm(ServerPlayer owner)
    {
        return this.rocco.isFreeTime() && this.rocco.combatStance() == RoccoCombat.STANCE_NONE
                && !this.rocco.brain().busyTalking() && !owner.isSleeping() && !owner.isFallFlying() && !this.rocco.isInWater();
    }

    private static boolean hasRoom(ServerPlayer player)
    {
        return player.getInventory().getFreeSlot() >= 0;
    }

    private void giveSeed(ServerPlayer owner)
    {
        int line = this.rocco.getRandom().nextInt(LINES);
        RoccoTask task = com.cristoree.rocco.config.RoccoCommonConfig.gardenLid() ? RoccoTask.GIVE_SEED_CHILL : RoccoTask.GIVE_SEED;
        this.rocco.startGift(task, owner, new ItemStack(ModItems.SEMILLITA.get()), 3.0D, () ->
        {
            hand(owner, new ItemStack(ModItems.SEMILLITA.get()));
            owner.getPersistentData().putBoolean(TAG_SEED, true);
            // Las recetas de las mochilas aparecen en el libro.
            ResourceLocation[] recipes = new ResourceLocation[6];
            for (int i = 0; i < recipes.length; i++)
            {
                recipes[i] = new ResourceLocation(com.cristoree.rocco.RoccoMod.MODID, "mochila_" + (i + 1));
            }
            owner.awardRecipesByKey(recipes);
            this.rocco.say(OfflineRocco.lineAt("gift_seed_give", line), null);
        });
        this.rocco.sayDuring(2, OfflineRocco.lineAt("gift_seed_start", line));
    }

    private void giveFlower(ServerPlayer owner)
    {
        int line = this.rocco.getRandom().nextInt(LINES);
        RoccoTask task = com.cristoree.rocco.config.RoccoCommonConfig.gardenLid() ? RoccoTask.GIVE_FLOWER_CHILL : RoccoTask.GIVE_FLOWER;
        this.rocco.startGift(task, owner, new ItemStack(ModItems.FLOR_AMISTAD.get()), 1.8D, () ->
        {
            hand(owner, new ItemStack(ModItems.FLOR_AMISTAD.get()));
            owner.getPersistentData().putBoolean(TAG_FLOWER, true);
        });
        for (int i = 0; i < FLOWER_LINE_TICKS.length; i++)
        {
            this.rocco.sayDuring(FLOWER_LINE_TICKS[i], OfflineRocco.lineAt("gift_flower_" + (i + 1), line));
        }
    }

    // Directo al inventario; si justo se lleno, a sus pies.
    static void hand(ServerPlayer player, ItemStack stack)
    {
        if (!player.getInventory().add(stack))
        {
            player.drop(stack, false);
        }
    }
}
