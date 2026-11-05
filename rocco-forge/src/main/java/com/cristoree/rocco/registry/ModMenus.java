package com.cristoree.rocco.registry;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.menu.RoccoEquipmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus
{
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, RoccoMod.MODID);

    // El equipo de Rocco: armadura, arma y escudo, con tu inventario abajo.
    public static final RegistryObject<MenuType<RoccoEquipmentMenu>> EQUIPMENT = MENUS.register("equipment",
            () -> IForgeMenuType.create(RoccoEquipmentMenu::fromNetwork));

    // Su inventario: se agranda con las mochilas, con una pagina cada 6 filas.
    public static final RegistryObject<MenuType<com.cristoree.rocco.menu.RoccoInventoryMenu>> INVENTORY = MENUS.register("inventory",
            () -> IForgeMenuType.create(com.cristoree.rocco.menu.RoccoInventoryMenu::fromNetwork));

    public static void register(IEventBus modEventBus)
    {
        MENUS.register(modEventBus);
    }

    private ModMenus() {}
}
