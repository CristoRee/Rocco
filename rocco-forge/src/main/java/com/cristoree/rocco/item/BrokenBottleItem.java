package com.cristoree.rocco.item;

import com.cristoree.rocco.registry.ModParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// La botella de Roco Cola rota contra el piso: un arma corta de 3 de daño, mas rapida que una
// espada, que dura 40 golpes y no se repara. Cuando se termina de romper suena a vidrio.
public final class BrokenBottleItem extends SwordItem
{
    public static final Tier GLASS = new Tier()
    {
        @Override
        public int getUses()
        {
            return 40;
        }

        @Override
        public float getSpeed()
        {
            return 1.0F;
        }

        @Override
        public float getAttackDamageBonus()
        {
            return 0.0F;
        }

        @Override
        public int getLevel()
        {
            return 0;
        }

        @Override
        public int getEnchantmentValue()
        {
            return 6;
        }

        @Override
        public Ingredient getRepairIngredient()
        {
            return Ingredient.EMPTY;
        }
    };

    public BrokenBottleItem(Properties properties)
    {
        // 1 de la mano + 2 = 3 de daño; 4 - 2 = 2 golpes por segundo (la espada da 1,6).
        super(GLASS, 2, -2.0F, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker)
    {
        stack.hurtAndBreak(1, attacker, holder -> broke(holder, EquipmentSlot.MAINHAND));
        return true;
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner)
    {
        if (state.getDestroySpeed(level, pos) != 0.0F)
        {
            stack.hurtAndBreak(2, miner, holder -> broke(holder, EquipmentSlot.MAINHAND));
        }
        return true;
    }

    // Se termino de romper en la mano: el ruido de siempre y ademas vidrio.
    public static void broke(LivingEntity holder, EquipmentSlot slot)
    {
        holder.broadcastBreakEvent(slot);
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
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.rocco.botella_rota").withStyle(ChatFormatting.GRAY));
    }
}
