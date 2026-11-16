package com.cristoree.rocco.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Las comidas que hace Rocco: la torta frita y lo que sale de su parrilla.
public final class RoccoFoodItem extends Item
{
    private final String tip;

    // Lo que es carne (el asado) esta en la etiqueta minecraft:meat: los lobos lo comen.
    public RoccoFoodItem(int nutrition, float saturation, boolean meat, String tip)
    {
        super(new Properties().food(new FoodProperties.Builder().nutrition(nutrition).saturationModifier(saturation).build()));
        this.tip = tip;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable(this.tip).withStyle(ChatFormatting.GRAY));
    }
}
