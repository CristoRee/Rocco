package com.cristoree.rocco.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Lo que te regala Rocco: la Semillita (al hacerse amigos) y la Flor de la Amistad, que siempre
// brilla como si estuviera encantada.
public final class RoccoGiftItem extends Item
{
    private final boolean glows;
    private final String[] tips;

    public RoccoGiftItem(Properties properties, boolean glows, String... tips)
    {
        super(properties);
        this.glows = glows;
        this.tips = tips;
    }

    @Override
    public boolean isFoil(ItemStack stack)
    {
        return this.glows || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        for (String tip : this.tips)
        {
            tooltip.add(Component.translatable(tip).withStyle(ChatFormatting.GRAY));
        }
    }
}
