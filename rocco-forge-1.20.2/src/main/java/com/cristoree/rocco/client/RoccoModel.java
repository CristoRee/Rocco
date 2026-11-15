package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.config.RoccoClientConfig;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoGear;
import com.cristoree.rocco.entity.RoccoScene;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class RoccoModel extends GeoModel<RoccoEntity>
{
    private static final float MAX_HEAD_YAW = 35.0F;

    private static final ResourceLocation MODEL =
            new ResourceLocation(RoccoMod.MODID, "geo/entity/rocco.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RoccoMod.MODID, "textures/entity/rocco.png");
    // Blockbench exporta el archivo como "model.animation.json". Se acepta ese nombre y tambien
    // el renombrado, asi re-exportar no rompe nada.
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(RoccoMod.MODID, "animations/entity/model.animation.json");
    @Override
    public ResourceLocation getModelResource(RoccoEntity animatable)
    {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(RoccoEntity animatable)
    {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(RoccoEntity animatable)
    {
        return ANIMATION;
    }

    // El modelo trae las veinte caras encimadas y la planta puesta: las animaciones mueven las
    // partes, pero es aca donde se decide cuales existen en cada fotograma.
    @Override
    public void setCustomAnimations(RoccoEntity rocco, long instanceId, AnimationState<RoccoEntity> animationState)
    {
        super.setCustomAnimations(rocco, instanceId, animationState);
        // Buscar un hueso por su nombre recorre todo el modelo: con un indice, cada cuadro de cada
        // Rocco cuesta unas pocas consultas en vez de miles de visitas.
        index(getBakedModel(getModelResource(rocco)));

        // Si el modelo todavia no trae la cara pedida (pasa al exportar de Blockbench a mitad de
        // camino), se usa happy: sin esto se esconderian todas y Rocco quedaria sin cara.
        RoccoExpression current = rocco.shownExpression();
        if (bone(EYES[current.ordinal()]).isEmpty())
        {
            current = RoccoExpression.HAPPY;
        }

        for (RoccoExpression expression : RoccoExpression.all())
        {
            boolean hidden = expression != current;
            int i = expression.ordinal();
            hide(EYES[i], hidden);
            hide(MOUTH[i], hidden);
            hide(TALK[i], hidden);
            // El contorno fijo del mewing (si no se tapa, aparece en todas las caras).
            hide(FACE[i], hidden);
        }

        // La animacion de habla intercambia smile y mouth_talk por escala, asi que mouth_talk
        // solo tiene que existir mientras habla. Este hueso no tiene cubos propios: lo unico
        // que lo esconde de verdad es tapar a sus hijos.
        // Comiendo o tomando la animacion usa mouth_talk para masticar y para la cara de asco.
        // Cantando, y con el audio de su voz sonando, tambien habla.
        boolean speaking = rocco.isTalking() || RoccoEntity.SPEAKING_HOOK.test(rocco) || rocco.getActiveTask().singing();
        hide("mouth_talk", !speaking && !rocco.getActiveTask().consuming());
        hide("plant", !rocco.isPlantVisible());
        // La mochila que lleva puesta (o la apariencia elegida), una sola. No se ve plantado, dormido, en la tumba, en el
        // barquito ni en el caballo (ahi chocaria con tu pecho).
        // Sentado en la silla del truco tampoco: el respaldo la atravesaria.
        boolean carries = !rocco.isPlantVisible() && !rocco.getRoccoState().isInert() && !rocco.getRoccoPose().isAsleep()
                && rocco.getMotion().kind() != com.cristoree.rocco.entity.RoccoMotion.Kind.BOAT && !rocco.getMotion().onHorse()
                && !(rocco.getVehicle() instanceof com.cristoree.rocco.truco.TrucoChairEntity);
        for (int tier = 1; tier <= com.cristoree.rocco.item.BackpackItem.MAX_TIER; tier++)
        {
            hide(BACKPACKS[tier - 1], !carries || rocco.shownBackpack() != tier);
        }
        // Las animaciones lo dejan en escala 0, pero si alguna no lo toca asomaria por arriba.
        hide("garden", !rocco.showsGarden());
        hide("lid_garden", !rocco.showsGarden());

        // La armadura y las armas las dibuja RoccoGearLayer en su propia pasada, con su textura.
        for (String bone : RoccoGearLayer.ALL_GEAR_BONES)
        {
            hide(bone, true);
        }
        // La tumba solo enterrado o saliendo de ella (las otras animaciones la dejan en escala 0).
        hide("grave", !rocco.getRoccoState().isGrave());

        // El mate, el termo y las esferas de magia viven escondidos adentro del cuerpo; igual se
        // tapan de verdad cuando no se usan, porque algunas animaciones abren la boca o la tapa.
        // Se muestran si la animacion que se esta viendo es la suya, ademas de si el servidor dice
        // que esta en esa tarea: antes dependia solo de lo segundo, y si ese dato llegaba tarde
        // (o no llegaba) Rocco hacia todo el mate con las manos vacias.
        String playing = playingAnimation(rocco, instanceId);
        // Las escenas: cada una con sus cosas, y solo mientras se ve su animacion.
        boolean asado = playing.startsWith("animation.rocco.asado");
        boolean tortas = playing.startsWith("animation.rocco.tortas");
        boolean truco = playing.startsWith("animation.rocco.truco");
        // En las tortas fritas toma mate, y en el truco el mate y el termo estan en la mesa.
        boolean mate = rocco.getActiveTask() == RoccoTask.MATE || rocco.isSharingMate() || playing.startsWith("animation.rocco.mate")
                || tortas || truco;
        boolean magic = rocco.getActiveTask() == RoccoTask.ENCHANTING || playing.startsWith("animation.rocco.enchant");
        for (String bone : MATE_BONES)
        {
            hide(bone, !mate);
        }
        // La botella, la tapita y el microfono, igual: solo en su animacion.
        boolean cola = playing.equals("animation.rocco.cola_drink") || playing.equals("animation.rocco.bottle_smash");
        hide("cola", !cola);
        hide("cola_cap", !cola);
        hide("mic", !playing.equals("animation.rocco.sing_groove") && !playing.equals("animation.rocco.sing_rap"));
        sceneBones(rocco, asado, tortas, truco);

        // Con el casco de oro (una coronita) se esconden las hojitas de la cabeza, para que se vea.
        boolean crown = RoccoClientConfig.SHOW_ARMOR.get() && !rocco.isPlantVisible()
                && RoccoGear.wearsCrown(rocco.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
        hide("head_leaves", crown);
        hide("hair_a", crown);
        // Solo los cubos de las esferas: adentro estan los localizadores de las manos, que tambien
        // usan los aplausos y el baile para sus particulas.
        for (String bone : MAGIC_BONES)
        {
            bone(bone).ifPresent(sphere -> sphere.setHidden(!magic));
        }

        // Los localizadores de particulas tienen que saber donde quedan en el mundo.
        for (String locator : LOCATORS)
        {
            bone(locator).ifPresent(bone -> bone.setTrackingMatrices(true));
        }

        // Tomando mate, bailando o festejando la cabeza la mueve solo la animacion: si se le
        // sumaba el giro hacia "su bloque", la boca quedaba lejos de la bombilla.
        if (!isPerformance(rocco.getActiveTask()) && !playing.startsWith("animation.rocco.mate"))
        {
            aimHeadAtWork(rocco);
        }
    }

    // La parrilla, la olla y las cartas viven a los costados del modelo: se tapan fuera de su escena.
    // Adentro, lo que depende de como va (que carne, cuanta masa, cuantas tortas, cuantas cartas) sale
    // de los datos de la escena.
    private void sceneBones(RoccoEntity rocco, boolean asado, boolean tortas, boolean truco)
    {
        for (String bone : ASADO_BONES)
        {
            hide(bone, !asado);
        }
        for (String bone : TORTAS_BONES)
        {
            hide(bone, !tortas);
        }
        for (String bone : TRUCO_BONES)
        {
            hide(bone, !truco);
        }
        int bits = rocco.sceneBones();
        if (asado)
        {
            int cook = RoccoScene.cookMeat(bits);
            int next = RoccoScene.nextMeat(bits);
            for (int meat = 1; meat <= 3; meat++)
            {
                hide("meat_cook_" + MEATS[meat], cook != meat);
                hide("raw_next_" + MEATS[meat], next != meat);
                for (int slot = 0; slot < 4; slot++)
                {
                    hide("warm_" + (slot + 1) + "_" + MEATS[meat], RoccoScene.warmMeat(bits, slot) != meat);
                }
            }
        }
        if (tortas)
        {
            int dough = RoccoScene.dough(bits);
            for (int size = 1; size <= 3; size++)
            {
                hide("dough_" + size, dough != size);
            }
            int bowl = RoccoScene.bowl(bits);
            for (int level = 1; level <= 4; level++)
            {
                hide("bowl_fill_" + level, level > bowl);
            }
        }
        if (truco)
        {
            int fan = RoccoScene.fan(bits);
            for (int card = 1; card <= 3; card++)
            {
                hide("card_fan_" + card, card > fan);
            }
        }
    }

    private static final String[] MEATS = {"", "beef", "pork", "lamb"};
    private static final String[] ASADO_BONES = {"grill", "meat_cook", "tongs", "apron_grill"};
    private static final String[] TORTAS_BONES = {"fritura", "apron_cook", "dough_ball", "torta", "skimmer"};
    private static final String[] TRUCO_BONES = {"cards_fan", "card_throw", "deal_card", "deck"};

    private static boolean isPerformance(RoccoTask task)
    {
        return task == RoccoTask.MATE || task == RoccoTask.DANCING || task == RoccoTask.CLAPPING
                || task == RoccoTask.CELEBRATING || task.musical() || task.scripted();
    }

    // Los nombres de los huesos de las caras, armados una sola vez.
    private static final String[] EYES = names("eyes_");
    private static final String[] MOUTH = names("mouth_");
    private static final String[] TALK = names("talk_");
    private static final String[] FACE = names("face_");
    private static final String[] BACKPACKS = {"backpack_1", "backpack_2", "backpack_3", "backpack_4", "backpack_5", "backpack_6"};

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

    private BakedGeoModel indexed;
    private final java.util.Map<String, GeoBone> bones = new java.util.HashMap<>();

    private void index(BakedGeoModel model)
    {
        if (model == this.indexed)
        {
            return;
        }
        this.indexed = model;
        this.bones.clear();
        for (GeoBone top : model.topLevelBones())
        {
            collect(top);
        }
    }

    private void collect(GeoBone bone)
    {
        this.bones.put(bone.getName(), bone);
        for (GeoBone child : bone.getChildBones())
        {
            collect(child);
        }
    }

    private java.util.Optional<GeoBone> bone(String name)
    {
        return java.util.Optional.ofNullable(this.bones.get(name));
    }

    private static final String[] MATE_BONES = {"mate", "termo", "mate_water"};
    private static final String[] MAGIC_BONES = {"magic_right", "magic_left"};
    // Las hojas-ala, el mechon, el patito, las antiparras, el barquito y el remo no se tocan desde
    // aca: las animaciones los sacan y los guardan (en las demas quedan en escala 0).
    public static final String[] LOCATORS = {"fx_head", "fx_mate", "fx_spout", "fx_magic_right", "fx_magic_left",
            "fx_wing_right", "fx_wing_left", "fx_tear_right", "fx_tear_left", "fx_snorkel", "fx_ring", "fx_boat",
            "fx_paddle_right", "fx_paddle_left", "fx_garden",
            "fx_cola", "fx_mic", "fx_belly", "fx_body", "fx_grave", "fx_soil", "item_right", "fx_held", "fx_backpack",
            "fx_coals", "fx_grill_smoke", "fx_meat", "fx_oil", "fx_fire", "fx_table"};

    public static String playingAnimation(RoccoEntity rocco)
    {
        return playingAnimation(rocco, rocco.getId());
    }

    // La animacion que el controlador principal esta mostrando en este momento ("" si ninguna).
    private static String playingAnimation(RoccoEntity rocco, long instanceId)
    {
        var manager = rocco.getAnimatableInstanceCache().getManagerForId(instanceId);
        var controller = manager == null ? null : manager.getAnimationControllers().get("main");
        var current = controller == null ? null : controller.getCurrentAnimation();
        return current == null ? "" : current.animation().name();
    }

    // El giro se SUMA al que ya trae la animacion, no lo reemplaza: en plant y harvest la mirada
    // hacia abajo viene del fotograma, asi que aca solo se corrige el costado.
    private void aimHeadAtWork(RoccoEntity rocco)
    {
        BlockPos target = rocco.getWorkTarget();
        if (target == null || !rocco.isWorking())
        {
            return;
        }

        double dx = target.getX() + 0.5D - rocco.getX();
        double dz = target.getZ() + 0.5D - rocco.getZ();

        // Comiendo de su propio inventario el objetivo es el, y atan2(0,0) le torceria la
        // cabeza sin motivo. Si no hay una direccion clara, la animacion manda sola.
        if (dx * dx + dz * dz < 0.25D)
        {
            return;
        }

        float wanted = (float) (Math.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        float delta = Mth.wrapDegrees(wanted - rocco.getYRot());
        float yaw = Mth.clamp(delta, -MAX_HEAD_YAW, MAX_HEAD_YAW) * Mth.DEG_TO_RAD;

        bone("head").ifPresent(head -> head.setRotY(head.getRotY() - yaw));
    }

    // GeckoLib tiene dos banderas distintas: setHidden tapa SOLO los cubos del propio hueso y
    // setChildrenHidden tapa a sus hijos. Usando una sola, un hueso como mouth_talk (que no
    // tiene cubos propios, solo 12 hijos) seguia dibujandose entero.
    private void hide(String boneName, boolean hidden)
    {
        bone(boneName).ifPresent(bone ->
        {
            bone.setHidden(hidden);
            bone.setChildrenHidden(hidden);
        });
    }
}
