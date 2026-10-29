package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import com.cristoree.rocco.entity.RoccoTask;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
    private static final ResourceLocation[] ANIMATION_FALLBACKS = {
            new ResourceLocation(RoccoMod.MODID, "animations/entity/rocco.animation.json")
    };

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

    @Override
    public ResourceLocation[] getAnimationResourceFallbacks(RoccoEntity animatable)
    {
        return ANIMATION_FALLBACKS;
    }

    // El modelo trae las veinte caras encimadas y la planta puesta: las animaciones mueven las
    // partes, pero es aca donde se decide cuales existen en cada fotograma.
    @Override
    public void setCustomAnimations(RoccoEntity rocco, long instanceId, AnimationState<RoccoEntity> animationState)
    {
        super.setCustomAnimations(rocco, instanceId, animationState);

        // Si el modelo todavia no trae la cara pedida (pasa al exportar de Blockbench a mitad de
        // camino), se usa happy: sin esto se esconderian todas y Rocco quedaria sin cara.
        String current = rocco.getExpression().id();
        if (getBone("eyes_" + current).isEmpty())
        {
            current = RoccoExpression.HAPPY.id();
        }

        for (RoccoExpression expression : RoccoExpression.all())
        {
            boolean hidden = !expression.id().equals(current);
            hide("eyes_" + expression.id(), hidden);
            hide("mouth_" + expression.id(), hidden);
            hide("talk_" + expression.id(), hidden);
        }

        // La animacion de habla intercambia smile y mouth_talk por escala, asi que mouth_talk
        // solo tiene que existir mientras habla. Este hueso no tiene cubos propios: lo unico
        // que lo esconde de verdad es tapar a sus hijos.
        hide("mouth_talk", !rocco.isTalking());
        hide("plant", !rocco.isPlantVisible());

        // El mate, el termo y las esferas de magia viven escondidos adentro del cuerpo; igual se
        // tapan de verdad cuando no se usan, porque algunas animaciones abren la boca o la tapa.
        // Se muestran si la animacion que se esta viendo es la suya, ademas de si el servidor dice
        // que esta en esa tarea: antes dependia solo de lo segundo, y si ese dato llegaba tarde
        // (o no llegaba) Rocco hacia todo el mate con las manos vacias.
        String playing = playingAnimation(rocco, instanceId);
        boolean mate = rocco.getActiveTask() == RoccoTask.MATE || playing.equals("animation.rocco.mate");
        boolean magic = rocco.getActiveTask() == RoccoTask.ENCHANTING || playing.startsWith("animation.rocco.enchant");
        for (String bone : MATE_BONES)
        {
            hide(bone, !mate);
        }
        // Solo los cubos de las esferas: adentro estan los localizadores de las manos, que tambien
        // usan los aplausos y el baile para sus particulas.
        for (String bone : MAGIC_BONES)
        {
            getBone(bone).ifPresent(sphere -> sphere.setHidden(!magic));
        }

        // Los localizadores de particulas tienen que saber donde quedan en el mundo.
        for (String locator : LOCATORS)
        {
            getBone(locator).ifPresent(bone -> bone.setTrackingMatrices(true));
        }

        aimHeadAtWork(rocco);
    }

    private static final String[] MATE_BONES = {"mate", "termo", "mate_water"};
    private static final String[] MAGIC_BONES = {"magic_right", "magic_left"};
    // Las hojas-ala, el mechon, el patito, las antiparras, el barquito y el remo no se tocan desde
    // aca: las animaciones los sacan y los guardan (en las demas quedan en escala 0).
    public static final String[] LOCATORS = {"fx_head", "fx_mate", "fx_spout", "fx_magic_right", "fx_magic_left",
            "fx_wing_right", "fx_wing_left", "fx_tear_right", "fx_tear_left", "fx_snorkel", "fx_ring", "fx_boat",
            "fx_paddle_right", "fx_paddle_left"};

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

        getBone("head").ifPresent(head -> head.setRotY(head.getRotY() - yaw));
    }

    // GeckoLib tiene dos banderas distintas: setHidden tapa SOLO los cubos del propio hueso y
    // setChildrenHidden tapa a sus hijos. Usando una sola, un hueso como mouth_talk (que no
    // tiene cubos propios, solo 12 hijos) seguia dibujandose entero.
    private void hide(String boneName, boolean hidden)
    {
        getBone(boneName).ifPresent(bone ->
        {
            bone.setHidden(hidden);
            bone.setChildrenHidden(hidden);
        });
    }
}
