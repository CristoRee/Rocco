package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoGear;
import com.cristoree.rocco.entity.RoccoPose;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// La armadura y las armas 3D de Rocco. Las piezas ya estan dentro de su modelo (transparentes en
// rocco.png); cada material tiene su textura pintada solo en sus piezas. Para dibujar una se
// vuelve a dibujar el mismo modelo, con la misma animacion, dejando visibles solo los huesos de
// esa pieza (RoccoRenderer hace una pasada por cada una): asi acompaña cada animacion (la tapa,
// tragar, volar, el barco) sin codigo extra, y la cuerda del arco y de la ballesta se tensa sola.
public final class RoccoGearLayer
{
    public static final Map<EquipmentSlot, List<String>> ARMOR_BONES = Map.of(
            EquipmentSlot.HEAD, List.of("armor_head", "armor_head_jaw", "armor_head_top"),
            EquipmentSlot.CHEST, List.of("armor_chest", "armor_chest_right", "armor_chest_left",
                    "armor_chest_right_wrist", "armor_chest_left_wrist"),
            EquipmentSlot.LEGS, List.of("armor_legs", "armor_legs_right", "armor_legs_left"),
            EquipmentSlot.FEET, List.of("armor_feet_right", "armor_feet_left"));
    public static final List<String> WEAPON_BONES =
            List.of("weapon_sword", "weapon_axe", "weapon_trident", "weapon_bow", "weapon_crossbow", "weapon_shield");
    // Todo lo que la pasada normal de Rocco no dibuja (son transparentes igual, pero no gastan).
    public static final List<String> ALL_GEAR_BONES;

    static
    {
        List<String> all = new ArrayList<>();
        ARMOR_BONES.values().forEach(all::addAll);
        all.addAll(WEAPON_BONES);
        ALL_GEAR_BONES = List.copyOf(all);
    }

    // Las que brillan un poco a oscuras: gemas, el cuerno de cristal y las almohadillas del gatito.
    private static final Set<String> GLOWING = Set.of("gold", "diamond", "netherite");
    private static final int WHITE = 0xFFFFFFFF;

    private static final Map<String, Identifier> TEXTURES = new HashMap<>();

    // Una pasada: que huesos se ven, con que textura y de que color.
    public record Pass(List<String> bones, Identifier texture, RenderType type, int color) {}

    // Las pasadas de este cuadro (vacio si no lleva nada que dibujar).
    public static List<Pass> passes(RoccoEntity rocco)
    {
        List<Pass> out = new ArrayList<>();
        // Plantado o naciendo, la tierra lo tapa: no se dibuja nada encima.
        if (rocco.isPlantVisible() || rocco.isInvisible())
        {
            return out;
        }
        if (RoccoClientConfig.SHOW_ARMOR.get())
        {
            for (EquipmentSlot slot : RoccoGear.ARMOR_SLOTS)
            {
                ItemStack stack = rocco.getItemBySlot(slot);
                if (com.cristoree.rocco.work.Kinds.isArmor(stack))
                {
                    armor(out, ARMOR_BONES.get(slot), stack);
                }
            }
        }
        ItemStack main = rocco.getMainHandItem();
        ItemStack off = rocco.getOffhandItem();
        RoccoGear.WeaponLook mainLook = RoccoGear.weaponLook(main);
        RoccoGear.WeaponLook offLook = RoccoGear.shieldLook(off);
        if (RoccoClientConfig.SHOW_HELD_ITEMS.get() && (mainLook != null || offLook != null)
                && !handsBusy(rocco, RoccoModel.playingAnimation(rocco)))
        {
            if (mainLook != null)
            {
                weapon(out, main, mainLook);
            }
            if (offLook != null)
            {
                weapon(out, off, offLook);
            }
        }
        return out;
    }

    private static void armor(List<Pass> out, List<String> bones, ItemStack stack)
    {
        String material = RoccoGear.armorMaterial(stack);
        boolean foil = stack.hasFoil();
        if (material.equals("leather"))
        {
            // El cuero toma el color con el que lo teñiste; los adornos (margarita, parche) no.
            int color = 0xFF000000 | net.minecraft.world.item.component.DyedItemColor.getOrDefault(stack, 0xA06540);
            Identifier leather = armorTexture("leather");
            out.add(new Pass(bones, leather, foil ? RenderTypes.armorCutoutNoCullGlint(leather) : RenderTypes.armorCutoutNoCull(leather), color));
            Identifier overlay = armorTexture("leather_overlay");
            out.add(new Pass(bones, overlay, RenderTypes.armorCutoutNoCull(overlay), WHITE));
        }
        else
        {
            Identifier texture = armorTexture(material);
            out.add(new Pass(bones, texture, foil ? RenderTypes.armorCutoutNoCullGlint(texture) : RenderTypes.armorCutoutNoCull(texture), WHITE));
        }
        if (GLOWING.contains(material))
        {
            Identifier glow = armorTexture(material + "_glow");
            out.add(new Pass(bones, glow, RenderTypes.eyes(glow), WHITE));
        }
    }

    private static void weapon(List<Pass> out, ItemStack stack, RoccoGear.WeaponLook look)
    {
        Identifier texture = weaponTexture(look.texture());
        out.add(new Pass(List.of(look.bone()), texture,
                stack.hasFoil() ? RenderTypes.armorCutoutNoCullGlint(texture) : RenderTypes.entityCutout(texture), WHITE));
    }

    // Con las manos ocupadas no se ve el arma: tomando mate o Roco Cola, cantando con el
    // microfono, volando (sostiene las hojas-ala), nadando, remando, encantando, comiendo o
    // tragando, sentado, dormido, en la tumba o naciendo.
    public static boolean handsBusy(RoccoEntity rocco, String playing)
    {
        if (rocco.getRoccoState() != RoccoState.ACTIVE || rocco.isSharingMate() || rocco.isTravelling()
                || rocco.getRoccoPose() != RoccoPose.STANDING)
        {
            return true;
        }
        // Cocinando o con las cartas en la mano: el arma queda guardada.
        com.cristoree.rocco.entity.RoccoScene scene = rocco.getScene();
        if (scene.cooking() || scene.truco() || playing.startsWith("animation.rocco.asado")
                || playing.startsWith("animation.rocco.tortas") || playing.startsWith("animation.rocco.truco"))
        {
            return true;
        }
        RoccoTask task = rocco.getActiveTask();
        return switch (task)
        {
            case MATE, COLA, ENCHANTING, EATING, SWALLOWING, ENDER_ABSORB, ENDER_AWAKEN, DANCING, CLAPPING, CELEBRATING,
                 FOOD_EAT, FOOD_EAT_BAD, POTION_DRINK, POTION_DRINK_BAD,
                 DANCE_FIESTA, DANCE_MENEITO, SING_SOFT, SING_GROOVE, SING_RAP,
                 GIVE_SEED, GIVE_SEED_CHILL, GIVE_FLOWER, GIVE_FLOWER_CHILL, GIVE_COLA, REACT_BURP, BACKPACK_SHOW -> true;
            default -> playing.startsWith("animation.rocco.sing") || playing.startsWith("animation.rocco.mate")
                    || playing.startsWith("animation.rocco.cola") || playing.startsWith("animation.rocco.enchant")
                    || playing.startsWith("animation.rocco.dance") || playing.startsWith("animation.rocco.food_eat")
                    || playing.startsWith("animation.rocco.potion_drink") || playing.startsWith("animation.rocco.give_")
                    || playing.equals("animation.rocco.react_burp") || playing.equals("animation.rocco.backpack_show");
        };
    }

    private static Identifier armorTexture(String material)
    {
        return TEXTURES.computeIfAbsent("armor/rocco_armor_" + material,
                path -> Identifier.fromNamespaceAndPath(RoccoMod.MODID, "textures/entity/" + path + ".png"));
    }

    private static Identifier weaponTexture(String material)
    {
        return TEXTURES.computeIfAbsent("weapon/rocco_weapon_" + material,
                path -> Identifier.fromNamespaceAndPath(RoccoMod.MODID, "textures/entity/" + path + ".png"));
    }

    private RoccoGearLayer() {}
}
