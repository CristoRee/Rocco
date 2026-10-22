package com.cristoree.rocco.client;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
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
}
