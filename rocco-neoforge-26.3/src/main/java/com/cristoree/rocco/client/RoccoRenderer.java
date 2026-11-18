package com.cristoree.rocco.client;

import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoGear;
import com.cristoree.rocco.entity.RoccoScene;
import com.cristoree.rocco.entity.RoccoTask;
import com.geckolib.animation.state.BoneSnapshot;
import com.geckolib.cache.model.GeoBone;
import com.geckolib.constant.DataTickets;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.GeoRenderLayer;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import com.geckolib.renderer.layer.builtin.BlockAndItemGeoLayer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

// Como se dibuja Rocco. Con GeckoLib 5 (y el juego desde 1.21.2) el dibujo va en dos tiempos:
// primero se juntan los datos de Rocco en un State (addRenderData, con Rocco a mano) y despues se
// dibuja solo con esos datos. Aca se decide que huesos se ven (caras, mochila, mate, escenas), se
// agregan las pasadas de armadura y armas y se dibujan el hilo de la caña y los corazones.
public class RoccoRenderer extends GeoEntityRenderer<RoccoEntity, RoccoRenderer.State>
{
    public static class State extends LivingEntityRenderState implements GeoRenderState
    {
        RoccoEntity rocco;
        boolean hiddenDead;
        int expression;
        boolean speaking;
        boolean consuming;
        boolean plant;
        int backpack;
        boolean garden;
        boolean grave;
        boolean crown;
        boolean mate;
        boolean magic;
        boolean cola;
        boolean mic;
        boolean asado;
        boolean tortas;
        boolean truco;
        int sceneBits;
        float headYaw;
        boolean tilt;
        float tiltPitch;
        float center;
        float horseLean;
        float modelScale = 1.0F;
        List<RoccoGearLayer.Pass> gear = List.of();
        @Nullable
        RoccoGearLayer.Pass pass;
        @Nullable
        RoccoFishingLine.Line line;
        @Nullable
        Component hearts;
        boolean rodInHand;
        float bodyYaw;
    }

    private static final float MAX_HEAD_YAW = 35.0F;
    private static final float HORSE_LEAN = 10.0F;
    private static final float HORSE_LEAN_FOR_RIDER = 50.0F;

    // Los demas objetos (herramientas, comida, lo que te da): el mismo giro del jugador, del tamaño de su patita.
    private static final float[] ITEM_POSE = {-90.0F, 180.0F, 0.0F, -1.0F, 2.5F, 0.0F, 0.6F, 0.0F, 0.5F, 0.0F, 0.0F};
    // La botella en su pata: corrimiento (px, desde el hueso de la mano), giro en X y en Y, y escala.
    private static final float[] BOTTLE_GRIP = {0.0F, 0.3F, -2.66F, -90.0F, 0.0F, 0.4F};
    // Un item de 16 px queda en unos 5,4 px: del tamaño de su patita.
    private static final float HELD_ITEM_SCALE = 0.34F;

    // Los nombres de los huesos de las caras, armados una sola vez.
    private static final String[] EYES = names("eyes_");
    private static final String[] MOUTH = names("mouth_");
    private static final String[] TALK = names("talk_");
    private static final String[] FACE = names("face_");
    private static final String[] BACKPACKS = {"backpack_1", "backpack_2", "backpack_3", "backpack_4", "backpack_5", "backpack_6"};
    private static final String[] MEATS = {"", "beef", "pork", "lamb"};
    private static final String[] ASADO_BONES = {"grill", "meat_cook", "tongs", "apron_grill"};
    private static final String[] TORTAS_BONES = {"fritura", "apron_cook", "dough_ball", "torta", "skimmer"};
    private static final String[] TRUCO_BONES = {"cards_fan", "card_throw", "deal_card", "deck"};
    private static final String[] MATE_BONES = {"mate", "termo", "mate_water"};
    private static final String[] MAGIC_BONES = {"magic_right", "magic_left"};

    // Mientras se dibuja una pieza de armadura o un arma: sin capas (los items de las manos ya estan).
    private boolean inGearPass;

    public RoccoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new RoccoModel());
        this.shadowRadius = 0.4F;
        // item_right e item_left son huesos vacios en las palmas: ahi se dibuja lo que sostiene.
        withRenderLayer(new HeldItems(context));
        // Las esferitas de magia brillan en la oscuridad: rocco_glowmask.png marca solo esas partes.
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public State createRenderState(RoccoEntity rocco, Void relatedObject)
    {
        return new State();
    }

    // ---------- juntar los datos de este cuadro ----------

    @Override
    public void addRenderData(RoccoEntity rocco, Void relatedObject, State state, float partialTick)
    {
        super.addRenderData(rocco, relatedObject, state, partialTick);
        state.rocco = rocco;
        // Muerto esperando para reaparecer (se lo llevo la tierra): no se dibuja nada.
        state.hiddenDead = rocco.isInvisible() && rocco.getRoccoState() == com.cristoree.rocco.entity.RoccoState.DYING;
        String playing = RoccoModel.playingAnimation(rocco);

        // Si el modelo todavia no trae la cara pedida, se usa happy (si no, quedaria sin cara).
        state.expression = rocco.shownExpression().ordinal();
        // Comiendo o tomando la animacion usa mouth_talk para masticar; cantando, y con el audio de su voz sonando, tambien habla.
        state.speaking = rocco.isTalking() || RoccoEntity.SPEAKING_HOOK.test(rocco) || rocco.getActiveTask().singing();
        state.consuming = rocco.getActiveTask().consuming();
        state.plant = rocco.isPlantVisible();
        // La mochila (o la apariencia elegida): no se ve plantado, dormido, en la tumba, en el barquito,
        // en el caballo (chocaria con tu pecho) ni sentado en la silla del truco (el respaldo la atravesaria).
        boolean carries = !rocco.isPlantVisible() && !rocco.getRoccoState().isInert() && !rocco.getRoccoPose().isAsleep()
                && rocco.getMotion().kind() != com.cristoree.rocco.entity.RoccoMotion.Kind.BOAT && !rocco.getMotion().onHorse()
                && !(rocco.getVehicle() instanceof com.cristoree.rocco.truco.TrucoChairEntity);
        state.backpack = carries ? rocco.shownBackpack() : 0;
        state.garden = rocco.showsGarden();
        state.grave = rocco.getRoccoState().isGrave();
        // Con el casco de oro (una coronita) se esconden las hojitas de la cabeza, para que se vea.
        state.crown = RoccoClientConfig.SHOW_ARMOR.get() && !rocco.isPlantVisible()
                && RoccoGear.wearsCrown(rocco.getItemBySlot(EquipmentSlot.HEAD));

        // Las escenas: cada una con sus cosas, y solo mientras se ve su animacion.
        state.asado = playing.startsWith("animation.rocco.asado");
        state.tortas = playing.startsWith("animation.rocco.tortas");
        state.truco = playing.startsWith("animation.rocco.truco");
        state.sceneBits = rocco.sceneBones();
        // El mate y las esferas se muestran si la animacion es la suya o si el servidor dice que esta en esa tarea.
        state.mate = rocco.getActiveTask() == RoccoTask.MATE || rocco.isSharingMate() || playing.startsWith("animation.rocco.mate")
                || state.tortas || state.truco;
        state.magic = rocco.getActiveTask() == RoccoTask.ENCHANTING || playing.startsWith("animation.rocco.enchant");
        state.cola = playing.equals("animation.rocco.cola_drink") || playing.equals("animation.rocco.bottle_smash");
        state.mic = playing.equals("animation.rocco.sing_groove") || playing.equals("animation.rocco.sing_rap");

        // Tomando mate, bailando o festejando la cabeza la mueve solo la animacion.
        state.headYaw = !isPerformance(rocco.getActiveTask()) && !playing.startsWith("animation.rocco.mate")
                ? workHeadYaw(rocco) : 0.0F;

        // Volando se inclina con el jugador; en el caballo parado de manos se echa para atras.
        state.tilt = rocco.getMotion().tiltsWithPlayer();
        state.tiltPitch = Mth.lerp(partialTick, rocco.xRotO, rocco.getXRot());
        state.center = rocco.getBbHeight() * 0.5F;
        float stand = rocco.horseStand(partialTick);
        state.horseLean = stand > 0.0F ? stand * (riderLooksOverHim(rocco) ? HORSE_LEAN_FOR_RIDER : HORSE_LEAN) : 0.0F;
        state.modelScale = RoccoClientConfig.MODEL_SCALE.get().floatValue();
        state.bodyYaw = Mth.lerp(partialTick, rocco.yBodyRotO, rocco.yBodyRot);

        state.gear = RoccoGearLayer.passes(rocco);
        state.rodInHand = rocco.getMainHandItem().getItem() instanceof FishingRodItem;
        state.line = RoccoFishingLine.extract(rocco, partialTick);
        state.hearts = hearts(rocco);
    }

    private static boolean isPerformance(RoccoTask task)
    {
        return task == RoccoTask.MATE || task == RoccoTask.DANCING || task == RoccoTask.CLAPPING
                || task == RoccoTask.CELEBRATING || task.musical() || task.scripted();
    }

    // Mira su trabajo: el giro se SUMA al que trae la animacion (en plant y harvest la mirada hacia
    // abajo viene del fotograma, asi que aca solo se corrige el costado). En radianes.
    private static float workHeadYaw(RoccoEntity rocco)
    {
        BlockPos target = rocco.getWorkTarget();
        if (target == null || !rocco.isWorking())
        {
            return 0.0F;
        }
        double dx = target.getX() + 0.5D - rocco.getX();
        double dz = target.getZ() + 0.5D - rocco.getZ();
        // Comiendo de su propio inventario el objetivo es el: sin una direccion clara, manda la animacion.
        if (dx * dx + dz * dz < 0.25D)
        {
            return 0.0F;
        }
        float wanted = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float delta = Mth.wrapDegrees(wanted - rocco.getYRot());
        return Mth.clamp(delta, -MAX_HEAD_YAW, MAX_HEAD_YAW) * Mth.DEG_TO_RAD;
    }

    private static boolean riderLooksOverHim(RoccoEntity rocco)
    {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null && minecraft.getCameraEntity() == minecraft.player
                && minecraft.options.getCameraType().isFirstPerson() && minecraft.player.getVehicle() == rocco.getVehicle();
    }

    // ---------- que huesos se ven ----------

    @Override
    public void adjustModelBonesForRender(RenderPassInfo<State> info, BoneSnapshots snapshots)
    {
        super.adjustModelBonesForRender(info, snapshots);
        State state = info.renderState();

        // El modelo trae las veinte caras encimadas: solo se ve la que corresponde.
        int current = state.expression;
        if (snapshots.get(EYES[current]).isEmpty())
        {
            current = RoccoExpression.HAPPY.ordinal();
        }
        for (int i = 0; i < EYES.length; i++)
        {
            boolean hidden = i != current;
            hide(snapshots, EYES[i], hidden);
            hide(snapshots, MOUTH[i], hidden);
            hide(snapshots, TALK[i], hidden);
            hide(snapshots, FACE[i], hidden);
        }
        hide(snapshots, "mouth_talk", !state.speaking && !state.consuming);
        hide(snapshots, "plant", !state.plant);
        for (int tier = 1; tier <= BACKPACKS.length; tier++)
        {
            hide(snapshots, BACKPACKS[tier - 1], state.backpack != tier);
        }
        hide(snapshots, "garden", !state.garden);
        hide(snapshots, "lid_garden", !state.garden);
        // La armadura y las armas se dibujan en su propia pasada, con su textura.
        for (String bone : RoccoGearLayer.ALL_GEAR_BONES)
        {
            hide(snapshots, bone, true);
        }
        hide(snapshots, "grave", !state.grave);
        for (String bone : MATE_BONES)
        {
            hide(snapshots, bone, !state.mate);
        }
        hide(snapshots, "cola", !state.cola);
        hide(snapshots, "cola_cap", !state.cola);
        hide(snapshots, "mic", !state.mic);
        sceneBones(snapshots, state);
        hide(snapshots, "head_leaves", state.crown);
        hide(snapshots, "hair_a", state.crown);
        // Solo los cubos de las esferas: adentro estan los localizadores de las manos.
        for (String bone : MAGIC_BONES)
        {
            snapshots.ifPresent(bone, sphere -> sphere.skipRender(!state.magic));
        }
        if (state.headYaw != 0.0F)
        {
            snapshots.ifPresent("head", head -> head.setRotY(head.getRotY() - state.headYaw));
        }
    }

    // La parrilla, la olla y las cartas se tapan fuera de su escena; adentro, lo que depende de como
    // va (que carne, cuanta masa, cuantas tortas, cuantas cartas) sale de los datos de la escena.
    private static void sceneBones(BoneSnapshots snapshots, State state)
    {
        for (String bone : ASADO_BONES)
        {
            hide(snapshots, bone, !state.asado);
        }
        for (String bone : TORTAS_BONES)
        {
            hide(snapshots, bone, !state.tortas);
        }
        for (String bone : TRUCO_BONES)
        {
            hide(snapshots, bone, !state.truco);
        }
        int bits = state.sceneBits;
        if (state.asado)
        {
            int cook = RoccoScene.cookMeat(bits);
            int next = RoccoScene.nextMeat(bits);
            for (int meat = 1; meat <= 3; meat++)
            {
                hide(snapshots, "meat_cook_" + MEATS[meat], cook != meat);
                hide(snapshots, "raw_next_" + MEATS[meat], next != meat);
                for (int slot = 0; slot < 4; slot++)
                {
                    hide(snapshots, "warm_" + (slot + 1) + "_" + MEATS[meat], RoccoScene.warmMeat(bits, slot) != meat);
                }
            }
        }
        if (state.tortas)
        {
            int dough = RoccoScene.dough(bits);
            for (int size = 1; size <= 3; size++)
            {
                hide(snapshots, "dough_" + size, dough != size);
            }
            int bowl = RoccoScene.bowl(bits);
            for (int level = 1; level <= 4; level++)
            {
                hide(snapshots, "bowl_fill_" + level, level > bowl);
            }
        }
        if (state.truco)
        {
            int fan = RoccoScene.fan(bits);
            for (int card = 1; card <= 3; card++)
            {
                hide(snapshots, "card_fan_" + card, card > fan);
            }
        }
    }

    // Tapa los cubos del hueso y tambien a sus hijos (mouth_talk no tiene cubos propios, solo hijos).
    private static void hide(BoneSnapshots snapshots, String name, boolean hidden)
    {
        snapshots.ifPresent(name, bone -> bone.skipRender(hidden).skipChildrenRender(hidden));
    }

    private static String[] names(String prefix)
    {
        RoccoExpression[] all = RoccoExpression.all();
        String[] out = new String[all.length];
        for (RoccoExpression expression : all)
        {
            out[expression.ordinal()] = prefix + expression.id();
        }
        return out;
    }

    // ---------- la pose ----------

    // Al morir hace su animacion de caida: sin el giro de costado del juego.
    @Override
    protected float getDeathMaxRotation(GeoRenderState state)
    {
        return 0.0F;
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<State> info, float widthScale, float heightScale)
    {
        float scale = info.renderState().modelScale;
        super.scaleModelForRender(info, widthScale * scale, heightScale * scale);
    }

    // Volando, las animaciones ya lo acuestan; ademas se inclina con el jugador (gira alrededor del
    // centro del modelo para que no se despegue del lugar). En el caballo parado de manos se echa
    // para atras: poco, abrazado al cuello; para el que lo monta en primera persona se acuesta.
    @Override
    protected void applyRotations(RenderPassInfo<State> info, PoseStack poseStack, float nativeScale)
    {
        super.applyRotations(info, poseStack, nativeScale);
        State state = info.renderState();
        if (state.tilt)
        {
            poseStack.translate(0.0F, state.center, 0.0F);
            poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(-state.tiltPitch));
            poseStack.translate(0.0F, -state.center, 0.0F);
        }
        if (state.horseLean > 0.0F)
        {
            poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(state.horseLean));
        }
    }

    // ---------- dibujar ----------

    @Override
    public void preRenderPass(RenderPassInfo<State> info, SubmitNodeCollector collector)
    {
        super.preRenderPass(info, collector);
        if (this.inGearPass)
        {
            return;
        }
        State state = info.renderState();
        RoccoEntity rocco = state.rocco;
        // Donde quedan los localizadores (cabeza, mate, manos): de ahi salen las particulas.
        for (String name : RoccoModel.LOCATORS)
        {
            info.addBonePositionListener(name, (world, model, local) ->
            {
                if (world != null)
                {
                    RoccoParticleFx.rememberLocator(rocco, name, world);
                }
            });
        }
        // La punta de la caña: un poco adelante y arriba de su mano, como la sostiene.
        if (state.rodInHand)
        {
            float scale = state.modelScale;
            float yaw = state.bodyYaw * Mth.DEG_TO_RAD;
            info.addBonePositionListener("item_right", (world, model, local) ->
            {
                if (world != null)
                {
                    RoccoFishingLine.rememberTip(rocco, world.add(-Mth.sin(yaw) * 0.55D * scale, 0.5D * scale, Mth.cos(yaw) * 0.55D * scale));
                }
            });
        }
    }

    @Override
    public List<GeoRenderLayer<RoccoEntity, Void, State>> getRenderLayers()
    {
        return this.inGearPass ? List.of() : super.getRenderLayers();
    }

    @Override
    public Identifier getTextureLocation(State state)
    {
        return state.pass != null ? state.pass.texture() : super.getTextureLocation(state);
    }

    @Override
    public RenderType getRenderType(State state, Identifier texture)
    {
        return state.pass != null ? state.pass.type() : super.getRenderType(state, texture);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        if (state.hiddenDead)
        {
            return;
        }
        super.submit(state, poseStack, collector, camera);

        // La armadura y las armas: el mismo modelo con la misma animacion, solo con los huesos de cada pieza.
        if (!state.gear.isEmpty())
        {
            Integer color = state.getGeckolibData(DataTickets.RENDER_COLOR);
            this.inGearPass = true;
            try
            {
                for (RoccoGearLayer.Pass pass : state.gear)
                {
                    state.pass = pass;
                    state.addGeckolibData(DataTickets.RENDER_COLOR, pass.color());
                    RenderPassInfo.BoneUpdater<State> only = (info, snapshots) -> showOnly(info, snapshots, pass.bones());
                    performRenderPass(state, poseStack, collector, camera, List.of(only));
                }
            }
            finally
            {
                this.inGearPass = false;
                state.pass = null;
                if (color != null)
                {
                    state.addGeckolibData(DataTickets.RENDER_COLOR, color);
                }
            }
        }

        if (state.line != null)
        {
            RoccoFishingLine.submit(state.line, poseStack, collector, camera);
        }
        if (state.hearts != null)
        {
            submitHealth(state, poseStack, collector, camera);
        }
    }

    // Lo de adentro de esas piezas se ve; sus padres no dibujan sus propios cubos pero dejan pasar a
    // los hijos; todo lo demas se tapa. Se recorre el arbol a mano: los huesos de GeckoLib 5 no se
    // pueden guardar en un HashSet (su hashCode mira al padre y a los hijos y no termina nunca).
    private static void showOnly(RenderPassInfo<State> info, BoneSnapshots snapshots, List<String> targets)
    {
        for (GeoBone top : info.model().topLevelBones())
        {
            showOnly(top, false, snapshots, targets);
        }
    }

    // Devuelve si en este hueso o debajo hay alguna pieza de la pasada.
    private static boolean showOnly(GeoBone bone, boolean inside, BoneSnapshots snapshots, List<String> targets)
    {
        boolean self = inside || targets.contains(bone.name());
        boolean any = self;
        for (GeoBone child : bone.children())
        {
            any |= showOnly(child, self, snapshots, targets);
        }
        BoneSnapshot snapshot = snapshots.get(bone);
        if (snapshot != null)
        {
            if (self)
            {
                snapshot.skipRender(false).skipChildrenRender(false);
            }
            else
            {
                snapshot.skipRender(true).skipChildrenRender(!any);
            }
        }
        return any;
    }

    // ---------- los corazones ----------

    // Sus corazones (verdes, de hoja) sobre la cabeza mientras no tenga la vida llena. Van arriba del nombre, si se ve.
    @Nullable
    private Component hearts(RoccoEntity rocco)
    {
        float health = rocco.getHealth();
        float max = rocco.getMaxHealth();
        if (!RoccoClientConfig.SHOW_HEALTH.get() || rocco.getRoccoState() != com.cristoree.rocco.entity.RoccoState.ACTIVE
                || health >= max || health <= 0.0F || this.entityRenderDispatcher.distanceToSqr(rocco) > 24.0D * 24.0D)
        {
            return null;
        }
        // Uno por cada 2 de vida (hasta 10); medio corazon en verde claro. Con mas de 20 de vida maxima, en numeros.
        if (max > 20.0F)
        {
            return Component.literal("♥ " + (int) Math.ceil(health) + "/" + (int) max).withStyle(net.minecraft.ChatFormatting.GREEN);
        }
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int total = (int) Math.ceil(max / 2.0F);
        int points = (int) Math.ceil(health);
        for (int i = 0; i < total; i++)
        {
            int filled = points - i * 2;
            int color = filled >= 2 ? 0x3FD13F : filled == 1 ? 0xB5E655 : 0x4A4A4A;
            out.append(Component.literal("♥").withStyle(style -> style.withColor(color)));
        }
        return out;
    }

    private void submitHealth(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera)
    {
        // Como un cartel de nombre del juego (fondo y corazones en dos pasadas, mirando a la camara);
        // con el nombre visible, una linea mas arriba.
        net.minecraft.world.phys.Vec3 at = state.nameTagAttachment != null ? state.nameTagAttachment
                : new net.minecraft.world.phys.Vec3(0.0D, state.boundingBoxHeight + 0.5D, 0.0D);
        collector.submitNameTag(poseStack, at, state.nameTag != null ? -11 : 0, state.hearts, !state.isDiscrete,
                state.lightCoords, camera);
    }

    // ---------- que se dibuja y que no ----------

    @Override
    public boolean shouldRender(RoccoEntity entity, Frustum frustum, double camX, double camY, double camZ, float partialTick)
    {
        return super.shouldRender(entity, frustum, camX, camY, camZ, partialTick) || RoccoFishingLine.isVisible(entity, frustum);
    }

    // Con la parrilla o la mesa delante, la caja tiene que incluirlas: si no, desaparecen cuando
    // Rocco queda justo afuera de la pantalla.
    @Override
    protected AABB getBoundingBoxForCulling(RoccoEntity entity, float partialTick)
    {
        return entity.cullingBox();
    }

    @Override
    public boolean shouldShowName(RoccoEntity animatable, double distanceSq)
    {
        if (!RoccoClientConfig.SHOW_NAMEPLATE.get() || Minecraft.getInstance().gui.hud.isHidden() || animatable.isPlantVisible())
        {
            return false;
        }
        double max = RoccoClientConfig.NAMEPLATE_DISTANCE.get();
        return distanceSq <= max * max;
    }

    // ---------- lo que tiene en las manos ----------

    // Comiendo, tomando una pocion o dando un regalo: lo dice el servidor o la animacion que se esta viendo.
    static boolean isConsuming(RoccoEntity rocco)
    {
        if (rocco.getActiveTask().usesHeld())
        {
            return true;
        }
        String playing = RoccoModel.playingAnimation(rocco);
        return playing.startsWith("animation.rocco.food_eat") || playing.startsWith("animation.rocco.potion_drink")
                || playing.startsWith("animation.rocco.give_");
    }

    // En la mano: lo que tenga, salvo un arma con modelo propio (va en su pasada) y salvo un arma
    // con las manos ocupadas (tomando mate, volando, nadando...).
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

    private final class HeldItems extends BlockAndItemGeoLayer<RoccoEntity, Void, State>
    {
        HeldItems(EntityRendererProvider.Context context)
        {
            super(context, RoccoRenderer.this);
        }

        @Override
        public void addRenderData(RoccoEntity rocco, Void relatedObject, State state, float partialTick)
        {
            state.addGeckolibData(CONTENTS, getRelevantBones(rocco, relatedObject, state, partialTick));
        }

        @Override
        protected List<RenderData> getRelevantBones(RoccoEntity rocco, Void relatedObject, State state, float partialTick)
        {
            List<RenderData> out = new ArrayList<>();
            boolean consuming = isConsuming(rocco);
            ItemStack main = rocco.getMainHandItem();
            ItemStack right = consuming ? ItemStack.EMPTY : heldFor(rocco, main, RoccoGear.weaponLook(main));
            boolean bottle = right.is(com.cristoree.rocco.registry.ModItems.BOTELLA_ROTA.get());
            add(out, rocco, "item_right", right, bottle ? ItemDisplayContext.NONE : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND);
            ItemStack off = rocco.getOffhandItem();
            add(out, rocco, "item_left", heldFor(rocco, off, RoccoGear.shieldLook(off)), ItemDisplayContext.THIRD_PERSON_LEFT_HAND);
            // El cofre de ender que se traga o que absorbe, flotando entre las manitos.
            add(out, rocco, "grab", consuming ? ItemStack.EMPTY : rocco.grabbedItem(), ItemDisplayContext.FIXED);
            // La comida y la pocion en el hueso held; cantando flor en el truco saca una flor de la cabeza.
            ItemStack held = consuming ? rocco.grabbedItem()
                    : RoccoModel.playingAnimation(rocco).equals("animation.rocco.truco_call_flor")
                    ? new ItemStack(net.minecraft.world.item.Items.POPPY) : ItemStack.EMPTY;
            add(out, rocco, "held", held, ItemDisplayContext.NONE);
            return out;
        }

        private void add(List<RenderData> out, RoccoEntity rocco, String bone, ItemStack stack, ItemDisplayContext context)
        {
            if (stack.isEmpty())
            {
                return;
            }
            ItemStackRenderState item = new ItemStackRenderState();
            this.itemModelResolver.updateForLiving(item, stack, context, rocco);
            out.add(RenderData.item(bone, context, item));
        }

        @Override
        protected void submitItemStackRender(PoseStack poseStack, GeoBone bone, ItemStackRenderState item, ItemDisplayContext context,
                                             State state, SubmitNodeCollector collector, int packedLight)
        {
            poseStack.pushPose();
            switch (bone.name())
            {
                // El cofre que flota: a un tercio de bloque (FIXED lo dibuja a medio bloque).
                case "grab" -> poseStack.scale(0.66F, 0.66F, 0.66F);
                // Del tamaño de su patita: la animacion la ubica, la gira y la achica mordida a mordida.
                case "held" -> poseStack.scale(HELD_ITEM_SCALE, HELD_ITEM_SCALE, HELD_ITEM_SCALE);
                case "item_right" ->
                {
                    if (context == ItemDisplayContext.NONE)
                    {
                        // La botella rota: la boca entre sus dos pezuñas y el pico roto apuntando hacia adelante.
                        float[] g = BOTTLE_GRIP;
                        poseStack.translate(g[0] / 16.0F, g[1] / 16.0F, g[2] / 16.0F);
                        poseStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(g[4]));
                        poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(g[3]));
                        poseStack.scale(g[5], g[5], g[5]);
                    }
                    else if (!state.rodInHand)
                    {
                        // Agarrado como lo agarra el jugador (la caña no: el hilo esta medido sobre como queda).
                        float[] p = ITEM_POSE;
                        poseStack.translate(p[3] / 16.0F, p[4] / 16.0F, p[5] / 16.0F);
                        poseStack.rotate(com.mojang.math.Axis.ZP.rotationDegrees(p[10]));
                        poseStack.rotate(com.mojang.math.Axis.XP.rotationDegrees(p[0]));
                        poseStack.rotate(com.mojang.math.Axis.YP.rotationDegrees(p[1]));
                        poseStack.rotate(com.mojang.math.Axis.ZP.rotationDegrees(p[2]));
                        poseStack.translate(p[7] / 16.0F, p[8] / 16.0F, p[9] / 16.0F);
                        poseStack.scale(p[6], p[6], p[6]);
                    }
                }
                default -> { }
            }
            super.submitItemStackRender(poseStack, bone, item, context, state, collector, packedLight);
            poseStack.popPose();
        }
    }
}
