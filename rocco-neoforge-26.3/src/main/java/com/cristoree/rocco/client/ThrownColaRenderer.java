package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.ThrownColaBottle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;

// La botella 3D dando vueltas en el aire (el modelo de piso, como cuando esta tirada).
public final class ThrownColaRenderer extends EntityRenderer<ThrownColaBottle, ThrownColaRenderer.State>
{
    public static final class State extends EntityRenderState
    {
        final ItemStackRenderState item = new ItemStackRenderState();
        boolean hidden;
        float yaw;
        float spin;
    }

    private final ItemModelResolver items;

    public ThrownColaRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.items = context.getItemModelResolver();
        this.shadowRadius = 0.15F;
    }

    @Override
    public State createRenderState()
    {
        return new State();
    }

    @Override
    public void extractRenderState(ThrownColaBottle bottle, State state, float partialTick)
    {
        super.extractRenderState(bottle, state, partialTick);
        // Recien salida, pegada a la cara del que la tiro: igual que las bolas de nieve.
        Entity viewer = this.entityRenderDispatcher.camera == null ? null : this.entityRenderDispatcher.camera.entity();
        state.hidden = bottle.tickCount < 2 && viewer != null && viewer.distanceToSqr(bottle) < 12.25D;
        state.yaw = Mth.lerp(partialTick, bottle.yRotO, bottle.getYRot());
        state.spin = (bottle.tickCount + partialTick) * 25.0F;
        this.items.updateForNonLiving(state.item, bottle.getItem(), ItemDisplayContext.GROUND, bottle);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (!state.hidden && !state.item.isEmpty())
        {
            pose.pushPose();
            pose.translate(0.0F, 0.15F, 0.0F);
            pose.rotate(Axis.YP.rotationDegrees(-state.yaw));
            pose.rotate(Axis.XP.rotationDegrees(state.spin));
            state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, state.outlineColor);
            pose.popPose();
        }
        super.submit(state, pose, collector, camera);
    }
}
