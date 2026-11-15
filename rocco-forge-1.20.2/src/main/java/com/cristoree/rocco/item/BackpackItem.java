package com.cristoree.rocco.item;

import com.cristoree.rocco.menu.RoccoInventoryMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

// Las mochilas de Rocco. No tienen inventario propio: agrandan el suyo. Rocco arranca con 3 filas
// y la mochila N le suma N filas (la 6 suma una pagina entera de 6x9). Lo que esta en los lugares
// que agrega la mochila viaja adentro de ella cuando se la sacan ("Items" en su NBT), y por eso
// mejorarla conserva lo que lleva. En tu mano se abre con clic derecho y se ve eso mismo: son los
// lugares que le va a sumar a Rocco cuando se la pongas.
public final class BackpackItem extends Item
{
    public static final int MAX_TIER = 6;
    public static final int BASE_SLOTS = 27;
    public static final int PAGE_SLOTS = 54;
    public static final int MAX_SLOTS = slotsFor(MAX_TIER);

    private final int tier;

    public BackpackItem(int tier, Properties properties)
    {
        super(properties);
        this.tier = tier;
    }

    public int tier()
    {
        return this.tier;
    }

    // Cuantos lugares le suma esta mochila.
    public static int bonusFor(int tier)
    {
        return tier * 9;
    }

    // Con la mochila de ese nivel puesta: 27, 36, 54, 81, 117, 162 y 216 (cuatro paginas justas).
    public static int slotsFor(int tier)
    {
        return BASE_SLOTS + 9 * tier * (tier + 1) / 2;
    }

    public static int pagesFor(int slots)
    {
        return (slots + PAGE_SLOTS - 1) / PAGE_SLOTS;
    }

    // Los lugares que trae adentro cuando no la tiene puesta: los mismos que le suma a Rocco.
    public static int ownSlots(int tier)
    {
        return slotsFor(tier) - BASE_SLOTS;
    }

    // Clic derecho en el aire o en un bloque comun: se abre. A Rocco se la pones porque el clic
    // sobre una criatura llega antes que esto.
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand)
    {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer)
        {
            RoccoInventoryMenu.openHeld(serverPlayer, hand, stack, ownSlots(this.tier));
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    // Guardar cosas cambia su NBT: sin esto la mano baja y sube en cada cambio.
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack before, ItemStack after, boolean slotChanged)
    {
        return slotChanged || before.getItem() != after.getItem();
    }

    // Si se quema o la rompe una explosion, lo de adentro queda en el piso, como en los cofres de shulker.
    @Override
    public void onDestroyed(ItemEntity entity)
    {
        ItemStack stack = entity.getItem();
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("Items"))
        {
            return;
        }
        ListTag stored = tag.getList("Items", 10);
        stack.removeTagKey("Items");
        ItemUtils.onContainerDestroyed(entity, stored.stream().map(entry -> ItemStack.of((CompoundTag) entry)));
    }

    // Cuantas cosas distintas lleva adentro (lo que quedo en los lugares de la mochila).
    public static int storedStacks(ItemStack stack)
    {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getList("Items", 10).size();
    }

    // Adentro de un cofre de shulker o de un bolso no entra: lleva un inventario adentro.
    @Override
    public boolean canFitInsideContainerItems()
    {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag)
    {
        tooltip.add(Component.translatable("tooltip.rocco.mochila_" + this.tier).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.rocco.backpack_open").withStyle(ChatFormatting.GRAY));
        int stored = storedStacks(stack);
        if (stored > 0)
        {
            tooltip.add(Component.translatable("tooltip.rocco.backpack_stored", stored).withStyle(ChatFormatting.GOLD));
        }
        if (this.tier < MAX_TIER)
        {
            tooltip.add(Component.translatable("tooltip.rocco.backpack_upgrade").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
