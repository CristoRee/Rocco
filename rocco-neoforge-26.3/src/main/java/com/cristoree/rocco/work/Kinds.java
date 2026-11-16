package com.cristoree.rocco.work;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

// Que tipo de cosa es un item. Desde 1.21.5 las espadas, hachas, picos y armaduras ya no son
// clases propias: se reconocen por sus etiquetas (#minecraft:swords...) y sus componentes.
// Asi tambien entran las de otros mods que usan esas etiquetas.
public final class Kinds
{
    public static boolean isSword(ItemStack stack) { return stack.is(ItemTags.SWORDS); }
    public static boolean isAxe(ItemStack stack) { return stack.is(ItemTags.AXES); }
    public static boolean isPickaxe(ItemStack stack) { return stack.is(ItemTags.PICKAXES); }
    public static boolean isShovel(ItemStack stack) { return stack.is(ItemTags.SHOVELS); }
    public static boolean isHoe(ItemStack stack) { return stack.is(ItemTags.HOES); }

    public static boolean isSword(Item item) { return isSword(item.getDefaultInstance()); }
    public static boolean isDigger(Item item)
    {
        ItemStack stack = item.getDefaultInstance();
        return isPickaxe(stack) || isAxe(stack) || isShovel(stack) || isHoe(stack);
    }

    // La armadura de verdad: se pone en la cabeza, el pecho, las piernas o los pies y protege.
    // Asi quedan afuera la calabaza tallada, las cabezas y los elitros.
    @Nullable
    public static EquipmentSlot armorSlot(ItemStack stack)
    {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.slot().getType() != EquipmentSlot.Type.HUMANOID_ARMOR)
        {
            return null;
        }
        return attribute(stack, equippable.slot(), Attributes.ARMOR) > 0.0D ? equippable.slot() : null;
    }

    public static boolean isArmor(ItemStack stack)
    {
        return armorSlot(stack) != null;
    }

    public static boolean isArmor(Item item)
    {
        return isArmor(item.getDefaultInstance());
    }

    public static double defense(ItemStack stack)
    {
        EquipmentSlot slot = armorSlot(stack);
        return slot == null ? 0.0D : attribute(stack, slot, Attributes.ARMOR);
    }

    public static double toughness(ItemStack stack)
    {
        EquipmentSlot slot = armorSlot(stack);
        return slot == null ? 0.0D : attribute(stack, slot, Attributes.ARMOR_TOUGHNESS);
    }

    // De que esta hecha la armadura (el id de su aspecto: minecraft:iron, minecraft:chainmail...).
    public static String armorAsset(ItemStack stack)
    {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null || equippable.assetId().isEmpty())
        {
            return "";
        }
        return equippable.assetId().get().identifier().getPath().toLowerCase(Locale.ROOT);
    }

    // El material de una herramienta o arma por su nombre: wooden_, stone_, iron_... ("" si no se sabe).
    public static String material(ItemStack stack)
    {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        for (String prefix : new String[]{"wooden", "stone", "copper", "iron", "golden", "diamond", "netherite"})
        {
            if (path.startsWith(prefix + "_"))
            {
                return prefix;
            }
        }
        return "";
    }

    // El nivel del material como en 1.20: madera y oro 0, piedra y cobre 1, hierro 2, diamante 3, netherite 4.
    public static int level(ItemStack stack)
    {
        return switch (material(stack))
        {
            case "stone", "copper" -> 1;
            case "iron" -> 2;
            case "diamond" -> 3;
            case "netherite" -> 4;
            default -> 0;
        };
    }

    // Que tan rapido rompe el material (los valores del juego).
    public static float speed(ItemStack stack)
    {
        return switch (material(stack))
        {
            case "wooden" -> 2.0F;
            case "stone" -> 4.0F;
            case "copper" -> 5.0F;
            case "iron" -> 6.0F;
            case "diamond" -> 8.0F;
            case "netherite" -> 9.0F;
            case "golden" -> 12.0F;
            default -> 1.0F;
        };
    }

    // Lo que suma el item a un atributo cuando esta en ese lugar (el daño de un arma, la defensa de una armadura).
    public static double attribute(ItemStack stack, EquipmentSlot slot, net.minecraft.core.Holder<Attribute> wanted)
    {
        double[] total = {0.0D};
        stack.getAttributeModifiers().forEach(slot, (attribute, modifier) ->
        {
            if (attribute.is(wanted) && modifier.operation() == AttributeModifier.Operation.ADD_VALUE)
            {
                total[0] += modifier.amount();
            }
        });
        return total[0];
    }

    public static double attackDamage(ItemStack stack)
    {
        return attribute(stack, EquipmentSlot.MAINHAND, Attributes.ATTACK_DAMAGE);
    }

    private Kinds() {}
}
