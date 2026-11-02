package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoGear;
import com.cristoree.rocco.entity.RoccoPose;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.entity.RoccoTask;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// La armadura y las armas 3D de Rocco. Las piezas ya estan dentro de su modelo (transparentes en
// rocco.png); cada material tiene su textura pintada solo en sus piezas. Para dibujar una se
// vuelve a renderizar el mismo modelo, con la misma animacion, dejando visibles solo los huesos de
// esa pieza: asi acompaña cada animacion (la tapa, tragar, volar, el barco) sin codigo extra, y la
// cuerda del arco y de la ballesta se tensa sola.
public class RoccoGearLayer extends GeoRenderLayer<RoccoEntity>
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

    private final Map<String, ResourceLocation> textures = new HashMap<>();

    // Los huesos del modelo horneado (cambia solo al recargar los recursos).
    private BakedGeoModel indexed;
    private List<GeoBone> bones = List.of();
    private final Map<String, GeoBone> byName = new HashMap<>();
    private boolean[] hiddenBefore = new boolean[0];
    private boolean[] childrenHiddenBefore = new boolean[0];

    public RoccoGearLayer(GeoRenderer<RoccoEntity> renderer)
    {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, RoccoEntity rocco, BakedGeoModel model, RenderType renderType,
                       MultiBufferSource buffers, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        // Plantado o naciendo, la tierra lo tapa: no se dibuja nada encima.
        if (rocco.isPlantVisible() || rocco.isInvisible())
        {
            return;
        }
        boolean armor = RoccoClientConfig.SHOW_ARMOR.get() && hasArmor(rocco);
        ItemStack main = rocco.getMainHandItem();
        ItemStack off = rocco.getOffhandItem();
        RoccoGear.WeaponLook mainLook = RoccoGear.weaponLook(main);
        RoccoGear.WeaponLook offLook = RoccoGear.shieldLook(off);
        boolean weapons = RoccoClientConfig.SHOW_HELD_ITEMS.get() && (mainLook != null || offLook != null)
                && !handsBusy(rocco, RoccoModel.playingAnimation(rocco));
        if (!armor && !weapons)
        {
            return;
        }

        index(model);
        snapshot();
        try
        {
            if (armor)
            {
                for (EquipmentSlot slot : RoccoGear.ARMOR_SLOTS)
                {
                    ItemStack stack = rocco.getItemBySlot(slot);
                    if (stack.getItem() instanceof ArmorItem item)
                    {
                        showOnly(ARMOR_BONES.get(slot));
                        drawArmor(poseStack, rocco, model, buffers, partialTick, packedLight, stack, item);
                    }
                }
            }
            if (weapons)
            {
                if (mainLook != null)
                {
                    showOnly(List.of(mainLook.bone()));
                    drawWeapon(poseStack, rocco, model, buffers, partialTick, packedLight, packedOverlay, main, mainLook);
                }
                if (offLook != null)
                {
                    showOnly(List.of(offLook.bone()));
                    drawWeapon(poseStack, rocco, model, buffers, partialTick, packedLight, packedOverlay, off, offLook);
                }
            }
        }
        finally
        {
            restore();
        }
    }

    private void drawArmor(PoseStack poseStack, RoccoEntity rocco, BakedGeoModel model, MultiBufferSource buffers,
                           float partialTick, int packedLight, ItemStack stack, ArmorItem item)
    {
        String material = RoccoGear.armorMaterial(item);
        if (material.equals("leather"))
        {
            // El cuero toma el color con el que lo teñiste; los adornos (margarita, parche) no.
            int color = item instanceof DyeableLeatherItem dyeable ? dyeable.getColor(stack) : 0xA06540;
            draw(poseStack, rocco, model, buffers, RenderType.armorCutoutNoCull(armorTexture("leather")), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, ((color >> 16) & 0xFF) / 255.0F, ((color >> 8) & 0xFF) / 255.0F, (color & 0xFF) / 255.0F);
            draw(poseStack, rocco, model, buffers, RenderType.armorCutoutNoCull(armorTexture("leather_overlay")), partialTick,
                    packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F);
        }
        else
        {
            draw(poseStack, rocco, model, buffers, RenderType.armorCutoutNoCull(armorTexture(material)), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F);
        }
        if (stack.hasFoil())
        {
            draw(poseStack, rocco, model, buffers, RenderType.armorEntityGlint(), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F);
        }
        if (GLOWING.contains(material))
        {
            draw(poseStack, rocco, model, buffers, RenderType.eyes(armorTexture(material + "_glow")), partialTick, packedLight,
                    OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F);
        }
    }

    private void drawWeapon(PoseStack poseStack, RoccoEntity rocco, BakedGeoModel model, MultiBufferSource buffers,
                            float partialTick, int packedLight, int packedOverlay, ItemStack stack, RoccoGear.WeaponLook look)
    {
        draw(poseStack, rocco, model, buffers, RenderType.entityCutoutNoCull(weaponTexture(look.texture())), partialTick,
                packedLight, packedOverlay, 1.0F, 1.0F, 1.0F);
        if (stack.hasFoil())
        {
            draw(poseStack, rocco, model, buffers, RenderType.entityGlint(), partialTick, packedLight, packedOverlay,
                    1.0F, 1.0F, 1.0F);
        }
    }

    private void draw(PoseStack poseStack, RoccoEntity rocco, BakedGeoModel model, MultiBufferSource buffers, RenderType type,
                      float partialTick, int packedLight, int packedOverlay, float red, float green, float blue)
    {
        getRenderer().reRender(model, poseStack, buffers, rocco, type, buffers.getBuffer(type), partialTick, packedLight,
                packedOverlay, red, green, blue, 1.0F);
    }

    private static boolean hasArmor(RoccoEntity rocco)
    {
        for (EquipmentSlot slot : RoccoGear.ARMOR_SLOTS)
        {
            if (rocco.getItemBySlot(slot).getItem() instanceof ArmorItem)
            {
                return true;
            }
        }
        return false;
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
        RoccoTask task = rocco.getActiveTask();
        return switch (task)
        {
            case MATE, COLA, ENCHANTING, EATING, SWALLOWING, ENDER_ABSORB, ENDER_AWAKEN, DANCING, CLAPPING, CELEBRATING,
                 FOOD_EAT, FOOD_EAT_BAD, POTION_DRINK, POTION_DRINK_BAD -> true;
            default -> playing.startsWith("animation.rocco.sing") || playing.startsWith("animation.rocco.mate")
                    || playing.startsWith("animation.rocco.cola") || playing.startsWith("animation.rocco.enchant");
        };
    }

    private ResourceLocation armorTexture(String material)
    {
        return this.textures.computeIfAbsent("armor/rocco_armor_" + material,
                path -> new ResourceLocation(RoccoMod.MODID, "textures/entity/" + path + ".png"));
    }

    private ResourceLocation weaponTexture(String material)
    {
        return this.textures.computeIfAbsent("weapon/rocco_weapon_" + material,
                path -> new ResourceLocation(RoccoMod.MODID, "textures/entity/" + path + ".png"));
    }

    // ---------- dejar visible solo una pieza ----------

    private void index(BakedGeoModel model)
    {
        if (model == this.indexed)
        {
            return;
        }
        this.indexed = model;
        List<GeoBone> all = new ArrayList<>();
        for (GeoBone top : model.topLevelBones())
        {
            collect(top, all);
        }
        this.bones = all;
        this.byName.clear();
        all.forEach(bone -> this.byName.put(bone.getName(), bone));
        this.hiddenBefore = new boolean[all.size()];
        this.childrenHiddenBefore = new boolean[all.size()];
    }

    private static void collect(GeoBone bone, List<GeoBone> into)
    {
        into.add(bone);
        for (GeoBone child : bone.getChildBones())
        {
            collect(child, into);
        }
    }

    private void snapshot()
    {
        for (int i = 0; i < this.bones.size(); i++)
        {
            this.hiddenBefore[i] = this.bones.get(i).isHidden();
            this.childrenHiddenBefore[i] = this.bones.get(i).isHidingChildren();
        }
    }

    private void restore()
    {
        for (int i = 0; i < this.bones.size(); i++)
        {
            GeoBone bone = this.bones.get(i);
            bone.setHidden(this.hiddenBefore[i]);
            bone.setChildrenHidden(this.childrenHiddenBefore[i]);
        }
    }

    // Lo de adentro de esas piezas se ve; sus padres no dibujan sus propios cubos pero dejan pasar a
    // los hijos; todo lo demas se tapa. setHidden tambien tapa a los hijos: por eso va primero.
    private void showOnly(List<String> targets)
    {
        Set<GeoBone> path = new HashSet<>();
        Set<GeoBone> roots = new HashSet<>();
        for (String name : targets)
        {
            GeoBone bone = this.byName.get(name);
            if (bone == null)
            {
                continue;
            }
            roots.add(bone);
            for (GeoBone up = bone.getParent(); up != null; up = up.getParent())
            {
                path.add(up);
            }
        }
        for (GeoBone bone : this.bones)
        {
            if (insideAny(bone, roots))
            {
                bone.setHidden(false);
                bone.setChildrenHidden(false);
            }
            else if (path.contains(bone))
            {
                bone.setHidden(true);
                bone.setChildrenHidden(false);
            }
            else
            {
                bone.setHidden(true);
                bone.setChildrenHidden(true);
            }
        }
    }

    private static boolean insideAny(GeoBone bone, Set<GeoBone> roots)
    {
        for (GeoBone up = bone; up != null; up = up.getParent())
        {
            if (roots.contains(up))
            {
                return true;
            }
        }
        return false;
    }
}
