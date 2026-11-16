package com.cristoree.rocco.item;

import com.cristoree.rocco.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;

// La botella de Roco Cola rota contra el piso: un arma corta de 3 de daño, mas rapida que una
// espada, que dura 40 golpes y no se repara. Cuando se termina de romper suena a vidrio.
// (Esta en la etiqueta minecraft:swords: se encanta y se usa como una espada.)
public final class BrokenBottleItem extends Item
{
    public BrokenBottleItem(Properties properties)
    {
        // 1 de la mano + 2 = 3 de daño; 4 - 2 = 2 golpes por segundo (la espada da 1,6).
        super(properties.durability(40).enchantable(6).attributes(ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 2.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_ID, -2.0D, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                .build()));
    }

    @Override
    public void hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        wear(stack, 1, attacker);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner)
    {
        if (state.getDestroySpeed(level, pos) != 0.0F)
        {
            wear(stack, 2, miner);
        }
        return true;
    }

    private static void wear(ItemStack stack, int amount, LivingEntity holder)
    {
        if (holder.level() instanceof ServerLevel server)
        {
            stack.hurtAndBreak(amount, server, holder, broken -> broke(holder, broken, EquipmentSlot.MAINHAND));
        }
    }

    // Se termino de romper en la mano: el ruido de siempre y ademas vidrio.
    public static void broke(LivingEntity holder, ItemStack broken, EquipmentSlot slot)
    {
        holder.onEquippedItemBroken(broken, slot);
        shatter(holder.level(), holder.getX(), holder.getY() + holder.getBbHeight() * 0.5D, holder.getZ(), 1.0F);
    }

    // Vidrios y ruido de vidrio roto en ese lugar (del lado del servidor, para todos).
    public static void shatter(Level level, double x, double y, double z, float pitch)
    {
        level.playSound(null, x, y, z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 1.0F, pitch);
        if (level instanceof ServerLevel server)
        {
            server.sendParticles(ModParticles.GLASS_SHARD.get(), x, y, z, 8, 0.1D, 0.1D, 0.1D, 0.15D);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("tooltip.rocco.botella_rota").withStyle(ChatFormatting.GRAY));
    }
}
