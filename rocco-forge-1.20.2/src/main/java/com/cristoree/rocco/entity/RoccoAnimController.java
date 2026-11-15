package com.cristoree.rocco.entity;

import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;

// GeckoLib 4.3 (la unica que hay para 1.20.2) no sabe cortar una animacion disparada antes de que
// termine. Este controlador lo hace igual que GeckoLib 4.7: suelta la animacion y deja que el
// controlador vuelva a la de siempre con su transicion.
public final class RoccoAnimController<T extends GeoAnimatable> extends AnimationController<T>
{
    public RoccoAnimController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler)
    {
        super(animatable, name, transitionTicks, handler);
    }

    // animation null corta cualquiera; si no, solo esa.
    boolean stopTriggered(@Nullable String animation)
    {
        if (this.triggeredAnimation == null)
        {
            return false;
        }
        if (animation != null && this.triggerableAnimations.get(animation) != this.triggeredAnimation)
        {
            return false;
        }
        if (this.currentRawAnimation == this.triggeredAnimation)
        {
            this.currentAnimation = null;
            this.currentRawAnimation = null;
        }
        this.triggeredAnimation = null;
        this.needsAnimationReload = true;
        return true;
    }

    // Del lado del cliente: busca el controlador de esa entidad y corta la animacion.
    public static void stop(GeoEntity animatable, @Nullable String controller, @Nullable String animation)
    {
        AnimatableManager<GeoAnimatable> manager = animatable.getAnimatableInstanceCache()
                .getManagerForId(((Entity) animatable).getId());
        if (manager == null)
        {
            return;
        }
        for (AnimationController<GeoAnimatable> candidate : manager.getAnimationControllers().values())
        {
            if ((controller == null || controller.equals(candidate.getName()))
                    && candidate instanceof RoccoAnimController<?> own && own.stopTriggered(animation))
            {
                return;
            }
        }
    }
}
