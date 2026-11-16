package com.cristoree.rocco.entity;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

import java.util.List;

// Lo que Rocco puede comer o tomar y cuanto lo ayuda. La comida le da "saciedad", como al
// jugador: mientras le dura se cura el doble de rapido (mas o menos segun lo que comio). Las
// manzanas doradas y las pociones hacen su efecto de siempre, pero las usa solo si lo dejaste.
public final class RoccoFood
{
    // Que se puede usar solo (las tres casillas del menu de equipo).
    public static final int FOOD = 1;
    public static final int POTIONS = 1 << 1;
    public static final int GOLDEN = 1 << 2;

    // Lo mismo que el jugador: la saciedad no pasa de 20.
    public static final float MAX_SATURATION = 20.0F;

    public enum Need { HEAL, REGEN, FIRE, STRENGTH }

    public static boolean isGoldenApple(ItemStack stack)
    {
        return stack.is(Items.GOLDEN_APPLE) || stack.is(Items.ENCHANTED_GOLDEN_APPLE);
    }

    // Comida comun: se come y no hace mal. Afuera: lo que trae efectos (carne podrida, ojo de
    // araña, pez globo, pollo crudo), el guiso sospechoso y la fruta coral (lo teletransportaria).
    public static boolean isPlainFood(ItemStack stack)
    {
        if (stack.isEmpty() || !stack.has(net.minecraft.core.component.DataComponents.FOOD) || isGoldenApple(stack)
                || stack.is(Items.SUSPICIOUS_STEW) || stack.is(Items.CHORUS_FRUIT))
        {
            return false;
        }
        return effectsOf(stack).isEmpty();
    }

    // Los efectos que da al comerlo (carne podrida, ojo de araña, manzana dorada...).
    public static java.util.List<net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect> effectsOf(ItemStack stack)
    {
        java.util.List<net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect> out = new java.util.ArrayList<>();
        net.minecraft.world.item.component.Consumable consumable = stack.get(net.minecraft.core.component.DataComponents.CONSUMABLE);
        if (consumable != null)
        {
            for (net.minecraft.world.item.consume_effects.ConsumeEffect effect : consumable.onConsumeEffects())
            {
                if (effect instanceof net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect status)
                {
                    out.add(status);
                }
            }
        }
        return out;
    }

    // Los efectos de una pocion (lo que da al tomarla).
    public static java.util.List<MobEffectInstance> potionEffects(ItemStack stack)
    {
        java.util.List<MobEffectInstance> out = new java.util.ArrayList<>();
        stack.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
                net.minecraft.world.item.alchemy.PotionContents.EMPTY).getAllEffects().forEach(out::add);
        return out;
    }

    // Una pocion que se toma (no las que se tiran) y que tiene algo bueno.
    public static boolean isDrinkablePotion(ItemStack stack)
    {
        return stack.is(Items.POTION) && !com.cristoree.rocco.entity.RoccoFood.potionEffects(stack).isEmpty() && !isHarmful(stack);
    }

    static boolean isHarmful(ItemStack stack)
    {
        for (MobEffectInstance effect : com.cristoree.rocco.entity.RoccoFood.potionEffects(stack))
        {
            if (!effect.getEffect().value().isBeneficial())
            {
                return true;
            }
        }
        return false;
    }

    public static boolean potionGives(ItemStack stack, net.minecraft.core.Holder<MobEffect> wanted)
    {
        for (MobEffectInstance effect : com.cristoree.rocco.entity.RoccoFood.potionEffects(stack))
        {
            if (effect.getEffect().is(wanted))
            {
                return true;
            }
        }
        return false;
    }

    public static boolean potionFor(ItemStack stack, Need need)
    {
        if (!isDrinkablePotion(stack))
        {
            return false;
        }
        return switch (need)
        {
            case HEAL -> potionGives(stack, MobEffects.INSTANT_HEALTH);
            case REGEN -> potionGives(stack, MobEffects.REGENERATION);
            case FIRE -> potionGives(stack, MobEffects.FIRE_RESISTANCE);
            case STRENGTH -> potionGives(stack, MobEffects.STRENGTH);
        };
    }

    // La saciedad que da: la del jugador (en 1.21 ya viene como nutricion x modificador x 2) mas media
    // nutricion, porque Rocco no tiene barra de hambre que llenar primero. Un bife da unos 17 (casi
    // 11 corazones curados al doble de velocidad), el pan 7 y una manzana 4.
    public static float saturationOf(ItemStack stack)
    {
        FoodProperties food = stack.get(net.minecraft.core.component.DataComponents.FOOD);
        if (food == null)
        {
            return 0.0F;
        }
        return food.saturation() + food.nutrition() * 0.5F;
    }

    // Lo que queda despues: el bol del guiso o la botella de la miel y de las pociones.
    public static ItemStack leftoverOf(ItemStack stack)
    {
        net.minecraft.world.item.component.UseRemainder remainder = stack.get(net.minecraft.core.component.DataComponents.USE_REMAINDER);
        if (remainder != null)
        {
            return remainder.convertInto().create();
        }
        if (stack.is(Items.SUSPICIOUS_STEW))
        {
            return new ItemStack(Items.BOWL);
        }
        if (stack.is(Items.HONEY_BOTTLE) || stack.getItem() instanceof PotionItem)
        {
            return new ItemStack(Items.GLASS_BOTTLE);
        }
        return ItemStack.EMPTY;
    }

    public static boolean isBad(ItemStack stack)
    {
        if (stack.getItem() instanceof PotionItem)
        {
            return isHarmful(stack);
        }
        for (net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect status : effectsOf(stack))
        {
            for (MobEffectInstance effect : status.effects())
            {
                if (effect.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL)
                {
                    return true;
                }
            }
        }
        return false;
    }

    // Que animacion le toca: las pociones (y la miel) se toman; lo demas se come.
    public static RoccoTask taskFor(ItemStack stack)
    {
        boolean bad = isBad(stack);
        if (isDrink(stack))
        {
            return bad ? RoccoTask.POTION_DRINK_BAD : RoccoTask.POTION_DRINK;
        }
        return bad ? RoccoTask.FOOD_EAT_BAD : RoccoTask.FOOD_EAT;
    }

    public static boolean isConsumable(ItemStack stack)
    {
        return stack.has(net.minecraft.core.component.DataComponents.FOOD) || stack.is(Items.POTION) || stack.is(Items.HONEY_BOTTLE);
    }

    public static boolean isDrink(ItemStack stack)
    {
        return stack.getItem() instanceof PotionItem || stack.is(Items.HONEY_BOTTLE);
    }

    // De lo que lleva, la mejor comida comun (la que mas sacia). Vacio si no tiene.
    public static ItemStack bestFood(List<ItemStack> carried)
    {
        ItemStack best = ItemStack.EMPTY;
        float bestValue = 0.0F;
        for (ItemStack stack : carried)
        {
            if (isPlainFood(stack))
            {
                float value = saturationOf(stack);
                if (value > bestValue)
                {
                    bestValue = value;
                    best = stack;
                }
            }
        }
        return best;
    }

    public static ItemStack find(List<ItemStack> carried, java.util.function.Predicate<ItemStack> filter)
    {
        for (ItemStack stack : carried)
        {
            if (!stack.isEmpty() && filter.test(stack))
            {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private RoccoFood() {}
}
