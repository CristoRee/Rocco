package com.cristoree.rocco.item;

import com.cristoree.rocco.RoccoBurp;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

// La Roco Cola que te da Rocco. Tiene 2 tragos: llena -> a la mitad -> botella vacia, cada estado es un
// item propio (sin NBT, asi se apilan). Se toma como una pocion, refresca (rapidez y prisa, 20 segundos)
// y a los 2,5 segundos sale el eructo gigante (RoccoBurp).
public final class RocoColaItem extends Item
{
    private static final int USE_TICKS = 32;
    private static final int REFRESHED_TICKS = 20 * 20;
    // Un segundo antes de poder tirar la botella vacia.
    private static final int EMPTY_THROW_DELAY = 20;

    private final Supplier<Item> next;
    private final String[] tips;

    public RocoColaItem(Properties properties, Supplier<Item> next, String... tips)
    {
        super(properties);
        this.next = next;
        this.tips = tips;
    }

    @Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand)
    {
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack)
    {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack, net.minecraft.world.entity.LivingEntity user)
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
            player.addEffect(new MobEffectInstance(MobEffects.SPEED, REFRESHED_TICKS, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HASTE, REFRESHED_TICKS, 0));
            RoccoBurp.drank(player);
        }
        drinker.gameEvent(GameEvent.DRINK);
        if (drinker instanceof Player player && player.getAbilities().instabuild)
        {
            return stack;
        }
        ItemStack after = new ItemStack(this.next.get());
        // Recien vaciada no sale volando: el clic que se sigue apretando la tiraba enseguida.
        if (drinker instanceof Player player && after.getItem() instanceof EmptyColaItem)
        {
            player.getCooldowns().addCooldown(after, EMPTY_THROW_DELAY);
        }
        stack.shrink(1);
        if (stack.isEmpty())
        {
            return after;
        }
        // Tenia varias: las otras siguen llenas y la que tomo queda al lado.
        if (drinker instanceof Player player && !player.getInventory().add(after))
        {
            player.drop(after, false, net.minecraft.util.Prediction.SERVER_ONLY);
        }
        return stack;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext level, net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> tooltip, TooltipFlag flag)
    {
        for (String tip : this.tips)
        {
            tooltip.accept(Component.translatable(tip).withStyle(ChatFormatting.GRAY));
        }
    }
}
