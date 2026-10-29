package com.cristoree.rocco.entity;

import com.cristoree.rocco.RoccoMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Que Rocco no moleste cuando construis:
//  - si vas a poner un bloque justo donde esta parado, se corre a un costado en ese mismo clic
//    (antes de que el juego mire si el lugar esta libre), asi el bloque entra;
//  - cada bloque que pones cerca le avisa que estas construyendo: por un rato no pasea por ahi y
//    te sigue a unos bloques de distancia, en vez de pegarse a tu lado.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoBuildingHelper
{
    private static final double NEAR = 8.0D;

    // HIGHEST: tiene que correrse antes de que se procese la colocacion.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event)
    {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (!(player.level() instanceof ServerLevel level) || !(stack.getItem() instanceof BlockItem) || player.isSpectator())
        {
            return;
        }

        BlockHitResult hit = event.getHitVec();
        BlockPos clicked = hit.getBlockPos();
        // Pasto alto, nieve fina y demas: el bloque nuevo los reemplaza en el mismo lugar.
        boolean replaces = level.getBlockState(clicked).canBeReplaced(new BlockPlaceContext(player, event.getHand(), stack, hit));
        BlockPos target = replaces ? clicked : clicked.relative(hit.getDirection());
        AABB spot = new AABB(target);

        for (RoccoEntity rocco : level.getEntitiesOfClass(RoccoEntity.class, spot.inflate(1.0D), RoccoEntity::isAlive))
        {
            if (rocco.getBoundingBox().intersects(spot))
            {
                rocco.stepAsideFrom(target, player);
            }
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event)
    {
        if (!(event.getEntity() instanceof Player player) || !(event.getLevel() instanceof ServerLevel level))
        {
            return;
        }
        for (RoccoEntity rocco : level.getEntitiesOfClass(RoccoEntity.class, player.getBoundingBox().inflate(NEAR), RoccoEntity::isAlive))
        {
            rocco.giveSpace(20 * 15);
        }
    }

    private RoccoBuildingHelper() {}
}
