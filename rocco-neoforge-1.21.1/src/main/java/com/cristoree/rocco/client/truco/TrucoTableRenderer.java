package com.cristoree.rocco.client.truco;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.truco.TrucoLayout;
import com.cristoree.rocco.truco.TrucoTableEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

// La mesa del truco y lo que hay arriba (ROCCO-V14 7.3): las cartas jugadas delante de cada uno, el
// mazo y la muestra al lado de Rocco, lo que se reparte deslizandose y las cartas que barre cuando
// gana la mano. Todo en px desde el centro de la mesa, con la tapa a 12,5 px del piso.
public final class TrucoTableRenderer extends GeoEntityRenderer<TrucoTableEntity>
{
    private static final float CARD_U = 40.0F / 512.0F;
    private static final float CARD_V = 60.0F / 512.0F;
    // Los tiempos dentro de truco_deal: las tandas de cartas y la muestra.
    private static final int[] DEAL_TICKS = {35, 39, 44, 48, 53, 57};
    private static final int DEAL_END = 72;
    private static final int SLIDE_TICKS = 8;
    private static final int SWEEP_TICKS = 12;
    // El modelo de la mesa se dibuja 0,01 bloque mas arriba (GeckoLib lo sube para que no titile con
    // el piso): la tapa queda en 12,66 px. Las cartas van apenas por encima.
    private static final double FELT = TrucoLayout.TOP + 0.16D;

    // Cuando aparecio cada carta jugada (para que la de un jugador entre deslizandose).
    private final Map<TrucoTableEntity, Map<Integer, long[]>> seen = new WeakHashMap<>();

    public TrucoTableRenderer(EntityRendererProvider.Context context)
    {
        super(context, new Model());
        this.shadowRadius = 0.0F;
    }

    @Override
    protected void applyRotations(TrucoTableEntity table, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale)
    {
        super.applyRotations(table, poseStack, ageInTicks, table.getYRot(), partialTick, nativeScale);
    }

    @Override
    public void render(TrucoTableEntity table, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light)
    {
        super.render(table, entityYaw, partialTick, poseStack, buffers, light);
        if (table.leaving() || table.level() == null)
        {
            return;
        }
        CompoundTag view = table.view();
        int[] places = view.getIntArray("places");
        int n = view.getInt("n");
        if (n <= 0 || places.length < n)
        {
            return;
        }
        float now = table.level().getGameTime() + partialTick;
        TrucoLayout.Size size = table.size();
        float yaw = table.getYRot();
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TrucoScreen.CARDS));
        PoseStack.Pose last = poseStack.last();
        Matrix4f matrix = last.pose();
        Matrix3f normal = last.normal();
        Painter paint = new Painter(buffer, matrix, normal, light, size, yaw);

        long deal = view.getLong("deal");
        float dealt = deal < 0 ? 999.0F : now - deal;
        boolean dealing = dealt >= 0.0F && dealt < DEAL_END;
        int roccoSeat = view.getInt("rseat");

        // El mazo y la muestra, en el marco de Rocco (lugar 0).
        if (view.getBoolean("deck") && !dealing)
        {
            paint.deck(6.4D, -6.2D);
        }
        int muestra = view.getInt("muestra");
        if (muestra >= 0)
        {
            paint.card(0, 6.6D, -8.4D, FELT + 0.05D, muestra);
        }

        // Lo que se reparte: dorsos que van del mazo a cada asiento.
        if (dealing)
        {
            for (int tanda = 0; tanda < DEAL_TICKS.length; tanda++)
            {
                float t = (dealt - DEAL_TICKS[tanda]) / SLIDE_TICKS;
                if (t < 0.0F || t > 1.0F)
                {
                    continue;
                }
                for (int seat = 0; seat < n; seat++)
                {
                    if (seat % 2 != tanda % 2)
                    {
                        continue;
                    }
                    double[] from = TrucoLayout.seatToTable(size, 0, 6.4D, -6.2D);
                    double[] to = TrucoLayout.seatToTable(size, places[seat], 0.0D, -4.5D);
                    paint.flyingBack(from, to, ease(t), seat);
                }
            }
        }

        // Las cartas jugadas.
        int[] cards = view.getIntArray("cards");
        long sweep = view.getLong("sweep");
        float swept = sweep < 0 ? -1.0F : now - sweep;
        if (swept > SWEEP_TICKS)
        {
            return;
        }
        Map<Integer, long[]> appeared = this.seen.computeIfAbsent(table, key -> new HashMap<>());
        for (int trick = 0; trick < 3; trick++)
        {
            for (int seat = 0; seat < n; seat++)
            {
                int index = trick * n + seat;
                int card = index < cards.length ? cards[index] : -1;
                long[] mark = appeared.get(index);
                if (card < 0)
                {
                    appeared.remove(index);
                    continue;
                }
                if (mark == null || mark[0] != card)
                {
                    mark = new long[]{card, table.level().getGameTime()};
                    appeared.put(index, mark);
                }
                int place = places[seat];
                boolean rocco = seat == roccoSeat;
                double depth = rocco ? 9.6D : 8.8D;
                double sx = -2.2D + 2.2D * trick;
                double[] at = TrucoLayout.seatToTable(size, place, sx, -depth);
                // La de un jugador entra deslizandose desde su lugar (la de Rocco la apoya el).
                float slide = rocco ? 1.0F : Mth.clamp((now - mark[1]) / 4.0F, 0.0F, 1.0F);
                if (slide < 1.0F)
                {
                    double[] from = TrucoLayout.seatToTable(size, place, sx, -3.0D);
                    at = lerp(from, at, ease(slide));
                }
                if (swept >= 0.0F)
                {
                    double[] home = TrucoLayout.seatToTable(size, 0, 0.0D, -4.0D);
                    at = lerp(at, home, ease(Mth.clamp(swept / SWEEP_TICKS, 0.0F, 1.0F)));
                }
                paint.cardAt(place, at, FELT + 0.08D + trick * 0.02D, card);
            }
        }
    }

    private static double[] lerp(double[] a, double[] b, float t)
    {
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }

    private static float ease(float t)
    {
        return t * t * (3.0F - 2.0F * t);
    }

    // Dibuja cartas de 2 x 3 px acostadas sobre la mesa.
    private static final class Painter
    {
        private final VertexConsumer buffer;
        private final Matrix4f matrix;
        private final Matrix3f normal;
        private final int light;
        private final TrucoLayout.Size size;
        private final float yaw;

        Painter(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, int light, TrucoLayout.Size size, float yaw)
        {
            this.buffer = buffer;
            this.matrix = matrix;
            this.normal = normal;
            this.light = light;
            this.size = size;
            this.yaw = yaw;
        }

        // Una carta en el marco de un asiento.
        void card(int place, double sx, double sz, double y, int id)
        {
            cardAt(place, TrucoLayout.seatToTable(this.size, place, sx, sz), y, id);
        }

        // Una carta boca arriba (o el dorso si id < 0), con el lado largo hacia el centro de la mesa.
        void cardAt(int place, double[] at, double y, int id)
        {
            double[] f = TrucoLayout.facing(this.size, place);
            double[] r = {-f[1], f[0]};
            float u0 = id < 0 ? 0.0F : (id % 10) * CARD_U;
            float v0 = id < 0 ? 240.0F / 512.0F : (id / 10) * CARD_V;
            quad(at, f, r, 1.0D, 1.5D, y, u0, v0, u0 + CARD_U, v0 + CARD_V, 1.0F);
        }

        // Un dorso volando del mazo a un asiento (un poquito en el aire).
        void flyingBack(double[] from, double[] to, float t, int seat)
        {
            double[] at = {from[0] + (to[0] - from[0]) * t, from[1] + (to[1] - from[1]) * t};
            double dx = to[0] - from[0];
            double dz = to[1] - from[1];
            double length = Math.max(0.001D, Math.sqrt(dx * dx + dz * dz));
            double[] f = {dx / length, dz / length};
            double[] r = {-f[1], f[0]};
            double lift = Math.sin(t * Math.PI) * 1.5D;
            quad(at, f, r, 1.0D, 1.5D, FELT + 0.4D + lift + seat * 0.01D, 0.0F, 240.0F / 512.0F, CARD_U,
                    240.0F / 512.0F + CARD_V, 1.0F);
        }

        // El mazo: una pila de 0,75 px de alto con el dorso arriba.
        void deck(double sx, double sz)
        {
            double[] at = TrucoLayout.seatToTable(this.size, 0, sx, sz);
            double[] f = TrucoLayout.facing(this.size, 0);
            double[] r = {-f[1], f[0]};
            double bottom = FELT + 0.01D;
            double top = bottom + 0.75D;
            float v0 = 240.0F / 512.0F;
            quad(at, f, r, 1.0D, 1.5D, top, 0.0F, v0, CARD_U, v0 + CARD_V, 1.0F);
            // Los cantos: una franja del borde del dorso, un poco mas oscura.
            float edgeU = 1.0F / 512.0F;
            float edgeV = v0 + 1.0F / 512.0F;
            for (int side = 0; side < 4; side++)
            {
                double[] dir = switch (side)
                {
                    case 0 -> f;
                    case 1 -> new double[]{-f[0], -f[1]};
                    case 2 -> r;
                    default -> new double[]{-r[0], -r[1]};
                };
                double[] along = side < 2 ? r : f;
                double half = side < 2 ? 1.0D : 1.5D;
                double reach = side < 2 ? 1.5D : 1.0D;
                double[] c = {at[0] + dir[0] * reach, at[1] + dir[1] * reach};
                Vec3 a = point(c[0] - along[0] * half, bottom, c[1] - along[1] * half);
                Vec3 b = point(c[0] + along[0] * half, bottom, c[1] + along[1] * half);
                Vec3 d = point(c[0] - along[0] * half, top, c[1] - along[1] * half);
                Vec3 e = point(c[0] + along[0] * half, top, c[1] + along[1] * half);
                Vec3 out = TrucoLayout.directionToWorld(this.yaw, dir[0], dir[1]);
                vertex(a, edgeU, edgeV, out, 0.75F);
                vertex(b, edgeU * 3, edgeV, out, 0.75F);
                vertex(e, edgeU * 3, edgeV + edgeU, out, 0.75F);
                vertex(d, edgeU, edgeV + edgeU, out, 0.75F);
            }
        }

        // Un rectangulo acostado: mitad 'halfR' hacia la derecha del asiento y 'halfF' hacia adelante.
        private void quad(double[] at, double[] f, double[] r, double halfR, double halfF, double y, float u0, float v0,
                          float u1, float v1, float shade)
        {
            Vec3 frontLeft = point(at[0] + f[0] * halfF - r[0] * halfR, y, at[1] + f[1] * halfF - r[1] * halfR);
            Vec3 frontRight = point(at[0] + f[0] * halfF + r[0] * halfR, y, at[1] + f[1] * halfF + r[1] * halfR);
            Vec3 backRight = point(at[0] - f[0] * halfF + r[0] * halfR, y, at[1] - f[1] * halfF + r[1] * halfR);
            Vec3 backLeft = point(at[0] - f[0] * halfF - r[0] * halfR, y, at[1] - f[1] * halfF - r[1] * halfR);
            Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
            // Lo de arriba de la carta (v0) queda hacia el centro de la mesa.
            vertex(frontLeft, u0, v0, up, shade);
            vertex(backLeft, u0, v1, up, shade);
            vertex(backRight, u1, v1, up, shade);
            vertex(frontRight, u1, v0, up, shade);
        }

        private Vec3 point(double x, double y, double z)
        {
            return TrucoLayout.toWorld(Vec3.ZERO, this.yaw, x, y, z);
        }

        private void vertex(Vec3 at, float u, float v, Vec3 n, float shade)
        {
            int c = (int) (255 * shade);
            org.joml.Vector3f turned = this.normal.transform((float) n.x, (float) n.y, (float) n.z, new org.joml.Vector3f());
            this.buffer.addVertex(this.matrix, (float) at.x, (float) at.y, (float) at.z)
                    .setColor(c, c, c, 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(this.light)
                    .setNormal(turned.x(), turned.y(), turned.z());
        }
    }

    static final class Model extends GeoModel<TrucoTableEntity>
    {
        private static final ResourceLocation GEO = ResourceLocation.fromNamespaceAndPath(RoccoMod.MODID, "geo/entity/truco_mesa.geo.json");
        private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(RoccoMod.MODID, "textures/entity/truco_mesa.png");
        private static final ResourceLocation ANIMATION = ResourceLocation.fromNamespaceAndPath(RoccoMod.MODID, "animations/entity/truco_mesa.animation.json");

        @Override
        public ResourceLocation getModelResource(TrucoTableEntity table)
        {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(TrucoTableEntity table)
        {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(TrucoTableEntity table)
        {
            return ANIMATION;
        }

        @Override
        public void setCustomAnimations(TrucoTableEntity table, long instanceId, AnimationState<TrucoTableEntity> state)
        {
            super.setCustomAnimations(table, instanceId, state);
            TrucoLayout.Size size = table.size();
            for (TrucoLayout.Size each : TrucoLayout.Size.values())
            {
                boolean hidden = each != size;
                getBone(each.bone).ifPresent(bone ->
                {
                    bone.setHidden(hidden);
                    bone.setChildrenHidden(hidden);
                });
            }
        }
    }
}
