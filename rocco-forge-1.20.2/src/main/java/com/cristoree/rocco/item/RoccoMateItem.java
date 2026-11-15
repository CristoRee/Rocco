package com.cristoree.rocco.item;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.registry.ModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// El mate que te pasa Rocco. No se fabrica ni esta en el creativo: solo te lo da el. Se toma en
// tres sorbos (el agua baja en el modelo) y despues hay que devolverselo con clic derecho para que
// lo vuelva a cebar. Nunca se pierde ni se duplica: si te alejas, lo tiras o el deja de esperarlo,
// vuelve con el.
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid = com.cristoree.rocco.RoccoMod.MODID)
public class RoccoMateItem extends Item
{
    public static final int SIPS = 3;
    private static final int SIP_TICKS = 30;
    private static final String TAG_ROCCO = "Rocco";
    private static final String TAG_SIPS = "Sips";

    public RoccoMateItem(Properties properties)
    {
        super(properties);
    }

    public static ItemStack create(RoccoEntity rocco)
    {
        ItemStack stack = new ItemStack(com.cristoree.rocco.registry.ModItems.MATE.get());
        CompoundTag tag = stack.getOrCreateTag();
        tag.putUUID(TAG_ROCCO, rocco.getUUID());
        tag.putInt(TAG_SIPS, 0);
        return stack;
    }

    public static int sips(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(TAG_SIPS);
    }

    @Nullable
    public static UUID roccoOf(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.hasUUID(TAG_ROCCO) ? tag.getUUID(TAG_ROCCO) : null;
    }

    public static boolean isMateOf(ItemStack stack, RoccoEntity rocco)
    {
        return stack.getItem() instanceof RoccoMateItem && rocco.getUUID().equals(roccoOf(stack));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack)
    {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack stack)
    {
        return SIP_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        // Vacio ya no se toma: hay que devolverselo.
        if (sips(stack) >= SIPS)
        {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity drinker)
    {
        if (level.isClientSide)
        {
            return stack;
        }
        int sips = Math.min(SIPS, sips(stack) + 1);
        stack.getOrCreateTag().putInt(TAG_SIPS, sips);

        SoundEvent sound = switch (sips)
        {
            case 1 -> ModSounds.PLAYER_SIP_1.get();
            case 2 -> ModSounds.PLAYER_SIP_2.get();
            default -> ModSounds.PLAYER_SIP_3.get();
        };
        level.playSound(null, drinker.getX(), drinker.getY(), drinker.getZ(), sound, SoundSource.PLAYERS, 0.6F, 1.0F);

        // El ultimo sorbo te deja con pilas por un ratito.
        if (sips == SIPS)
        {
            drinker.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, 0, false, false, true));
        }
        if (drinker instanceof ServerPlayer player && rocco(level, stack) instanceof RoccoEntity rocco)
        {
            rocco.mateShare().sipped(player, sips);
        }
        return stack;
    }

    // Cada segundo se fija que Rocco lo siga esperando: si no, el mate ya volvio con el.
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected)
    {
        if (level.isClientSide || !(holder instanceof ServerPlayer player) || player.tickCount % 20 != 0)
        {
            return;
        }
        if (!(rocco(level, stack) instanceof RoccoEntity rocco)
                || rocco.distanceToSqr(player) > 32.0D * 32.0D
                || !rocco.mateShare().isWaitingFor(player))
        {
            stack.setCount(0);
        }
    }

    // Tirado al piso no queda: vuelve con Rocco y el guarda sus cosas.
    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity)
    {
        Level level = entity.level();
        if (!level.isClientSide)
        {
            if (rocco(level, stack) instanceof RoccoEntity rocco)
            {
                rocco.mateShare().mateDropped();
            }
            entity.discard();
        }
        return true;
    }

    // Si te moris con el mate en la mano no queda tirado: vuelve con el.
    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event)
    {
        event.getDrops().removeIf(drop -> drop.getItem().getItem() instanceof RoccoMateItem);
    }

    @Nullable
    private static Entity rocco(Level level, ItemStack stack)
    {
        UUID id = roccoOf(stack);
        return id != null && level instanceof ServerLevel server ? server.getEntity(id) : null;
    }
}
