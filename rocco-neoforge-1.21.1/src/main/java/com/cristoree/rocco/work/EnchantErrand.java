package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// El poder que le dio la Madre Naturaleza: encantar algo tuyo (el pico, la espada, la armadura,
// un libro). Le cuesta mucho, asi que solo puede una vez cada tanto (magic.enchantCooldownMinutes)
// y despues se sienta a descansar. No te gasta experiencia ni lapislazuli. Dos formas:
//  - Al azar ("encantame este pico"): como una mesa de encantamientos al maximo, salen varios
//    encantamientos sorpresa. Solo con cosas sin encantar, igual que la mesa.
//  - A pedido ("encantame el pico con eficiencia 5"): le pone ESE encantamiento y nada mas.
public final class EnchantErrand extends Errand
{
    private enum Phase { CHECK, WALK, WAITING, DONE }

    private static final double REACH = 3.0D;

    @Nullable
    private final Names.Query query;
    private final String hint;
    @Nullable
    private final Holder<Enchantment> wanted;
    private final int wantedLevel;

    private Phase phase = Phase.CHECK;
    private int slot = -1;
    private int targetLevel;
    private ItemStack expected = ItemStack.EMPTY;
    private int walkTicks;
    private boolean applied;
    private String result = "";

    public EnchantErrand(RoccoEntity rocco, @Nullable Player requester, @Nullable Names.Query query, String hint,
                         @Nullable Holder<Enchantment> wanted, int wantedLevel)
    {
        super(rocco, requester);
        this.query = query != null && query.found() ? query : null;
        this.hint = hint == null ? "" : hint;
        this.wanted = wanted;
        this.wantedLevel = wantedLevel;
    }

    @Override
    protected Status step()
    {
        ServerPlayer player = requester();
        if (player == null || player.level() != this.level)
        {
            say("give_no_player");
            return Status.FAILED;
        }

        switch (this.phase)
        {
            case CHECK ->
            {
                long left = this.rocco.enchantCooldownLeft();
                if (left > 0)
                {
                    say("enchant_tired", "time", minutes(left));
                    return Status.FAILED;
                }

                this.slot = findTarget(player);
                if (this.slot < 0)
                {
                    say(this.query == null ? "enchant_nothing" : "enchant_dont_see", "item",
                            this.query == null ? "" : Names.name(this.query.first()));
                    return Status.FAILED;
                }

                ItemStack stack = player.getInventory().getItem(this.slot);
                String refusal = this.wanted != null ? checkSpecific(stack) : checkRandom(stack);
                if (refusal != null)
                {
                    return Status.FAILED;
                }
                this.expected = stack.copy();
                this.phase = Phase.WALK;
                return Status.RUNNING;
            }
            case WALK ->
            {
                double distance = this.rocco.distanceTo(player);
                if (distance <= REACH || (this.walkTicks > 200 && distance < 6.0D))
                {
                    this.rocco.getNavigation().stop();
                    say("enchant_start", "item", Names.name(this.expected));
                    work(new RoccoEntity.Job(RoccoTask.ENCHANTING, player.blockPosition(), ItemStack.EMPTY, player.getId(),
                            level -> apply(player), null, 0, 10.0D));
                    this.phase = Phase.WAITING;
                    return Status.RUNNING;
                }
                if (this.walkTicks > 400)
                {
                    say("give_cant_reach");
                    return Status.FAILED;
                }
                if (this.walkTicks++ % 10 == 0)
                {
                    this.rocco.getNavigation().moveTo(player, 1.1D);
                }
                return Status.RUNNING;
            }
            case WAITING ->
            {
                this.phase = Phase.DONE;
                boolean interrupted = jobFailed();
                if (this.applied)
                {
                    say("enchant_done", "item", Names.name(this.expected), "enchants", this.result);
                    this.rocco.restAfterMagic();
                    return Status.DONE;
                }
                say(interrupted ? "enchant_interrupted" : "enchant_lost");
                return Status.FAILED;
            }
            default ->
            {
                return Status.DONE;
            }
        }
    }

    // ---------- antes de empezar: se puede? ----------

    // Al azar, como la mesa: solo cosas sin encantar (o libros).
    @Nullable
    private String checkRandom(ItemStack stack)
    {
        if (stack.is(Items.BOOK))
        {
            return null;
        }
        if (stack.isEnchanted() || stack.is(Items.ENCHANTED_BOOK))
        {
            say("enchant_already_enchanted", "item", Names.name(stack));
            return "encantado";
        }
        if (!stack.isEnchantable() || stack.getEnchantmentValue() <= 0)
        {
            say("enchant_cant", "item", Names.name(stack));
            return "no se encanta";
        }
        return null;
    }

    // A pedido: que el encantamiento vaya con ese item, que no choque con lo que ya tiene y que
    // no sea uno prohibido.
    @Nullable
    private String checkSpecific(ItemStack stack)
    {
        Holder<Enchantment> enchantment = this.wanted;
        String name = Ench.name(enchantment, 1).replaceAll("\\s+[IVX]+$", "");

        if (enchantment.is(EnchantmentTags.CURSE))
        {
            say("enchant_no_curses");
            return "maldicion";
        }
        if (enchantment.is(EnchantmentTags.TREASURE) && !RoccoCommonConfig.ENCHANT_TREASURE.get())
        {
            say("enchant_treasure", "enchant", name);
            return "tesoro";
        }

        boolean book = stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
        if (!book && !enchantment.value().canEnchant(stack))
        {
            say("enchant_wrong_item", "enchant", name, "item", Names.name(stack));
            return "no va";
        }

        int max = enchantment.value().getMaxLevel();
        this.targetLevel = this.wantedLevel <= 0 ? max : Math.min(this.wantedLevel, max);
        if (this.wantedLevel > max)
        {
            say("enchant_level_capped", "enchant", name, "level", roman(max));
        }

        net.minecraft.world.item.enchantment.ItemEnchantments current = Ench.of(stack);
        int has = current.getLevel(enchantment);
        if (has > 0 && has >= this.targetLevel)
        {
            say("enchant_already_has", "item", Names.name(stack), "enchant", Ench.name(enchantment, has));
            return "ya lo tiene";
        }
        for (Holder<Enchantment> other : current.keySet())
        {
            if (!other.equals(enchantment) && !Enchantment.areCompatible(other, enchantment))
            {
                say("enchant_conflict", "enchant", name, "other", Ench.name(other, current.getLevel(other)));
                return "choca";
            }
        }
        return null;
    }

    // ---------- el efecto, en el empujon de la animacion ----------

    private void apply(ServerPlayer player)
    {
        Inventory inventory = player.getInventory();
        ItemStack stack = inventory.getItem(this.slot);
        if (!ItemStack.isSameItem(stack, this.expected))
        {
            // Lo movio mientras tanto: se busca de nuevo lo mismo.
            this.slot = indexOf(inventory, candidate -> ItemStack.isSameItem(candidate, this.expected));
            if (this.slot < 0)
            {
                return;
            }
            stack = inventory.getItem(this.slot);
        }

        List<String> names = this.wanted != null
                ? enchantSpecific(player, inventory, this.slot, stack)
                : enchantRandom(player, inventory, this.slot, stack);
        if (names.isEmpty())
        {
            return;
        }
        this.applied = true;
        this.result = Names.joinList(names);
        this.rocco.markEnchantUsed();
        inventory.setChanged();
    }

    // Exactamente el encantamiento pedido, y nada mas.
    private List<String> enchantSpecific(ServerPlayer player, Inventory inventory, int index, ItemStack stack)
    {
        Holder<Enchantment> enchantment = this.wanted;
        int level = this.targetLevel;
        List<String> names = new ArrayList<>();

        if (stack.is(Items.BOOK))
        {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            EnchantmentHelper.updateEnchantments(book, enchantments -> enchantments.set(enchantment, level));
            replaceOne(player, inventory, index, stack, book);
        }
        else
        {
            // En un libro encantado va a lo que guarda; en lo demas, a sus encantamientos.
            EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(enchantment, level));
        }
        names.add(Ench.name(enchantment, level));
        return names;
    }

    // Como una mesa de encantamientos al maximo: lo que salga, al azar.
    private List<String> enchantRandom(ServerPlayer player, Inventory inventory, int index, ItemStack stack)
    {
        int power = RoccoCommonConfig.ENCHANT_POWER.get();
        boolean treasure = RoccoCommonConfig.ENCHANT_TREASURE.get();
        net.minecraft.core.Registry<Enchantment> registry = Ench.registry(player.registryAccess());
        // Como la mesa; con los "tesoros" permitidos, todo lo que puede salir en un cofre.
        java.util.stream.Stream<Holder<Enchantment>> possible = registry.getTag(treasure ? EnchantmentTags.ON_RANDOM_LOOT : EnchantmentTags.IN_ENCHANTING_TABLE)
                .map(set -> set.stream()).orElse(java.util.stream.Stream.empty());
        List<EnchantmentInstance> rolled = EnchantmentHelper.selectEnchantment(this.rocco.getRandom(), stack, power, possible);
        List<String> names = new ArrayList<>();
        if (rolled.isEmpty())
        {
            return names;
        }

        if (stack.is(Items.BOOK))
        {
            ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
            for (EnchantmentInstance instance : rolled)
            {
                EnchantmentHelper.updateEnchantments(book, enchantments -> enchantments.upgrade(instance.enchantment, instance.level));
                names.add(Ench.name(instance.enchantment, instance.level));
            }
            replaceOne(player, inventory, index, stack, book);
            return names;
        }

        for (EnchantmentInstance instance : rolled)
        {
            stack.enchant(instance.enchantment, instance.level);
            names.add(Ench.name(instance.enchantment, instance.level));
        }
        return names;
    }

    // De una pila de libros se encanta uno solo.
    private static void replaceOne(ServerPlayer player, Inventory inventory, int index, ItemStack stack, ItemStack result)
    {
        stack.shrink(1);
        if (stack.isEmpty())
        {
            inventory.setItem(index, result);
        }
        else if (!inventory.add(result))
        {
            player.drop(result, false);
        }
    }

    // ---------- que encantar ----------

    private boolean usable(ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return false;
        }
        if (this.wanted != null)
        {
            return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK) || this.wanted.value().canEnchant(stack);
        }
        return stack.is(Items.BOOK) || (!stack.isEnchanted() && stack.isEnchantable());
    }

    // Lo que nombro, la armadura, o lo que tiene en la mano.
    private int findTarget(ServerPlayer player)
    {
        Inventory inventory = player.getInventory();
        String hint = Names.normalize(this.hint);

        // "el pico", "la espada", "las botas" sin material: cualquiera de ese tipo, primero el de la mano.
        java.util.function.Predicate<ItemStack> kind = kindOf(hint);
        if (kind != null)
        {
            int held = inventory.selected;
            if (kind.test(inventory.getItem(held)))
            {
                return held;
            }
            for (int i = 36; i <= 40; i++)
            {
                if (kind.test(inventory.getItem(i)) && usable(inventory.getItem(i)))
                {
                    return i;
                }
            }
            int preferred = indexOf(inventory, stack -> kind.test(stack) && usable(stack));
            return preferred >= 0 ? preferred : indexOf(inventory, kind);
        }

        if (this.query != null)
        {
            List<Item> items = this.query.items();
            int held = inventory.selected;
            if (items.contains(inventory.getItem(held).getItem()))
            {
                return held;
            }
            int preferred = indexOf(inventory, stack -> items.contains(stack.getItem()) && usable(stack));
            return preferred >= 0 ? preferred : indexOf(inventory, stack -> items.contains(stack.getItem()));
        }

        if (hint.contains("armadura") || hint.contains("armor"))
        {
            // Las 4 piezas puestas (indices 36 a 39), primero la que sirva para este pedido.
            for (int i = 36; i < 40; i++)
            {
                if (usable(inventory.getItem(i)))
                {
                    return i;
                }
            }
            for (int i = 36; i < 40; i++)
            {
                if (!inventory.getItem(i).isEmpty())
                {
                    return i;
                }
            }
            return -1;
        }

        if (!inventory.getItem(inventory.selected).isEmpty())
        {
            return inventory.selected;
        }
        // La mano izquierda (indice 40).
        return inventory.getItem(40).isEmpty() ? -1 : 40;
    }

    private static final java.util.regex.Pattern MATERIAL =
            java.util.regex.Pattern.compile("\\b(madera|piedra|hierro|oro|dorad[ao]s?|diamante|netherita|cuero|malla|tortuga)\\b");

    // El tipo de cosa que nombro, si no dijo de que material. null si nombro algo concreto.
    @Nullable
    private static java.util.function.Predicate<ItemStack> kindOf(String hint)
    {
        if (hint.isEmpty() || MATERIAL.matcher(hint).find())
        {
            return null;
        }
        java.util.Map<String, java.util.function.Predicate<ItemStack>> kinds = new java.util.LinkedHashMap<>();
        kinds.put("pico", stack -> stack.getItem() instanceof net.minecraft.world.item.PickaxeItem);
        kinds.put("hacha", stack -> stack.getItem() instanceof net.minecraft.world.item.AxeItem);
        kinds.put("pala", stack -> stack.getItem() instanceof net.minecraft.world.item.ShovelItem);
        kinds.put("azada", stack -> stack.getItem() instanceof net.minecraft.world.item.HoeItem);
        kinds.put("espada", stack -> stack.getItem() instanceof net.minecraft.world.item.SwordItem);
        kinds.put("ballesta", stack -> stack.getItem() instanceof net.minecraft.world.item.CrossbowItem);
        kinds.put("arco", stack -> stack.getItem() instanceof net.minecraft.world.item.BowItem);
        kinds.put("tridente", stack -> stack.getItem() instanceof net.minecraft.world.item.TridentItem);
        kinds.put("cana", stack -> stack.getItem() instanceof net.minecraft.world.item.FishingRodItem);
        kinds.put("escudo", stack -> stack.getItem() instanceof net.minecraft.world.item.ShieldItem);
        kinds.put("elitros", stack -> stack.is(Items.ELYTRA));
        kinds.put("elytra", stack -> stack.is(Items.ELYTRA));
        kinds.put("libro", stack -> stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK));
        kinds.put("casco", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.HEAD));
        kinds.put("pechera", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.CHEST));
        kinds.put("peto", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.CHEST));
        kinds.put("pantalon", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.LEGS));
        kinds.put("grebas", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.LEGS));
        kinds.put("botas", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.FEET));
        kinds.put("bota", stack -> isArmor(stack, net.minecraft.world.entity.EquipmentSlot.FEET));
        for (String word : hint.split(" "))
        {
            for (var entry : kinds.entrySet())
            {
                if (word.equals(entry.getKey()) || Names.singular(word).equals(entry.getKey()))
                {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static boolean isArmor(ItemStack stack, net.minecraft.world.entity.EquipmentSlot slot)
    {
        return stack.getItem() instanceof net.minecraft.world.item.ArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    private static int indexOf(Inventory inventory, java.util.function.Predicate<ItemStack> filter)
    {
        for (int i = 0; i < inventory.getContainerSize(); i++)
        {
            if (filter.test(inventory.getItem(i)))
            {
                return i;
            }
        }
        return -1;
    }

    static String minutes(long ticks)
    {
        long minutes = (ticks + 1199L) / 1200L;
        if (ticks < 1200L)
        {
            return "menos de un minuto";
        }
        return minutes == 1 ? "1 minuto" : minutes + " minutos";
    }

    private static String roman(int level)
    {
        String[] numerals = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return level >= 0 && level < numerals.length ? numerals[level] : String.valueOf(level);
    }

    @Override
    public String describe()
    {
        String what = this.expected.isEmpty() ? "algo" : Names.name(this.expected);
        return this.wanted == null ? "encantando " + what : "encantando " + what + " con "
                + Ench.name(this.wanted, Math.max(1, this.targetLevel));
    }
}
