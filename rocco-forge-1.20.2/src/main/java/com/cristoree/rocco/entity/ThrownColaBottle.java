package com.cristoree.rocco.entity;

import com.cristoree.rocco.item.BrokenBottleItem;
import com.cristoree.rocco.registry.ModEntities;
import com.cristoree.rocco.registry.ModItems;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

// La botella vacia de Roco Cola tirada: a lo que le pega le hace medio corazón y se rompe.
public final class ThrownColaBottle extends ThrowableItemProjectile
{
    public ThrownColaBottle(EntityType<? extends ThrownColaBottle> type, Level level)
    {
        super(type, level);
    }

    public ThrownColaBottle(Level level, LivingEntity thrower)
    {
        super(ModEntities.BOTELLA_LANZADA.get(), thrower, level);
    }

    @Override
    protected Item getDefaultItem()
    {
        return ModItems.ROCO_COLA_EMPTY.get();
    }

    @Override
    protected void onHitEntity(EntityHitResult result)
    {
        super.onHitEntity(result);
        result.getEntity().hurt(damageSources().thrown(this, getOwner()), 1.0F);
    }

    @Override
    protected void onHit(HitResult result)
    {
        super.onHit(result);
        if (!level().isClientSide)
        {
            BrokenBottleItem.shatter(level(), getX(), getY(), getZ(), 1.1F + this.random.nextFloat() * 0.2F);
            if (level() instanceof ServerLevel server)
            {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, getItem()), getX(), getY(), getZ(), 6, 0.05D, 0.05D, 0.05D, 0.08D);
            }
            discard();
        }
    }
}
