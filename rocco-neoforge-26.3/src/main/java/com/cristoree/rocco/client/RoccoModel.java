package com.cristoree.rocco.client;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.entity.RoccoEntity;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

// Los archivos del modelo de Rocco. Que huesos se ven en cada cuadro lo decide RoccoRenderer
// (con GeckoLib 5 eso va en la pasada de dibujo, con los datos que se juntaron de Rocco).
public class RoccoModel extends GeoModel<RoccoEntity>
{
    // GeckoLib 5 los busca en assets/rocco/geckolib/models y geckolib/animations.
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "entity/rocco");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "textures/entity/rocco.png");
    // Blockbench exporta el archivo como "model.animation.json".
    private static final Identifier ANIMATION = Identifier.fromNamespaceAndPath(RoccoMod.MODID, "entity/model");

    @Override
    public Identifier getModelResource(GeoRenderState state)
    {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState state)
    {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(RoccoEntity animatable)
    {
        return ANIMATION;
    }

    // Las hojas-ala, el mechon, el patito, las antiparras, el barquito y el remo no se tocan desde
    // aca: las animaciones los sacan y los guardan (en las demas quedan en escala 0).
    public static final String[] LOCATORS = {"fx_head", "fx_mate", "fx_spout", "fx_magic_right", "fx_magic_left",
            "fx_wing_right", "fx_wing_left", "fx_tear_right", "fx_tear_left", "fx_snorkel", "fx_ring", "fx_boat",
            "fx_paddle_right", "fx_paddle_left", "fx_garden",
            "fx_cola", "fx_mic", "fx_belly", "fx_body", "fx_grave", "fx_soil", "item_right", "fx_held", "fx_backpack",
            "fx_coals", "fx_grill_smoke", "fx_meat", "fx_oil", "fx_fire", "fx_table"};

    // La animacion que el controlador principal esta mostrando en este momento ("" si ninguna).
    public static String playingAnimation(RoccoEntity rocco)
    {
        return rocco.mainAnimationName();
    }
}
