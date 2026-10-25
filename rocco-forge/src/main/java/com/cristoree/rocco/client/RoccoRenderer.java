package com.cristoree.rocco.client;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class RoccoRenderer extends GeoEntityRenderer<RoccoEntity>
{
    public RoccoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new RoccoModel());
        this.shadowRadius = 0.4F;

        // item_right e item_left son huesos vacios en las palmas: ahi se dibuja lo que sostiene.
        addRenderLayer(new BlockAndItemGeoLayer<>(this)
        {
            @Override
            protected ItemStack getStackForBone(GeoBone bone, RoccoEntity animatable)
            {
                return switch (bone.getName())
                {
                    case "item_right" -> animatable.getMainHandItem();
                    case "item_left" -> animatable.getOffhandItem();
                    default -> ItemStack.EMPTY;
                };
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                                  RoccoEntity animatable)
            {
                return bone.getName().equals("item_left")
                        ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                        : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
            }
        });

        // Las esferitas de magia brillan en la oscuridad: rocco_glowmask.png marca solo esas partes.
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void preRender(PoseStack poseStack, RoccoEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            float scale = RoccoClientConfig.MODEL_SCALE.get().floatValue();
            poseStack.scale(scale, scale, scale);
        }

        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    // Despues de dibujar, anota donde quedaron los localizadores (cabeza, mate, manos) para que
    // las particulas salgan justo de ahi.
    @Override
    public void renderFinal(PoseStack poseStack, RoccoEntity animatable, BakedGeoModel model,
                            MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick,
                            int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);

        for (String name : RoccoModel.LOCATORS)
        {
            model.getBone(name).ifPresent(bone ->
            {
                if (bone.isTrackingMatrices())
                {
                    Vector3d world = bone.getWorldPosition();
                    RoccoParticleFx.rememberLocator(animatable, name, new Vec3(world.x, world.y, world.z));
                }
            });
        }
    }

    @Override
    public boolean shouldShowName(RoccoEntity animatable)
    {
        if (!RoccoClientConfig.SHOW_NAMEPLATE.get() || !Minecraft.renderNames() || animatable.isPlantVisible())
        {
            return false;
        }
        double max = RoccoClientConfig.NAMEPLATE_DISTANCE.get();
        return this.entityRenderDispatcher.distanceToSqr(animatable) <= max * max;
    }
}
