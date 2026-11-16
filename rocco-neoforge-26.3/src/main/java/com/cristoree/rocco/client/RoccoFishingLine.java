package com.cristoree.rocco.client;

import com.cristoree.rocco.entity.RoccoEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

// La boya y el hilo de la caña de Rocco. El anzuelo del juego solo funciona con jugadores, asi
// que el servidor solo avisa donde cayo (y si pico) y aca se anima todo: el vuelo desde la punta
// de la caña, el flotar, el hundirse cuando muerde y la vuelta al recoger. El dibujo es el mismo
// de FishingHookRenderer: el sprite de la boya mirando a la camara y una curva de 16 tramos.
public final class RoccoFishingLine
{
    private static final Identifier TEXTURE = Identifier.parse("textures/entity/fishing_hook.png");
    private static final net.minecraft.client.renderer.rendertype.RenderType BOBBER =
            net.minecraft.client.renderer.rendertype.RenderTypes.entityCutout(TEXTURE);

    private static final int REEL_TICKS = 6;
    // Cuantos ticks se puede usar la ultima punta conocida si en este cuadro no se dibujo la caña
    // (pasa un tick entre trabajo y trabajo, cuando la mano queda vacia).
    private static final int TIP_MEMORY_TICKS = 4;

    private static final class State
    {
        @Nullable
        BlockPos target;
        int since;
        boolean reeling;
        int reelSince;
        Vec3 last = Vec3.ZERO;
        @Nullable
        Vec3 tip;
        int tipTick = Integer.MIN_VALUE;
    }

    private static final Map<RoccoEntity, State> STATES = new WeakHashMap<>();

    // Lo que se dibuja en este cuadro: la boya y el hilo, relativos a donde se dibuja Rocco.
    public record Line(Vec3 bobber, float dx, float dy, float dz, int light) {}

    // La ultima punta de la caña que se vio (la pone RoccoRenderer al dibujar la mano).
    private static final Map<RoccoEntity, Vec3> TIPS = new WeakHashMap<>();

    public static void rememberTip(RoccoEntity rocco, Vec3 tip)
    {
        TIPS.put(rocco, tip);
    }

    // Al juntar los datos de Rocco para dibujarlo (todavia con acceso a Rocco).
    @Nullable
    public static Line extract(RoccoEntity rocco, float partialTick)
    {
        State state = update(rocco);
        if (state == null)
        {
            return null;
        }
        Vec3 rodTip = rocco.getMainHandItem().getItem() instanceof net.minecraft.world.item.FishingRodItem ? TIPS.get(rocco) : null;
        Vec3 tip = tipFor(rocco, state, rodTip, partialTick);
        Vec3 bobber = bobberPosition(rocco, state, tip, partialTick);
        if (bobber == null)
        {
            return null;
        }
        state.last = bobber;

        Vec3 origin = new Vec3(Mth.lerp(partialTick, rocco.xOld, rocco.getX()),
                Mth.lerp(partialTick, rocco.yOld, rocco.getY()),
                Mth.lerp(partialTick, rocco.zOld, rocco.getZ()));
        BlockPos at = BlockPos.containing(bobber);
        int light = net.minecraft.util.LightCoordsUtil.pack(rocco.level().getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at),
                rocco.level().getBrightness(net.minecraft.world.level.LightLayer.SKY, at));
        // Igual que el juego: el hilo sale de la parte de arriba de la boya (+0,25).
        return new Line(bobber.subtract(origin), (float) (tip.x - bobber.x), (float) (tip.y - bobber.y - 0.25D),
                (float) (tip.z - bobber.z), light);
    }

    // Despues de dibujar a Rocco. La pose esta parada en la posicion de Rocco.
    public static void submit(Line line, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector collector,
                              net.minecraft.client.renderer.state.level.CameraRenderState camera)
    {
        poseStack.pushPose();
        poseStack.translate(line.bobber().x, line.bobber().y, line.bobber().z);

        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.rotate(camera.orientation);
        int light = line.light();
        collector.submitCustomGeometry(poseStack, BOBBER, (pose, consumer) ->
        {
            quadVertex(consumer, pose, light, 0.0F, 0, 0, 1);
            quadVertex(consumer, pose, light, 1.0F, 0, 1, 1);
            quadVertex(consumer, pose, light, 1.0F, 1, 1, 0);
            quadVertex(consumer, pose, light, 0.0F, 1, 0, 0);
        });
        poseStack.popPose();

        float width = net.minecraft.client.Minecraft.getInstance().gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;
        float dx = line.dx(), dy = line.dy(), dz = line.dz();
        collector.submitCustomGeometry(poseStack, net.minecraft.client.renderer.rendertype.RenderTypes.lines(), (pose, consumer) ->
        {
            for (int i = 0; i < 16; i++)
            {
                float from = i / 16.0F;
                float to = (i + 1) / 16.0F;
                stringVertex(dx, dy, dz, consumer, pose, from, to, width);
                stringVertex(dx, dy, dz, consumer, pose, to, from, width);
            }
        });
        poseStack.popPose();
    }

    // Para no cortar el hilo cuando Rocco queda fuera de cuadro pero la boya no.
    public static boolean isVisible(RoccoEntity rocco, Frustum frustum)
    {
        State state = STATES.get(rocco);
        if (state == null || (state.target == null && !state.reeling))
        {
            return false;
        }
        return frustum.isVisible(new AABB(state.last, state.last).inflate(1.0D));
    }

    // Se fija si el servidor tiro, cambio o recogio la boya desde el ultimo cuadro.
    @Nullable
    private static State update(RoccoEntity rocco)
    {
        BlockPos target = rocco.getBobber();
        State state = STATES.get(rocco);

        if (state == null)
        {
            if (target == null)
            {
                return null;
            }
            state = new State();
            STATES.put(rocco, state);
            state.target = null;
        }

        if (target != null && !target.equals(state.target))
        {
            state.target = target;
            state.since = rocco.tickCount;
            state.reeling = false;
        }
        else if (target == null && state.target != null)
        {
            state.target = null;
            state.reeling = true;
            state.reelSince = rocco.tickCount;
        }

        if (state.target == null && state.reeling && rocco.tickCount - state.reelSince > REEL_TICKS)
        {
            state.reeling = false;
        }
        if (state.target == null && !state.reeling)
        {
            STATES.remove(rocco);
            return null;
        }
        return state;
    }

    private static Vec3 tipFor(RoccoEntity rocco, State state, @Nullable Vec3 rodTip, float partialTick)
    {
        if (rodTip != null)
        {
            state.tip = rodTip;
            state.tipTick = rocco.tickCount;
            return rodTip;
        }
        if (state.tip != null && rocco.tickCount - state.tipTick <= TIP_MEMORY_TICKS)
        {
            return state.tip;
        }

        // Sin caña dibujada: mas o menos donde queda su mano derecha, un poco adelante.
        float yaw = Mth.lerp(partialTick, rocco.yBodyRotO, rocco.yBodyRot) * Mth.DEG_TO_RAD;
        double side = 0.3D;
        double ahead = 0.6D;
        return new Vec3(Mth.lerp(partialTick, rocco.xOld, rocco.getX()) - Mth.cos(yaw) * side - Mth.sin(yaw) * ahead,
                Mth.lerp(partialTick, rocco.yOld, rocco.getY()) + rocco.getBbHeight() * 0.9D,
                Mth.lerp(partialTick, rocco.zOld, rocco.getZ()) - Mth.sin(yaw) * side + Mth.cos(yaw) * ahead);
    }

    @Nullable
    private static Vec3 bobberPosition(RoccoEntity rocco, State state, Vec3 tip, float partialTick)
    {
        if (state.reeling)
        {
            // Vuelve a la caña con un saltito.
            float k = Mth.clamp((rocco.tickCount - state.reelSince + partialTick) / REEL_TICKS, 0.0F, 1.0F);
            return state.last.lerp(tip, k).add(0.0D, Mth.sin(k * Mth.PI) * 0.4D, 0.0D);
        }

        BlockPos target = state.target;
        if (target == null)
        {
            return null;
        }

        Vec3 rest = restingSpot(rocco.level(), target, rocco.getId());
        float age = rocco.tickCount - state.since + partialTick;

        if (age < RoccoEntity.BOBBER_FLIGHT_TICKS)
        {
            // En el aire: una parabola desde la punta, mas alta cuanto mas lejos tira.
            float k = age / RoccoEntity.BOBBER_FLIGHT_TICKS;
            double arc = 0.6D + tip.distanceTo(rest) * 0.15D;
            return tip.lerp(rest, k).add(0.0D, Mth.sin(k * Mth.PI) * arc, 0.0D);
        }

        double bob = rocco.isBobberBiting()
                ? -0.28D + Mth.sin(age * 0.9F) * 0.05D
                : Mth.sin(age * 0.2F) * 0.035D;
        return rest.add(0.0D, bob, 0.0D);
    }

    // Donde flota: sobre la superficie del agua, un poco corrida del centro del bloque para que
    // no caiga siempre en el mismo punto exacto.
    private static Vec3 restingSpot(Level level, BlockPos water, int seed)
    {
        double surface = water.getY() + level.getFluidState(water).getHeight(level, water);
        long hash = water.asLong() * 31L + seed;
        double ox = ((hash & 0xFF) / 255.0D - 0.5D) * 0.5D;
        double oz = (((hash >> 8) & 0xFF) / 255.0D - 0.5D) * 0.5D;
        return new Vec3(water.getX() + 0.5D + ox, surface - 0.05D, water.getZ() + 0.5D + oz);
    }

    private static void quadVertex(VertexConsumer consumer, PoseStack.Pose pose, int light, float x, int y, int u, int v)
    {
        consumer.addVertex(pose, x - 0.5F, y - 0.5F, 0.0F)
                .setColor(0xFFFFFFFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    // La curva del hilo: recta en el plano y cayendo como una cuerda floja en altura.
    private static void stringVertex(float x, float y, float z, VertexConsumer consumer, PoseStack.Pose pose,
                                     float from, float to, float width)
    {
        float px = x * from;
        float py = y * (from * from + from) * 0.5F + 0.25F;
        float pz = z * from;
        float nx = x * to - px;
        float ny = y * (to * to + to) * 0.5F + 0.25F - py;
        float nz = z * to - pz;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length > 1.0E-4F)
        {
            nx /= length;
            ny /= length;
            nz /= length;
        }
        consumer.addVertex(pose, px, py, pz).setColor(0xFF000000).setNormal(pose, nx, ny, nz).setLineWidth(width);
    }

    private RoccoFishingLine() {}
}
