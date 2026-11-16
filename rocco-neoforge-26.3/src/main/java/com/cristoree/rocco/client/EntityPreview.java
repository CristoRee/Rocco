package com.cristoree.rocco.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Rocco en chiquito dentro de los menus, con los pies en (x, y), igual que en 1.20.1. El juego lo
// dibuja centrado en un recuadro (y lo recorta ahi): el recuadro se arma alrededor de esos pies.
public final class EntityPreview
{

    // Mira hacia el mouse: lookX y lookY son la distancia del punto de los ojos al mouse.
    public static void followMouse(GuiGraphicsExtractor graphics, int x, int y, int scale, float lookX, float lookY, LivingEntity entity)
    {
        float yaw = (float) Math.atan(lookX / 40.0F);
        float pitch = (float) Math.atan(lookY / 40.0F);
        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch * 20.0F * ((float) Math.PI / 180.0F));
        pose.mul(camera);
        float body = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headOld = entity.yHeadRotO;
        float head = entity.yHeadRot;
        entity.yBodyRot = 180.0F + yaw * 20.0F;
        entity.setYRot(180.0F + yaw * 40.0F);
        entity.setXRot(-pitch * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();
        render(graphics, x, y, scale, pose, camera, entity);
        entity.yBodyRot = body;
        entity.setYRot(yRot);
        entity.setXRot(xRot);
        entity.yHeadRotO = headOld;
        entity.yHeadRot = head;
    }

    public static void render(GuiGraphicsExtractor graphics, int x, int y, int scale, Quaternionf pose, Quaternionf camera, LivingEntity entity)
    {
        net.minecraft.client.renderer.entity.state.EntityRenderState state = net.minecraft.client.Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(entity).createRenderState(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        // En el menu no van el cartel con el nombre, los corazones ni el hilo de la caña.
        state.nameTag = null;
        state.scoreText = null;
        if (state instanceof RoccoRenderer.State rocco)
        {
            rocco.hearts = null;
            rocco.line = null;
        }
        float height = state.boundingBoxHeight;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living && living.scale != 0.0F)
        {
            height /= living.scale;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight = height;
            living.scale = 1.0F;
        }
        // El centro del modelo queda en el centro del recuadro: los pies, 'height / 2' mas abajo.
        int middle = Math.round(y - height * scale / 2.0F);
        int halfWidth = Math.round(scale * 1.2F) + 8;
        int halfHeight = Math.round(height * scale / 2.0F + scale * 0.6F) + 8;
        graphics.entity(state, scale, new Vector3f(0.0F, height / 2.0F, 0.0F), pose, camera,
                x - halfWidth, middle - halfHeight, x + halfWidth, middle + halfHeight);
    }

    private EntityPreview() {}
}
