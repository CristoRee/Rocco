package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.truco.TrucoChairEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

// Las sillas del truco: el mismo modelo para todas, con la silla comun o la alta de Rocco.
public final class TrucoChairRenderer extends GeoEntityRenderer<TrucoChairEntity, TrucoChairRenderer.State>
{
    public static final class State extends EntityRenderState implements GeoRenderState
    {
        float yaw;
        boolean forRocco;
    }

    public TrucoChairRenderer(EntityRendererProvider.Context context)
    {
        super(context, new Model());
        this.shadowRadius = 0.3F;
    }

    @Override
    public State createRenderState(TrucoChairEntity chair, Void relatedObject)
    {
        return new State();
    }

    @Override
    public void addRenderData(TrucoChairEntity chair, Void relatedObject, State state, float partialTick)
    {
        super.addRenderData(chair, relatedObject, state, partialTick);
        state.yaw = chair.getYRot();
        state.forRocco = chair.forRocco();
    }

    // No es un ser vivo: GeckoLib no la gira sola. Mira hacia donde la puso la partida.
    @Override
    protected void applyRotations(RenderPassInfo<State> info, PoseStack poseStack, float nativeScale)
    {
        super.applyRotations(info, poseStack, nativeScale);
        poseStack.rotateDegrees(Axis.YP, -info.renderState().yaw);
    }

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> info, BoneSnapshots snapshots)
    {
        super.adjustModelBonesForRender(info, snapshots);
        boolean rocco = info.renderState().forRocco;
        snapshots.ifPresent("silla", bone -> bone.skipRender(rocco).skipChildrenRender(rocco));
        snapshots.ifPresent("silla_rocco", bone -> bone.skipRender(!rocco).skipChildrenRender(!rocco));
    }

    static final class Model extends GeoModel<TrucoChairEntity>
    {
        private static final Identifier GEO = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "entity/truco_silla");
        private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "textures/entity/truco_silla.png");
        private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "entity/truco_silla");

        @Override
        public Identifier getModelResource(GeoRenderState state)
        {
            return GEO;
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state)
        {
            return TEXTURE;
        }

        @Override
        public Identifier getAnimationResource(TrucoChairEntity chair)
        {
            return ANIMATION;
        }
    }
}
