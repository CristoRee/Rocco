package com.cristoree.rocco.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

// Rocco en chiquito dentro de los menus, con los pies en (x, y), igual que en 1.20.1. Desde 1.20.2
// el juego centra el modelo en un recuadro y lo recorta; aca se mantiene el dibujo de siempre.
public final class EntityPreview
{
    private static final Vector3f NO_OFFSET = new Vector3f();

    // Mira hacia el mouse: lookX y lookY son la distancia del punto de los ojos al mouse.
    public static void followMouse(GuiGraphics graphics, int x, int y, int scale, float lookX, float lookY, LivingEntity entity)
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

    public static void render(GuiGraphics graphics, int x, int y, int scale, Quaternionf pose, Quaternionf camera, LivingEntity entity)
    {
        com.cristoree.rocco.client.RoccoRenderer.inGui = true;
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, NO_OFFSET, pose, camera, entity);
        com.cristoree.rocco.client.RoccoRenderer.inGui = false;
    }

    private EntityPreview() {}
}
