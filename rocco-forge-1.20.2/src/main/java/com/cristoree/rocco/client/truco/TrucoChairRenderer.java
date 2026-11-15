package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.truco.TrucoChairEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

// Las sillas del truco: el mismo modelo para todas, con la silla comun o la alta de Rocco.
public final class TrucoChairRenderer extends GeoEntityRenderer<TrucoChairEntity>
{
    public TrucoChairRenderer(EntityRendererProvider.Context context)
    {
        super(context, new Model());
        this.shadowRadius = 0.3F;
    }

    // No es un ser vivo: GeckoLib no la gira sola. Mira hacia donde la puso la partida.
    @Override
    protected void applyRotations(TrucoChairEntity chair, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick)
    {
        super.applyRotations(chair, poseStack, ageInTicks, chair.getYRot(), partialTick);
    }

    static final class Model extends GeoModel<TrucoChairEntity>
    {
        private static final ResourceLocation GEO = new ResourceLocation(RoccoMod.MODID, "geo/entity/truco_silla.geo.json");
        private static final ResourceLocation TEXTURE = new ResourceLocation(RoccoMod.MODID, "textures/entity/truco_silla.png");
        private static final ResourceLocation ANIMATION = new ResourceLocation(RoccoMod.MODID, "animations/entity/truco_silla.animation.json");

        @Override
        public ResourceLocation getModelResource(TrucoChairEntity chair)
        {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(TrucoChairEntity chair)
        {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(TrucoChairEntity chair)
        {
            return ANIMATION;
        }

        @Override
        public void setCustomAnimations(TrucoChairEntity chair, long instanceId, AnimationState<TrucoChairEntity> state)
        {
            super.setCustomAnimations(chair, instanceId, state);
            boolean rocco = chair.forRocco();
            getBone("silla").ifPresent(bone ->
            {
                bone.setHidden(rocco);
                bone.setChildrenHidden(rocco);
            });
            getBone("silla_rocco").ifPresent(bone ->
            {
                bone.setHidden(!rocco);
                bone.setChildrenHidden(!rocco);
            });
        }
    }
}
