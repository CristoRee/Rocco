package com.cristoree.rocco.item;

import com.cristoree.rocco.RoccoBurp;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// La Roco Cola que te da Rocco. Se toma como una pocion, refresca (rapidez y prisa, 20 segundos) y
// a los 2,5 segundos sale el eructo gigante (RoccoBurp).
public final class RocoColaItem extends Item
{
    private static final int USE_TICKS = 32;
    private static final int REFRESHED_TICKS = 20 * 20;

    public RocoColaItem(Properties properties)
    {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return USE_TICKS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker)
    {
        if (drinker instanceof ServerPlayer player)
        {
            CriteriaTriggers.CONSUME_ITEM.trigger(player, stack);
            player.awardStat(Stats.ITEM_USED.get(this));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, REFRESHED_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, REFRESHED_TICKS, 0));
            RoccoBurp.drank(player);
        }
        if (!(drinker instanceof Player player) || !player.getAbilities().instabuild)
        {
            stack.shrink(1);
        }
        drinker.gameEvent(GameEvent.DRINK);
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.rocco.roco_cola").withStyle(ChatFormatting.GRAY));
    }
}
