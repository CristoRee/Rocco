package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.menu.RoccoEquipmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RoccoMod.MODID);

    // El equipo de Rocco: armadura, arma y escudo, con tu inventario abajo.
    public static final DeferredHolder<MenuType<?>, MenuType<RoccoEquipmentMenu>> EQUIPMENT = MENUS.register("equipment",
            () -> IMenuTypeExtension.create(RoccoEquipmentMenu::fromNetwork));

    // Su inventario: se agranda con las mochilas, con una pagina cada 6 filas.
    public static final DeferredHolder<MenuType<?>, MenuType<com.cristoree.rocco.menu.RoccoInventoryMenu>> INVENTORY = MENUS.register("inventory",
            () -> IMenuTypeExtension.create(com.cristoree.rocco.menu.RoccoInventoryMenu::fromNetwork));

    public static void register(IEventBus modEventBus)
    {
        MENUS.register(modEventBus);
    }

    private ModMenus() {}
}
