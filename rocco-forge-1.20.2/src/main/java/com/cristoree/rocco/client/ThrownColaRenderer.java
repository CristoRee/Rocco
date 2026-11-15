package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.ThrownColaBottle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

// La botella 3D dando vueltas en el aire (el modelo de piso, como cuando esta tirada).
public final class ThrownColaRenderer extends EntityRenderer<ThrownColaBottle>
{
    private final ItemRenderer items;

    public ThrownColaRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.items = context.getItemRenderer();
        this.shadowRadius = 0.15F;
    }

    @Override
    public void render(ThrownColaBottle bottle, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light)
    {
        if (bottle.tickCount < 2 && this.entityRenderDispatcher.camera.getEntity().distanceToSqr(bottle) < 12.25D)
        {
            // Recien salida, pegada a la cara del que la tiro: igual que las bolas de nieve.
            return;
        }
        pose.pushPose();
        pose.translate(0.0F, 0.15F, 0.0F);
        pose.mulPose(Axis.YP.rotationDegrees(-yaw));
        pose.mulPose(Axis.XP.rotationDegrees((bottle.tickCount + partialTick) * 25.0F));
        this.items.renderStatic(bottle.getItem(), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, pose, buffers, bottle.level(), bottle.getId());
        pose.popPose();
        super.render(bottle, yaw, partialTick, pose, buffers, light);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(ThrownColaBottle bottle)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
