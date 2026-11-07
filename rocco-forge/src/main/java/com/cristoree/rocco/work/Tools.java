package com.cristoree.rocco.work;

import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.tags.BlockTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Que herramienta usa Rocco para cada bloque y cuanto tarda, con las mismas cuentas que hace el
// juego para el jugador: velocidad de la herramienta, Eficiencia y si es la correcta.
public final class Tools
{
    // slot -1 = a mano.
    public record Choice(int slot, ItemStack tool, int ticks, boolean dropsSomething)
    {
        public boolean byHand()
        {
            return this.slot < 0;
        }
    }

    // La mejor opcion entre todo lo que lleva. null si el bloque no se puede romper.
    public static Choice best(Container inventory, BlockState state, BlockGetter level, BlockPos pos)
    {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0.0F)
        {
            return null;
        }

        Choice best = evaluate(-1, ItemStack.EMPTY, state, hardness);

        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !stack.isDamageableItem() || aboutToBreak(stack))
            {
                continue;
            }

            Choice candidate = evaluate(slot, stack, state, hardness);
            boolean better = (candidate.dropsSomething() && !best.dropsSomething())
                    || (candidate.dropsSomething() == best.dropsSomething() && candidate.ticks() < best.ticks());

            // Con el mismo resultado, guarda las encantadas para cuando hagan falta.
            if (!better && candidate.dropsSomething() == best.dropsSomething() && candidate.ticks() == best.ticks()
                    && best.tool().isEnchanted() && !stack.isEnchanted())
            {
                better = true;
            }

            if (better)
            {
                best = candidate;
            }
        }
        return best;
    }

    // Como best, pero solo herramientas con Toque de seda (para el cofre de ender).
    public static Choice bestWithSilkTouch(Container inventory, BlockState state, BlockGetter level, BlockPos pos)
    {
        float hardness = state.getDestroySpeed(level, pos);
        Choice best = null;

        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || !hasSilkTouch(stack) || aboutToBreak(stack))
            {
                continue;
            }
            Choice candidate = evaluate(slot, stack, state, hardness);
            if (candidate.dropsSomething() && (best == null || candidate.ticks() < best.ticks()))
            {
                best = candidate;
            }
        }
        return best;
    }

    private static Choice evaluate(int slot, ItemStack tool, BlockState state, float hardness)
    {
        boolean correct = !state.requiresCorrectToolForDrops() || (!tool.isEmpty() && tool.isCorrectToolForDrops(state));

        float speed = tool.isEmpty() ? 1.0F : tool.getDestroySpeed(state);
        if (speed > 1.0F)
        {
            int efficiency = tool.getEnchantmentLevel(Enchantments.BLOCK_EFFICIENCY);
            if (efficiency > 0)
            {
                speed += efficiency * efficiency + 1;
            }
        }

        int ticks;
        if (hardness <= 0.0F)
        {
            ticks = 1;
        }
        else
        {
            float perTick = speed / hardness / (correct ? 30.0F : 100.0F);
            ticks = (int) Math.ceil(1.0F / perTick);
        }

        return new Choice(slot, tool, Math.max(1, ticks), correct);
    }

    // Las encantadas no se rompen: se dejan en 1 para que el jugador las repare.
    private static boolean aboutToBreak(ItemStack stack)
    {
        return RoccoCommonConfig.WORK_PROTECT_ENCHANTED.get()
                && stack.isEnchanted()
                && stack.getMaxDamage() - stack.getDamageValue() <= 1;
    }

    public static boolean hasSilkTouch(ItemStack stack)
    {
        return stack.getEnchantmentLevel(Enchantments.SILK_TOUCH) > 0;
    }

    // Que tipo de herramienta haria falta, para decirselo al jugador.
    public static String neededFor(BlockState state)
    {
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE))
        {
            if (state.is(BlockTags.NEEDS_DIAMOND_TOOL))
            {
                return "un pico de diamante";
            }
            if (state.is(BlockTags.NEEDS_IRON_TOOL))
            {
                return "un pico de hierro";
            }
            if (state.is(BlockTags.NEEDS_STONE_TOOL))
            {
                return "un pico de piedra";
            }
            return "un pico";
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE))
        {
            return "un hacha";
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL))
        {
            return "una pala";
        }
        if (state.is(BlockTags.MINEABLE_WITH_HOE))
        {
            return "una azada";
        }
        return "otra herramienta";
    }

    public static int findHoe(Container inventory)
    {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof HoeItem && !aboutToBreak(stack))
            {
                return slot;
            }
        }
        return -1;
    }

    // Gasta un uso. Si se rompe, hace el ruido y lo avisa.
    public static void wear(RoccoEntity rocco, int slot)
    {
        if (slot < 0)
        {
            return;
        }

        Container inventory = rocco.inventory();
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty() || !stack.isDamageableItem())
        {
            return;
        }

        String name = Names.name(stack);
        boolean[] broke = {false};
        stack.hurtAndBreak(1, rocco, owner ->
        {
            owner.broadcastBreakEvent(EquipmentSlot.MAINHAND);
            broke[0] = true;
        });

        if (broke[0] || stack.isEmpty())
        {
            inventory.setItem(slot, ItemStack.EMPTY);
            rocco.sayLine("tool_broke", "tool", name);
        }
        inventory.setChanged();
    }

    // Rompe el bloque como lo haria el jugador con esa herramienta (Toque de seda, Fortuna) y
    // devuelve lo que solto, sin tirarlo al piso.
    public static List<ItemStack> breakBlock(RoccoEntity rocco, ServerLevel level, BlockPos pos, Choice choice)
    {
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
        {
            return List.of();
        }

        ItemStack tool = choice.byHand() ? ItemStack.EMPTY : rocco.inventory().getItem(choice.slot());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        float hardness = state.getDestroySpeed(level, pos);

        List<ItemStack> drops = choice.dropsSomething()
                ? new ArrayList<>(Block.getDrops(state, level, pos, blockEntity, rocco, tool))
                : new ArrayList<>();

        level.destroyBlock(pos, false, rocco);
        // Como al jugador: los bloques que se rompen al toque (pasto, antorchas) no gastan.
        if (!choice.byHand() && hardness != 0.0F)
        {
            Tools.wear(rocco, choice.slot());
        }
        return drops;
    }

    // "Pico de hierro (Eficiencia II, Toque de seda, 200/250)".
    public static String describe(ItemStack stack)
    {
        StringBuilder out = new StringBuilder(Names.name(stack));
        List<String> extras = new ArrayList<>();

        for (Map.Entry<Enchantment, Integer> entry : EnchantmentHelper.getEnchantments(stack).entrySet())
        {
            extras.add(entry.getKey().getFullname(entry.getValue()).getString());
        }
        if (stack.isDamageableItem())
        {
            extras.add((stack.getMaxDamage() - stack.getDamageValue()) + "/" + stack.getMaxDamage() + " de durabilidad");
        }

        if (!extras.isEmpty())
        {
            out.append(" (").append(String.join(", ", extras)).append(')');
        }
        return out.toString();
    }

    // Las herramientas con las que trabaja: la mejor de cada clase (pico, hacha, pala, azada, tijeras,
    // caña, encendedor). "Guardá todo" o "dame todo" no las tocan; las repetidas y la armadura que
    // no tiene puesta si. Lo que tiene puesto no esta en el inventario: eso nunca se guarda.
    public static java.util.Set<ItemStack> workTools(net.minecraft.world.Container inventory)
    {
        java.util.Map<String, ItemStack> best = new java.util.HashMap<>();
        for (ItemStack stack : Inv.contents(inventory))
        {
            String kind = workKind(stack);
            if (kind == null)
            {
                continue;
            }
            ItemStack current = best.get(kind);
            if (current == null || rank(stack) > rank(current))
            {
                best.put(kind, stack);
            }
        }
        java.util.Set<ItemStack> out = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        out.addAll(best.values());
        return out;
    }

    @org.jetbrains.annotations.Nullable
    private static String workKind(ItemStack stack)
    {
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof PickaxeItem) return "pickaxe";
        if (item instanceof AxeItem) return "axe";
        if (item instanceof ShovelItem) return "shovel";
        if (item instanceof HoeItem) return "hoe";
        if (item instanceof net.minecraft.world.item.ShearsItem) return "shears";
        if (item instanceof net.minecraft.world.item.FishingRodItem) return "rod";
        if (item instanceof net.minecraft.world.item.FlintAndSteelItem) return "flint";
        return null;
    }

    // Mejor material primero, despues encantada, despues la que mas vida le queda.
    private static double rank(ItemStack stack)
    {
        double tier = stack.getItem() instanceof net.minecraft.world.item.TieredItem tiered ? tiered.getTier().getLevel() * 1000.0D
                + tiered.getTier().getSpeed() * 10.0D : 0.0D;
        double left = stack.isDamageableItem() ? (stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage() : 1.0D;
        return tier + (stack.isEnchanted() ? 500.0D : 0.0D) + left;
    }

    public static boolean isTool(ItemStack stack)
    {
        return stack.isDamageableItem()
                || stack.getItem() instanceof PickaxeItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof ShovelItem
                || stack.getItem() instanceof HoeItem;
    }

    private Tools() {}
}
