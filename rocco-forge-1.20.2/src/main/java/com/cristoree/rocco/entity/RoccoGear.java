package com.cristoree.rocco.entity;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

// El equipo de Rocco: que se puede poner en cada lugar, con que animacion pega cada arma y que
// textura y hueso del modelo le tocan. Lo usan el servidor (menu, #equipar, combate) y el
// cliente (las capas que dibujan la armadura y las armas).
public final class RoccoGear
{
    public static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

    // Como pega con lo que tiene en la mano.
    public enum Attack
    {
        PUNCH("attack_punch", 9, 3),
        SWING("attack_swing", 12, 5),
        CHOP("attack_chop", 15, 7),
        THRUST("attack_thrust", 12, 6),
        BOW("bow_release", 9, 1),
        CROSSBOW("crossbow_shoot", 7, 1);

        private final String animation;
        private final int ticks;
        private final int impactTick;

        Attack(String animation, int ticks, int impactTick)
        {
            this.animation = animation;
            this.ticks = ticks;
            this.impactTick = impactTick;
        }

        public String animation()
        {
            return this.animation;
        }

        public int ticks()
        {
            return this.ticks;
        }

        public int impactTick()
        {
            return this.impactTick;
        }

        public boolean ranged()
        {
            return this == BOW || this == CROSSBOW;
        }
    }

    // ---------- que va en cada lugar ----------

    // Casco, pechera, pantalones y botas: solo armaduras de ese tipo (nada de calabazas, cabezas
    // ni elitros: Rocco ya vuela con sus hojas).
    public static boolean fitsArmor(EquipmentSlot slot, ItemStack stack)
    {
        return stack.getItem() instanceof ArmorItem armor && armor.getEquipmentSlot() == slot;
    }

    // La mano del arma: espadas, hachas, tridentes, arcos y ballestas.
    public static boolean isWeapon(ItemStack stack)
    {
        return stack.getItem() instanceof SwordItem
                || stack.getItem() instanceof AxeItem
                || stack.getItem() instanceof TridentItem
                || stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isShield(ItemStack stack)
    {
        return stack.getItem() instanceof ShieldItem;
    }

    public static boolean fits(EquipmentSlot slot, ItemStack stack)
    {
        if (stack.isEmpty())
        {
            return true;
        }
        return switch (slot)
        {
            case MAINHAND -> isWeapon(stack);
            case OFFHAND -> isShield(stack);
            default -> fitsArmor(slot, stack);
        };
    }

    public static Attack attackFor(ItemStack weapon)
    {
        if (weapon.getItem() instanceof AxeItem)
        {
            return Attack.CHOP;
        }
        if (weapon.getItem() instanceof TridentItem)
        {
            return Attack.THRUST;
        }
        if (weapon.getItem() instanceof BowItem)
        {
            return Attack.BOW;
        }
        if (weapon.getItem() instanceof CrossbowItem)
        {
            return Attack.CROSSBOW;
        }
        return weapon.isEmpty() ? Attack.PUNCH : Attack.SWING;
    }

    // Para elegir lo mejor del inventario con #equipar: armadura por defensa y dureza, armas por
    // daño (los arcos y ballestas valen poco: los elige solo si no hay nada para cuerpo a cuerpo).
    public static double armorValue(ItemStack stack)
    {
        if (!(stack.getItem() instanceof ArmorItem armor))
        {
            return -1.0D;
        }
        return armor.getDefense() * 10.0D + armor.getToughness() * 4.0D + stack.getEnchantmentTags().size();
    }

    public static double weaponValue(ItemStack stack)
    {
        if (stack.getItem() instanceof SwordItem sword)
        {
            return 10.0D + sword.getDamage() + stack.getEnchantmentTags().size();
        }
        if (stack.getItem() instanceof AxeItem axe)
        {
            return 9.0D + axe.getAttackDamage() + stack.getEnchantmentTags().size();
        }
        if (stack.getItem() instanceof TridentItem)
        {
            return 17.0D + stack.getEnchantmentTags().size();
        }
        if (stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem)
        {
            return 2.0D + stack.getEnchantmentTags().size();
        }
        return -1.0D;
    }

    // ---------- como se ve ----------

    // El nombre de la textura de armadura para ese material (armaduras de otros mods: la mas
    // parecida por nombre, y si no se sabe, hierro).
    public static String armorMaterial(ArmorItem armor)
    {
        ArmorMaterial material = armor.getMaterial();
        if (material == ArmorMaterials.LEATHER) return "leather";
        if (material == ArmorMaterials.CHAIN) return "chainmail";
        if (material == ArmorMaterials.IRON) return "iron";
        if (material == ArmorMaterials.GOLD) return "gold";
        if (material == ArmorMaterials.DIAMOND) return "diamond";
        if (material == ArmorMaterials.NETHERITE) return "netherite";
        if (material == ArmorMaterials.TURTLE) return "turtle";
        String name = material.getName().toLowerCase(Locale.ROOT);
        for (String known : new String[]{"netherite", "diamond", "gold", "chain", "leather", "turtle"})
        {
            if (name.contains(known))
            {
                return known.equals("chain") ? "chainmail" : known;
            }
        }
        return "iron";
    }

    // El casco de oro es una coronita: con el puesto se esconden las hojitas de la cabeza.
    public static boolean wearsCrown(ItemStack helmet)
    {
        return helmet.getItem() instanceof ArmorItem armor && armor.getMaterial() == ArmorMaterials.GOLD;
    }

    // El arma 3D que trae el modelo para ese item: el hueso y la textura. null = no tiene modelo
    // propio (armas de otros mods, herramientas): se dibuja el item de siempre en la mano.
    public record WeaponLook(String bone, String texture) {}

    @Nullable
    public static WeaponLook weaponLook(ItemStack stack)
    {
        if (stack.getItem() instanceof SwordItem sword)
        {
            String tier = tierName(sword.getTier());
            return tier == null ? null : new WeaponLook("weapon_sword", tier);
        }
        if (stack.getItem() instanceof AxeItem axe)
        {
            String tier = tierName(axe.getTier());
            return tier == null ? null : new WeaponLook("weapon_axe", tier);
        }
        if (stack.getItem() instanceof TridentItem)
        {
            return new WeaponLook("weapon_trident", "special");
        }
        if (stack.getItem() instanceof BowItem)
        {
            return new WeaponLook("weapon_bow", "special");
        }
        if (stack.getItem() instanceof CrossbowItem)
        {
            return new WeaponLook("weapon_crossbow", "special");
        }
        return null;
    }

    @Nullable
    public static WeaponLook shieldLook(ItemStack stack)
    {
        return stack.getItem() instanceof ShieldItem ? new WeaponLook("weapon_shield", "special") : null;
    }

    @Nullable
    private static String tierName(Tier tier)
    {
        if (tier == Tiers.WOOD) return "wood";
        if (tier == Tiers.STONE) return "stone";
        if (tier == Tiers.IRON) return "iron";
        if (tier == Tiers.GOLD) return "gold";
        if (tier == Tiers.DIAMOND) return "diamond";
        if (tier == Tiers.NETHERITE) return "netherite";
        return null;
    }

    private RoccoGear() {}
}
