package com.cristoree.rocco.item;

import com.cristoree.rocco.entity.ThrownColaBottle;
import com.cristoree.rocco.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// La botella vacia de Roco Cola: se tira (y se rompe donde pega), o agachado y con clic derecho
// sobre un bloque se rompe en la mano y queda la botella rota.
public final class EmptyColaItem extends Item
{
    public EmptyColaItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context)
    {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown())
        {
            // Sin agacharse se tira, como al aire.
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();
        if (!level.isClientSide())
        {
            ItemStack stack = context.getItemInHand();
            if (!player.getAbilities().instabuild)
            {
                stack.shrink(1);
            }
            ItemStack broken = new ItemStack(ModItems.BOTELLA_ROTA.get());
            if (!player.getInventory().add(broken))
            {
                player.drop(broken, false, net.minecraft.util.Prediction.SERVER_ONLY);
            }
            Direction face = context.getClickedFace();
            Vec3 at = context.getClickLocation().add(face.getStepX() * 0.05D, face.getStepY() * 0.05D, face.getStepZ() * 0.05D);
            BrokenBottleItem.shatter(level, at.x, at.y, at.z, 0.9F);
            if (level instanceof ServerLevel server)
            {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this), at.x, at.y, at.z, 6, 0.05D, 0.05D, 0.05D, 0.08D);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SPLASH_POTION_THROW, SoundSource.PLAYERS,
                0.5F, 0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide())
        {
            ThrownColaBottle bottle = new ThrownColaBottle(level, player);
            bottle.setItem(stack.copyWithCount(1));
            bottle.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.3F, 1.0F);
            level.addFreshEntity(bottle);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        if (!player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag)
    {
        tooltip.accept(Component.translatable("tooltip.rocco.roco_cola_empty").withStyle(ChatFormatting.GRAY));
    }
}
