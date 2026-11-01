package com.cristoree.rocco.client;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoGear;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.ForgeHooksClient;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

public class RoccoRenderer extends GeoEntityRenderer<RoccoEntity>
{
    // La punta de la caña en la textura del juego (pixel 14,1 de fishing_rod.png), en el espacio
    // 0..1 del modelo del item.
    private static final float ROD_TIP_X = 14.5F / 16.0F;
    private static final float ROD_TIP_Y = 1.0F - 1.5F / 16.0F;

    // La pose al arrancar a dibujar este Rocco (antes de la escala) y donde estaba en ese cuadro:
    // con eso se pasa la punta de la caña a coordenadas del mundo.
    private Matrix4f renderOrigin = new Matrix4f();
    private Vec3 renderPosition = Vec3.ZERO;
    @Nullable
    private Vec3 rodTip;

    public RoccoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new RoccoModel());
        this.shadowRadius = 0.4F;

        // item_right e item_left son huesos vacios en las palmas: ahi se dibuja lo que sostiene.
        addRenderLayer(new BlockAndItemGeoLayer<>(this)
        {
            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, RoccoEntity animatable,
                                              MultiBufferSource bufferSource, float partialTick, int packedLight,
                                              int packedOverlay)
            {
                // El cofre que flota: a un tercio de bloque (FIXED lo dibuja a medio bloque).
                boolean grab = bone.getName().equals("grab");
                if (grab)
                {
                    poseStack.pushPose();
                    poseStack.scale(0.66F, 0.66F, 0.66F);
                }
                super.renderStackForBone(poseStack, bone, stack, animatable, bufferSource, partialTick, packedLight,
                        packedOverlay);
                if (grab)
                {
                    poseStack.popPose();
                }
                if (stack.getItem() instanceof FishingRodItem && bone.getName().equals("item_right"))
                {
                    rememberRodTip(poseStack, stack, animatable, getTransformTypeForStack(bone, stack, animatable));
                }
            }

            @Override
            protected ItemStack getStackForBone(GeoBone bone, RoccoEntity animatable)
            {
                return switch (bone.getName())
                {
                    case "item_right" -> heldFor(animatable, animatable.getMainHandItem(), RoccoGear.weaponLook(animatable.getMainHandItem()));
                    case "item_left" -> heldFor(animatable, animatable.getOffhandItem(), RoccoGear.shieldLook(animatable.getOffhandItem()));
                    // El cofre de ender que se traga o que absorbe, flotando entre las manitos.
                    case "grab" -> animatable.grabbedItem();
                    default -> ItemStack.EMPTY;
                };
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack,
                                                                  RoccoEntity animatable)
            {
                return switch (bone.getName())
                {
                    case "item_left" -> ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
                    case "grab" -> ItemDisplayContext.FIXED;
                    default -> ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
                };
            }
        });

        // La armadura y las armas 3D, cada una con la textura de su material.
        addRenderLayer(new RoccoGearLayer(this));

        // Las esferitas de magia brillan en la oscuridad: rocco_glowmask.png marca solo esas partes.
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    // En la mano: lo que tenga, salvo un arma con modelo propio (la dibuja RoccoGearLayer) y salvo
    // un arma con las manos ocupadas (tomando mate, volando, nadando...).
    private static ItemStack heldFor(RoccoEntity rocco, ItemStack stack, @Nullable RoccoGear.WeaponLook look)
    {
        if (stack.isEmpty() || look != null || !RoccoClientConfig.SHOW_HELD_ITEMS.get())
        {
            return ItemStack.EMPTY;
        }
        if ((RoccoGear.isWeapon(stack) || RoccoGear.isShield(stack))
                && RoccoGearLayer.handsBusy(rocco, RoccoModel.playingAnimation(rocco)))
        {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    // Al morir hace su animacion de caida: sin el giro de costado del juego.
    @Override
    protected float getDeathMaxRotation(RoccoEntity animatable)
    {
        return 0.0F;
    }

    @Override
    public void preRender(PoseStack poseStack, RoccoEntity animatable, BakedGeoModel model,
                          MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender,
                          float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            this.renderOrigin = new Matrix4f(poseStack.last().pose());
            this.renderPosition = new Vec3(Mth.lerp(partialTick, animatable.xOld, animatable.getX()),
                    Mth.lerp(partialTick, animatable.yOld, animatable.getY()),
                    Mth.lerp(partialTick, animatable.zOld, animatable.getZ()));

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
    public void render(RoccoEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight)
    {
        this.rodTip = null;
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        RoccoFishingLine.render(entity, this.rodTip, partialTick, poseStack, bufferSource, this.entityRenderDispatcher);
    }

    @Override
    public boolean shouldRender(RoccoEntity entity, Frustum frustum, double camX, double camY, double camZ)
    {
        return super.shouldRender(entity, frustum, camX, camY, camZ) || RoccoFishingLine.isVisible(entity, frustum);
    }

    // Repite lo que hace ItemRenderer antes de dibujar el item (la pose de "en la mano" del modelo
    // y el corrimiento al centro) para saber donde quedo la punta, y la pasa a coordenadas del mundo.
    private void rememberRodTip(PoseStack poseStack, ItemStack stack, RoccoEntity rocco, ItemDisplayContext context)
    {
        poseStack.pushPose();
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, rocco.level(), rocco, rocco.getId());
        ForgeHooksClient.handleCameraTransforms(poseStack, model, context, false);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        Vector4f tip = poseStack.last().pose().transform(new Vector4f(ROD_TIP_X, ROD_TIP_Y, 0.5F, 1.0F));
        poseStack.popPose();

        Vector4f local = new Matrix4f(this.renderOrigin).invert().transform(tip);
        this.rodTip = this.renderPosition.add(local.x, local.y, local.z);
    }

    // Volando, las animaciones ya lo acuestan; ademas se inclina con el jugador (sube y baja con
    // el), igual que el juego inclina al jugador con el elytra. Gira alrededor del centro del
    // modelo para que no se despegue del lugar.
    @Override
    protected void applyRotations(RoccoEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick)
    {
        super.applyRotations(animatable, poseStack, ageInTicks, rotationYaw, partialTick);
        if (animatable.getMotion().tiltsWithPlayer())
        {
            float pitch = Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot());
            float center = animatable.getBbHeight() * 0.5F;
            poseStack.translate(0.0F, center, 0.0F);
            poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-pitch));
            poseStack.translate(0.0F, -center, 0.0F);
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
