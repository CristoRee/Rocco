package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
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

    // El modelo trae las ocho caras encimadas y la planta puesta: las animaciones mueven las
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
        // solo tiene que existir mientras habla.
        hide("mouth_talk", !rocco.isTalking());
        hide("plant", !rocco.isPlantVisible());

        aimHeadAtWork(rocco);
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

    private void hide(String boneName, boolean hidden)
    {
        getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
    }
}
