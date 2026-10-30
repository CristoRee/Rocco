package com.cristoree.rocco;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.work.Inv;
import com.cristoree.rocco.work.Names;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

// Lo que Rocco junto mientras no estabas. Al desconectarte se anota la hora; al volver, si paso
// al menos una hora, se arma el regalo segun cuanto tiempo estuviste afuera (lo maximo, a la
// semana) y queda guardado en el jugador hasta que Rocco este cerca y libre para avisarte. Lo
// deja en su tapa; solo te tira lo que no le entra.
//
// Que tan bueno es va de 0 (una hora) a 1 (un dia) y a 2 (una semana): crece rapido al
// principio y despues se frena. Las cantidades y las chances de cada cosa se interpolan entre
// esos tres puntos.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoPresents
{
    private static final String TAG_LEFT_AT = "rocco_left_at";
    private static final String TAG_PENDING = "rocco_present";
    private static final String TAG_PENDING_HOURS = "rocco_present_hours";

    private static final double MIN_HOURS = 1.0D;
    private static final double MAX_HOURS = 24.0D * 7.0D;
    private static final double HAND_DISTANCE = 4.5D;

    // De cada tipo, cuantas cosas distintas como mucho: de bichos una sola (si da polvora, es solo
    // polvora), de comida dos.
    private enum Kind { ORE, FOOD, MOB, MAGIC, TOOL }

    private static final Map<Kind, Integer> LIMIT = Map.of(Kind.MOB, 1, Kind.FOOD, 2, Kind.MAGIC, 2, Kind.TOOL, 1);

    // from: desde que nivel puede salir. hour/day/week: cantidad minima y maxima en cada punto.
    // weight: que tan seguido sale en cada punto.
    private record Present(Item item, Kind kind, double from, int[] hour, int[] day, int[] week, int[] weight) {}

    private static final List<Present> POOL = List.of(
            p(Items.COAL, Kind.ORE, 0.0D, 1, 5, 6, 25, 24, 48, 10, 5, 2),
            p(Items.IRON_INGOT, Kind.ORE, 0.0D, 1, 2, 5, 10, 10, 30, 8, 7, 7),
            p(Items.COPPER_INGOT, Kind.ORE, 0.0D, 2, 5, 8, 16, 16, 32, 5, 3, 1),
            p(Items.LAPIS_LAZULI, Kind.ORE, 0.0D, 1, 4, 6, 14, 16, 32, 4, 3, 2),
            p(Items.REDSTONE, Kind.ORE, 0.0D, 2, 6, 8, 20, 24, 48, 4, 3, 2),
            p(Items.AMETHYST_SHARD, Kind.ORE, 0.0D, 1, 3, 4, 10, 10, 20, 2, 2, 1),
            p(Items.GOLD_INGOT, Kind.ORE, 0.3D, 1, 2, 2, 6, 10, 20, 0, 5, 7),
            p(Items.QUARTZ, Kind.ORE, 0.5D, 2, 4, 6, 16, 16, 32, 0, 2, 2),
            p(Items.EMERALD, Kind.ORE, 0.4D, 1, 2, 2, 6, 6, 16, 0, 4, 5),
            p(Items.DIAMOND, Kind.ORE, 0.6D, 0, 1, 1, 4, 8, 15, 0, 5, 8),
            p(Items.NETHERITE_SCRAP, Kind.ORE, 1.2D, 0, 0, 0, 1, 1, 4, 0, 0, 6),

            p(Items.BREAD, Kind.FOOD, 0.0D, 1, 3, 4, 8, 8, 16, 5, 3, 1),
            p(Items.COOKED_BEEF, Kind.FOOD, 0.0D, 1, 3, 4, 8, 10, 20, 4, 3, 2),
            p(Items.BAKED_POTATO, Kind.FOOD, 0.0D, 2, 4, 5, 10, 12, 24, 4, 2, 1),
            p(Items.APPLE, Kind.FOOD, 0.0D, 1, 3, 3, 6, 6, 12, 4, 2, 1),
            p(Items.COOKED_SALMON, Kind.FOOD, 0.0D, 1, 3, 3, 7, 8, 16, 3, 2, 1),
            p(Items.SWEET_BERRIES, Kind.FOOD, 0.0D, 2, 6, 6, 12, 0, 0, 3, 1, 0),
            p(Items.PUMPKIN_PIE, Kind.FOOD, 0.3D, 1, 2, 2, 4, 4, 8, 0, 2, 1),
            p(Items.GOLDEN_CARROT, Kind.FOOD, 0.4D, 1, 2, 2, 6, 8, 16, 0, 3, 4),
            p(Items.GOLDEN_APPLE, Kind.FOOD, 0.6D, 0, 1, 1, 2, 5, 10, 0, 4, 7),

            p(Items.BONE, Kind.MOB, 0.0D, 1, 4, 4, 12, 12, 32, 4, 3, 1),
            p(Items.GUNPOWDER, Kind.MOB, 0.0D, 1, 3, 1, 10, 10, 24, 4, 4, 3),
            p(Items.STRING, Kind.MOB, 0.0D, 1, 4, 4, 12, 12, 32, 4, 3, 1),
            p(Items.SLIME_BALL, Kind.MOB, 0.0D, 1, 2, 2, 8, 8, 20, 2, 2, 2),
            p(Items.ARROW, Kind.MOB, 0.0D, 4, 10, 10, 24, 24, 64, 3, 2, 1),
            p(Items.ENDER_PEARL, Kind.MOB, 0.5D, 1, 1, 1, 4, 4, 12, 0, 4, 4),
            p(Items.BLAZE_ROD, Kind.MOB, 0.7D, 0, 1, 1, 3, 4, 10, 0, 1, 3),
            p(Items.PHANTOM_MEMBRANE, Kind.MOB, 0.7D, 0, 1, 1, 3, 3, 8, 0, 1, 2),
            p(Items.GHAST_TEAR, Kind.MOB, 1.4D, 0, 0, 0, 1, 1, 3, 0, 0, 1),

            p(Items.EXPERIENCE_BOTTLE, Kind.MAGIC, 0.0D, 1, 2, 3, 10, 5, 30, 1, 3, 7),
            p(Items.NAME_TAG, Kind.MAGIC, 0.8D, 1, 1, 1, 1, 1, 2, 0, 1, 2),
            p(Items.ENCHANTED_BOOK, Kind.MAGIC, 1.4D, 1, 1, 1, 1, 1, 1, 0, 0, 4),

            p(Items.TORCH, Kind.TOOL, 0.0D, 4, 12, 16, 32, 32, 64, 3, 2, 1));

    private static Present p(Item item, Kind kind, double from, int hourMin, int hourMax, int dayMin, int dayMax,
                             int weekMin, int weekMax, int weightHour, int weightDay, int weightWeek)
    {
        return new Present(item, kind, from, new int[]{hourMin, hourMax}, new int[]{dayMin, dayMax},
                new int[]{weekMin, weekMax}, new int[]{weightHour, weightDay, weightWeek});
    }

    // ---------- conectarse y desconectarse ----------

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            player.getPersistentData().putLong(TAG_LEFT_AT, System.currentTimeMillis());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        CompoundTag data = player.getPersistentData();
        if (!data.contains(TAG_LEFT_AT))
        {
            return;
        }
        double hours = (System.currentTimeMillis() - data.getLong(TAG_LEFT_AT)) / 3_600_000.0D;
        data.remove(TAG_LEFT_AT);

        // Solo si ya tiene a su Rocco. Si quedo uno sin dar, vale el de la ausencia mas larga.
        if (!RoccoCommonConfig.RETURN_GIFTS_ENABLED.get() || hours < MIN_HOURS
                || !data.hasUUID(RoccoCompanionTracker.TAG_ROCCO_UUID)
                || (data.contains(TAG_PENDING) && data.getDouble(TAG_PENDING_HOURS) >= hours))
        {
            return;
        }

        List<ItemStack> present = roll(Math.min(hours, MAX_HOURS), player.getRandom());
        ListTag list = new ListTag();
        for (ItemStack stack : present)
        {
            list.add(stack.save(new CompoundTag()));
        }
        data.put(TAG_PENDING, list);
        data.putDouble(TAG_PENDING_HOURS, hours);
        RoccoMod.LOGGER.info("[Rocco] {} volvio despues de {} horas: le tocan {}", player.getGameProfile().getName(),
                Math.round(hours * 10.0D) / 10.0D, Names.stacks(present));
    }

    // Si te moris antes de que te lo de, el regalo sigue esperandote.
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event)
    {
        CompoundTag old = event.getOriginal().getPersistentData();
        CompoundTag now = event.getEntity().getPersistentData();
        if (old.contains(TAG_PENDING))
        {
            now.put(TAG_PENDING, old.getList(TAG_PENDING, Tag.TAG_COMPOUND).copy());
            now.putDouble(TAG_PENDING_HOURS, old.getDouble(TAG_PENDING_HOURS));
        }
    }

    // ---------- darselo ----------

    // Una vez por segundo desde Rocco. Si te sigue, se acerca; si no (esperando, cuidando la huerta),
    // te avisa cuando pasas cerca. Lo guarda en su tapa, y solo te tira lo que no le entra.
    public static void tick(RoccoEntity rocco, ServerPlayer owner)
    {
        CompoundTag data = owner.getPersistentData();
        if (!data.contains(TAG_PENDING) || owner.tickCount < 100 || owner.isSpectator() || !owner.isAlive()
                || owner.isSleeping() || !rocco.canHandOver())
        {
            return;
        }

        double distance = rocco.distanceTo(owner);
        if (distance > HAND_DISTANCE)
        {
            if (distance < 24.0D && rocco.isFollowing() && rocco.currentErrand() == null)
            {
                rocco.getNavigation().moveTo(owner, 1.1D);
            }
            return;
        }

        List<ItemStack> present = load(data);
        double hours = data.getDouble(TAG_PENDING_HOURS);
        if (present.isEmpty())
        {
            clear(data);
            return;
        }

        // A su tapa. Lo que no entra, a vos.
        clear(data);
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack stack : present)
        {
            ItemStack left = Inv.insert(rocco.inventory(), stack.copy());
            if (!left.isEmpty())
            {
                overflow.add(left);
            }
        }

        rocco.getNavigation().stop();
        rocco.lookAt(owner, 30.0F, 30.0F);
        rocco.sayLine(hours >= MAX_HOURS ? RoccoExpression.CHEER : RoccoExpression.HAPPY,
                hours >= MAX_HOURS ? "present_week" : hours >= 24.0D ? "present_day" : "present_hours");
        rocco.sayLine(RoccoExpression.HAPPY, "present_items", "items", Names.stacks(present));
        if (!overflow.isEmpty())
        {
            rocco.sayLine(RoccoExpression.SMUG, "present_overflow");
            rocco.showInHand(overflow.get(0));
            rocco.triggerAnim("main", "interact");
            hand(rocco, owner, overflow);
        }
    }

    // Se lo tira a las manos, como el allay. Si quedaste lejos, te lo pone directo en el inventario
    // (o a tus pies si no entra).
    public static void hand(RoccoEntity rocco, ServerPlayer player, List<ItemStack> stacks)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        boolean close = player.level() == level && rocco.distanceTo(player) <= 5.0D;
        Vec3 from = new Vec3(rocco.getX(), rocco.getEyeY() - 0.3D, rocco.getZ());
        Vec3 to = player.position().add(0.0D, 1.0D, 0.0D);
        for (ItemStack stack : stacks)
        {
            if (stack.isEmpty())
            {
                continue;
            }
            if (!close)
            {
                player.getInventory().placeItemBackInInventory(stack.copy());
                continue;
            }
            ItemEntity entity = new ItemEntity(level, from.x, from.y, from.z, stack.copy());
            Vec3 push = to.subtract(from).normalize().scale(0.25D + 0.04D * from.distanceTo(to));
            entity.setDeltaMovement(push.x, push.y + 0.12D, push.z);
            entity.setPickUpDelay(10);
            // Marcado como suyo: asi no lo vuelve a levantar como si se lo hubieras tirado vos.
            entity.setThrower(rocco.getUUID());
            level.addFreshEntity(entity);
        }
        level.playSound(null, rocco.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL,
                0.6F, 0.8F + level.random.nextFloat() * 0.2F);
    }

    private static List<ItemStack> load(CompoundTag data)
    {
        List<ItemStack> stacks = new ArrayList<>();
        for (Tag raw : data.getList(TAG_PENDING, Tag.TAG_COMPOUND))
        {
            ItemStack stack = ItemStack.of((CompoundTag) raw);
            if (!stack.isEmpty())
            {
                stacks.add(stack);
            }
        }
        return stacks;
    }

    private static void clear(CompoundTag data)
    {
        data.remove(TAG_PENDING);
        data.remove(TAG_PENDING_HOURS);
    }

    // ---------- armar el regalo ----------

    // 0 a la hora, 1 al dia, 2 a la semana.
    static double level(double hours)
    {
        if (hours <= 1.0D)
        {
            return 0.0D;
        }
        if (hours <= 24.0D)
        {
            return Math.log(hours) / Math.log(24.0D);
        }
        return Math.min(2.0D, 1.0D + Math.log(hours / 24.0D) / Math.log(7.0D));
    }

    // Entre 1 y 2 cosas distintas a la hora, entre 2 y 4 al dia, entre 4 y 6 a la semana.
    static List<ItemStack> roll(double hours, RandomSource random)
    {
        double level = level(hours);
        int fewest = (int) Math.round(between(level, 1, 2, 4));
        int most = Math.max(fewest, (int) Math.round(between(level, 2, 4, 6)));
        int kinds = fewest + random.nextInt(most - fewest + 1);

        List<Present> open = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        for (Present present : POOL)
        {
            double weight = between(level, present.weight()[0], present.weight()[1], present.weight()[2]);
            if (level >= present.from() && weight > 0.0D && range(present, level)[1] >= 1)
            {
                open.add(present);
                weights.add(weight);
            }
        }

        List<ItemStack> chosen = new ArrayList<>();
        Map<Kind, Integer> taken = new EnumMap<>(Kind.class);
        while (chosen.size() < kinds && !open.isEmpty())
        {
            double total = weights.stream().mapToDouble(Double::doubleValue).sum();
            double roll = random.nextDouble() * total;
            int index = 0;
            while (index < open.size() - 1 && roll >= weights.get(index))
            {
                roll -= weights.get(index);
                index++;
            }
            Present present = open.remove(index);
            weights.remove(index);

            int already = taken.getOrDefault(present.kind(), 0);
            if (already >= LIMIT.getOrDefault(present.kind(), Integer.MAX_VALUE))
            {
                continue;
            }
            taken.put(present.kind(), already + 1);

            int[] range = range(present, level);
            int count = range[0] + random.nextInt(range[1] - range[0] + 1);
            ItemStack stack = make(present.item(), count, random);
            // Las perlas se apilan de a 16: lo que sobra va en otra pila.
            while (stack.getCount() > stack.getMaxStackSize())
            {
                chosen.add(stack.split(stack.getMaxStackSize()));
            }
            chosen.add(stack);
        }
        return chosen;
    }

    private static int[] range(Present present, double level)
    {
        double min = between(level, present.hour()[0], present.day()[0], present.week()[0]);
        double max = between(level, present.hour()[1], present.day()[1], present.week()[1]);
        int high = (int) Math.round(max);
        int low = Math.max(1, Math.min(high, (int) Math.round(min)));
        return new int[]{low, high};
    }

    // Interpola entre el valor de la hora (0), el del dia (1) y el de la semana (2).
    private static double between(double level, double hour, double day, double week)
    {
        return level <= 1.0D ? hour + (day - hour) * level : day + (week - day) * (level - 1.0D);
    }

    private static ItemStack make(Item item, int count, RandomSource random)
    {
        if (item == Items.ENCHANTED_BOOK)
        {
            return EnchantmentHelper.enchantItem(random, new ItemStack(Items.BOOK), 30, false);
        }
        return new ItemStack(item, count);
    }

    private RoccoPresents() {}
}
