package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.item.RoccoMateItem;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.conditional.FishingRodCast;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

// Lo que cambia como se ve un item segun quien lo tiene. Desde 1.21.4 los items eligen su modelo
// con propiedades declaradas en assets/<mod>/items/*.json.
public final class RoccoItemProperties
{
    public static final Identifier CAST = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "cast");
    public static final Identifier SIPPED = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "sipped");

    // La caña "tirada" (sin el hilo enrollado): la del jugador que pesca, como siempre, y ademas
    // la de Rocco con la boya afuera (assets/minecraft/items/fishing_rod.json usa esta propiedad).
    public record Cast() implements ConditionalItemModelProperty
    {
        public static final MapCodec<Cast> MAP_CODEC = MapCodec.unit(new Cast());
        private static final FishingRodCast VANILLA = new FishingRodCast();

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed, ItemDisplayContext context)
        {
            if (entity instanceof RoccoEntity rocco)
            {
                return rocco.getBobber() != null && rocco.getMainHandItem() == stack;
            }
            return VANILLA.get(stack, level, entity, seed, context);
        }

        @Override
        public MapCodec<Cast> type()
        {
            return MAP_CODEC;
        }
    }

    // El agua del mate baja con cada sorbo (el modelo trae lleno, 2/3, 1/3 y vacio).
    public record Sipped() implements RangeSelectItemModelProperty
    {
        public static final MapCodec<Sipped> MAP_CODEC = MapCodec.unit(new Sipped());

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed)
        {
            return RoccoMateItem.sips(stack) / (float) RoccoMateItem.SIPS;
        }

        @Override
        public MapCodec<Sipped> type()
        {
            return MAP_CODEC;
        }
    }

    private RoccoItemProperties() {}
}
