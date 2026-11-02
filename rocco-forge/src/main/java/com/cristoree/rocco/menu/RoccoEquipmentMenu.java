package com.cristoree.rocco.menu;

import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoGear;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.registry.ModMenus;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

// El equipo de Rocco: casco, pechera, pantalones, botas, el arma y el escudo, con tu inventario
// abajo para arrastrar las cosas. Cada lugar acepta solo lo suyo. Solo lo abre su dueño.
public class RoccoEquipmentMenu extends AbstractContainerMenu
{
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};
    private static final ResourceLocation[] EMPTY_ICONS = {InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE, InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS, new ResourceLocation("item/empty_slot_sword"),
            InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD};
    // Donde va cada uno en la pantalla: la armadura en columna a la izquierda, el arma y el escudo
    // al lado de Rocco (como la mano secundaria en tu inventario).
    public static final int[][] POSITIONS = {{8, 8}, {8, 26}, {8, 44}, {8, 62}, {77, 44}, {77, 62}};

    @Nullable
    private final RoccoEntity rocco;
    private final Container gear;
    // Lo que puede comer o tomar solo (RoccoFood.FOOD | POTIONS | GOLDEN), sincronizado al cliente.
    private final DataSlot consume;
    // Lo que tenia puesto al abrir: si al cerrar hay algo nuevo, lo festeja.
    private final ItemStack[] before = new ItemStack[SLOTS.length];

    public static void open(ServerPlayer player, RoccoEntity rocco)
    {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(
                (id, inventory, who) -> new RoccoEquipmentMenu(id, inventory, rocco, new Gear(rocco), new DataSlot()
                {
                    @Override
                    public int get()
                    {
                        return rocco.life().consumePerms();
                    }

                    @Override
                    public void set(int value)
                    {
                    }
                }),
                Component.translatable("rocco.menu.equipment", rocco.getDisplayName())), buf -> buf.writeVarInt(rocco.getId()));
    }

    public static RoccoEquipmentMenu fromNetwork(int id, Inventory inventory, FriendlyByteBuf buf)
    {
        Entity entity = inventory.player.level().getEntity(buf.readVarInt());
        return new RoccoEquipmentMenu(id, inventory, entity instanceof RoccoEntity rocco ? rocco : null,
                new SimpleContainer(SLOTS.length), DataSlot.standalone());
    }

    private RoccoEquipmentMenu(int id, Inventory inventory, @Nullable RoccoEntity rocco, Container gear, DataSlot consume)
    {
        super(ModMenus.EQUIPMENT.get(), id);
        this.rocco = rocco;
        this.gear = gear;
        this.consume = consume;
        addDataSlot(consume);
        for (int i = 0; i < SLOTS.length; i++)
        {
            this.before[i] = gear.getItem(i).copy();
            addSlot(new GearSlot(gear, i, POSITIONS[i][0], POSITIONS[i][1]));
        }
        for (int row = 0; row < 3; row++)
        {
            for (int column = 0; column < 9; column++)
            {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++)
        {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Nullable
    public RoccoEntity rocco()
    {
        return this.rocco;
    }

    @Override
    public boolean stillValid(Player player)
    {
        return this.gear.stillValid(player);
    }

    public int consumePerms()
    {
        return this.consume.get();
    }

    // Los botones de la derecha (0 comer solo, 1 pociones, 2 manzanas doradas): prenden o apagan
    // lo que puede usar solo. Lo que le pidas por chat lo hace igual.
    @Override
    public boolean clickMenuButton(Player player, int id)
    {
        if (this.rocco == null || id < 0 || id > 2 || !stillValid(player))
        {
            return false;
        }
        this.rocco.life().setConsumePerms(this.rocco.life().consumePerms() ^ (1 << id));
        return true;
    }

    // Shift + clic: del equipo a tu inventario, o de tu inventario al lugar que le corresponde.
    @Override
    public ItemStack quickMoveStack(Player player, int index)
    {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem())
        {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int gearEnd = SLOTS.length;
        int end = this.slots.size();

        if (index < gearEnd)
        {
            if (!moveItemStackTo(stack, gearEnd, end, true))
            {
                return ItemStack.EMPTY;
            }
        }
        else
        {
            boolean moved = false;
            for (int i = 0; i < gearEnd && !moved; i++)
            {
                Slot target = this.slots.get(i);
                if (!target.hasItem() && target.mayPlace(stack))
                {
                    moved = moveItemStackTo(stack, i, i + 1, false);
                }
            }
            if (!moved)
            {
                // Entre la mochila y la barra, como en cualquier inventario.
                int hotbar = end - 9;
                if (index < hotbar ? !moveItemStackTo(stack, hotbar, end, false) : !moveItemStackTo(stack, gearEnd, hotbar, false))
                {
                    return ItemStack.EMPTY;
                }
            }
        }

        if (stack.isEmpty())
        {
            slot.setByPlayer(ItemStack.EMPTY);
        }
        else
        {
            slot.setChanged();
        }
        return stack.getCount() == original.getCount() ? ItemStack.EMPTY : original;
    }

    @Override
    public void removed(Player player)
    {
        super.removed(player);
        if (this.rocco == null || player.level().isClientSide)
        {
            return;
        }
        for (int i = 0; i < SLOTS.length; i++)
        {
            ItemStack now = this.gear.getItem(i);
            if (!now.isEmpty() && !ItemStack.isSameItemSameTags(now, this.before[i]))
            {
                this.rocco.celebrateGear(player);
                return;
            }
        }
    }

    private static final class GearSlot extends Slot
    {
        private final EquipmentSlot equipment;
        private final ResourceLocation icon;

        GearSlot(Container container, int index, int x, int y)
        {
            super(container, index, x, y);
            this.equipment = SLOTS[index];
            this.icon = EMPTY_ICONS[index];
        }

        @Override
        public boolean mayPlace(ItemStack stack)
        {
            return RoccoGear.fits(this.equipment, stack);
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        // La maldicion de ligamiento lo deja puesto, como en el jugador.
        @Override
        public boolean mayPickup(Player player)
        {
            ItemStack stack = getItem();
            return player.isCreative() || stack.isEmpty() || !EnchantmentHelper.hasBindingCurse(stack) || this.equipment.getType() == EquipmentSlot.Type.HAND;
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon()
        {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, this.icon);
        }
    }

    // Del lado del servidor, los lugares leen y escriben directo en el equipo de Rocco.
    private static final class Gear implements Container
    {
        private final RoccoEntity rocco;

        Gear(RoccoEntity rocco)
        {
            this.rocco = rocco;
        }

        @Override
        public int getContainerSize()
        {
            return SLOTS.length;
        }

        @Override
        public boolean isEmpty()
        {
            for (EquipmentSlot slot : SLOTS)
            {
                if (!this.rocco.gear(slot).isEmpty())
                {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index)
        {
            return this.rocco.gear(SLOTS[index]);
        }

        @Override
        public ItemStack removeItem(int index, int count)
        {
            ItemStack worn = this.rocco.gear(SLOTS[index]);
            if (worn.isEmpty() || count <= 0)
            {
                return ItemStack.EMPTY;
            }
            ItemStack taken = worn.split(count);
            this.rocco.setGear(SLOTS[index], worn.isEmpty() ? ItemStack.EMPTY : worn);
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index)
        {
            ItemStack worn = this.rocco.gear(SLOTS[index]);
            this.rocco.setGear(SLOTS[index], ItemStack.EMPTY);
            return worn;
        }

        @Override
        public void setItem(int index, ItemStack stack)
        {
            this.rocco.setGear(SLOTS[index], stack);
        }

        @Override
        public int getMaxStackSize()
        {
            return 1;
        }

        @Override
        public void setChanged()
        {
        }

        @Override
        public boolean stillValid(Player player)
        {
            return this.rocco.isAlive() && this.rocco.getRoccoState() == RoccoState.ACTIVE
                    && player.distanceToSqr(this.rocco) < 8.0D * 8.0D
                    && (this.rocco.getOwnerId() == null || this.rocco.getOwnerId().equals(player.getUUID()));
        }

        @Override
        public void clearContent()
        {
            for (EquipmentSlot slot : SLOTS)
            {
                this.rocco.setGear(slot, ItemStack.EMPTY);
            }
        }
    }
}
