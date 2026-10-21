package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class RoccoModel extends GeoModel<RoccoEntity>
{
    private static final ResourceLocation MODEL =
            new ResourceLocation(RoccoMod.MODID, "geo/entity/rocco.geo.json");
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(RoccoMod.MODID, "textures/entity/rocco.png");
    private static final ResourceLocation ANIMATION =
            new ResourceLocation(RoccoMod.MODID, "animations/entity/rocco.animation.json");

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

    // El modelo trae las ocho caras encimadas y la planta puesta: las animaciones mueven las
    // partes, pero es aca donde se decide cuales existen en cada fotograma.
    @Override
    public void setCustomAnimations(RoccoEntity rocco, long instanceId, AnimationState<RoccoEntity> animationState)
    {
        super.setCustomAnimations(rocco, instanceId, animationState);

        String current = rocco.getExpression().id();
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
    }

    private void hide(String boneName, boolean hidden)
    {
        getBone(boneName).ifPresent(bone -> bone.setHidden(hidden));
    }
}
