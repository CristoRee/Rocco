package com.cristoree.rocco;

import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.Relationship;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoPose;
import com.cristoree.rocco.entity.RoccoState;
import com.cristoree.rocco.entity.RoccoTask;
import com.cristoree.rocco.voice.RoccoNetwork;
import com.cristoree.rocco.work.Errand;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;

// El menu de Rocco (agachado + clic derecho): lo que esta haciendo, que tan amigos son y botones
// para lo de todos los dias. Cada boton es una orden comun: se hace sin IA y Rocco contesta con
// su frase de siempre, igual que si se lo hubieras dicho.
public final class RoccoMenu
{
    public static final int FOLLOWING = 1;
    public static final int SITTING = 1 << 1;
    public static final int SLEEPING = 1 << 2;
    public static final int BUSY = 1 << 3;
    public static final int TENDING = 1 << 4;
    public static final int QUIET = 1 << 5;
    public static final int TRAVELLING = 1 << 6;
    // A quien puede atacar (las tres casillas) y si ya tiene el poder del cofre de ender.
    public static final int TARGET_HOSTILE = 1 << 7;
    public static final int TARGET_PEACEFUL = 1 << 8;
    public static final int TARGET_PLAYERS = 1 << 9;
    public static final int ENDER_POCKET = 1 << 10;

    private static final double REACH = 24.0D;

    public static void open(ServerPlayer player, RoccoEntity rocco)
    {
        if (!mine(rocco, player))
        {
            rocco.sayLine("menu_not_yours");
            return;
        }

        int flags = 0;
        RoccoPose pose = rocco.getRoccoPose();
        Errand errand = rocco.currentErrand();
        boolean tending = rocco.staysForWork();
        if (rocco.isFollowing()) flags |= FOLLOWING;
        if (pose == RoccoPose.SITTING || pose == RoccoPose.SITTING_DOWN) flags |= SITTING;
        if (pose.isAsleep()) flags |= SLEEPING;
        if (tending) flags |= TENDING;
        if (!tending && (errand != null || rocco.isWorking() || rocco.isSharingMate() || rocco.getActiveTask() != RoccoTask.NONE)) flags |= BUSY;
        if (rocco.brain().isQuiet()) flags |= QUIET;
        if (rocco.isTravelling()) flags |= TRAVELLING;
        int targets = rocco.attackTargets();
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.HOSTILE) != 0) flags |= TARGET_HOSTILE;
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.PEACEFUL) != 0) flags |= TARGET_PEACEFUL;
        if ((targets & com.cristoree.rocco.entity.RoccoCombat.PLAYERS) != 0) flags |= TARGET_PLAYERS;
        if (rocco.life().hasEnderPocket()) flags |= ENDER_POCKET;

        Relationship bond = rocco.relationship();
        long magicLeft = rocco.enchantCooldownLeft();
        String magic = magicLeft <= 0 ? "lista" : "en " + Math.max(1L, (magicLeft + 1199L) / 1200L) + " min";

        RoccoNetwork.openMenu(player, new RoccoNetwork.OpenMenu(rocco.getId(), status(rocco), flags, mode(), bondName(bond),
                Relationship.progress(rocco.trustPoints(), PersonalityProfile.current().trustPace()), magic,
                new ArrayList<>(rocco.places().keySet())));
    }

    public static void handle(ServerPlayer player, RoccoNetwork.MenuAction action)
    {
        Entity entity = player.level().getEntity(action.entityId());
        if (!(entity instanceof RoccoEntity rocco) || !rocco.isAlive() || rocco.getRoccoState() != RoccoState.ACTIVE
                || rocco.distanceToSqr(player) > REACH * REACH || !mine(rocco, player))
        {
            return;
        }

        String place = action.argument().trim();
        switch (action.action())
        {
            case "seguir" -> order(player, rocco, "Seguime.", "seguir", "", "follow");
            case "esperar" -> order(player, rocco, "Quedate aca.", "esperar", "", "wait");
            case "venir" -> order(player, rocco, "Veni aca.", "teletransportarse", "", "teleport_ack");
            case "sentarse" -> order(player, rocco, "Sentate.", "sentarse", "", "sit");
            case "levantarse" -> order(player, rocco, "Levantate.", "levantarse", "", "wake");
            case "dormir" -> order(player, rocco, "A dormir.", "dormir", "", "sleep");
            case "parar" -> order(player, rocco, "Para.", "parar", "", "stop");
            case "guardar" -> order(player, rocco, "Guarda todo.", "guardar", "todo", "store_ack");
            case "ordenar" -> order(player, rocco, "Ordena los cofres.", "ordenar", "", "sort_ack");
            case "huerta" -> order(player, rocco, "Cuida la huerta.", "cuidar_huerta", "", "tend_ack");
            case "casa" -> order(player, rocco, "Quedate cuidando la casa.", "cuidar_casa", "", "home_ack");
            case "mate" -> order(player, rocco, "Tomate un mate.", "mate", "", "mate_ack");
            case "cebar_mate" -> order(player, rocco, "Cebame un mate.", "cebar_mate", "", "mate_share_ack");
            case "bailar" -> order(player, rocco, "Baila.", "bailar", "", "dance_ack");
            case "ir_a" -> order(player, rocco, "Llevame a " + place + ".", "ir_a", place, "guide_ack");
            case "recordar_lugar" -> order(player, rocco, "Recorda este lugar como " + place + ".", "recordar_lugar", place, "");
            case "olvidar_lugar" -> order(player, rocco, "Olvidate de " + place + ".", "olvidar_lugar", place, "");
            case "callar" -> rocco.brain().menuQuiet(true);
            case "charlar" -> rocco.brain().menuQuiet(false);
            case "inventario" ->
            {
                if (!rocco.openInventory(player))
                {
                    rocco.sayLine("menu_cant_open");
                }
            }
            // Su armadura, su arma y su escudo.
            case "equipo" -> com.cristoree.rocco.menu.RoccoEquipmentMenu.open(player, rocco);
            // Las tres casillas de a quien puede atacar (llegan juntas, como un numero).
            case "objetivos" ->
            {
                try
                {
                    rocco.setAttackTargets(Integer.parseInt(place));
                }
                catch (NumberFormatException e)
                {
                    RoccoMod.LOGGER.debug("[Rocco] Objetivos invalidos desde el menu: {}", place);
                }
            }
            default -> RoccoMod.LOGGER.debug("[Rocco] Opcion de menu desconocida: {}", action.action());
        }
    }

    private static void order(ServerPlayer player, RoccoEntity rocco, String phrase, String verb, String argument, String ack)
    {
        if ((verb.equals("ir_a") || verb.endsWith("_lugar")) && argument.isEmpty())
        {
            return;
        }
        rocco.brain().menuOrder(player, phrase, verb, argument, ack);
    }

    // En multijugador, el menu es del dueño (si no, cualquiera lo podria dejar esperando en el medio de la nada).
    private static boolean mine(RoccoEntity rocco, ServerPlayer player)
    {
        return rocco.getOwnerId() == null || rocco.getOwnerId().equals(player.getUUID());
    }

    private static String status(RoccoEntity rocco)
    {
        RoccoPose pose = rocco.getRoccoPose();
        if (pose.isAsleep())
        {
            return "Durmiendo";
        }
        if (rocco.isTravelling())
        {
            return switch (rocco.getMotion().kind())
            {
                case AIR -> "Volando con vos";
                case BOAT -> "Remando en su barquito";
                case UNDERWATER -> "Buceando con vos";
                default -> "Flotando con su patito";
            };
        }
        if (rocco.isSharingMate())
        {
            return "Tomando mate con vos";
        }
        switch (rocco.getActiveTask())
        {
            case MATE -> { return "Tomando mate"; }
            case DANCING -> { return "Bailando"; }
            case ENCHANTING -> { return "Encantando"; }
            default -> { }
        }
        Errand errand = rocco.currentErrand();
        if (errand != null)
        {
            String what = errand.describe();
            return what.isEmpty() ? "Trabajando" : Character.toUpperCase(what.charAt(0)) + what.substring(1);
        }
        if (pose == RoccoPose.SITTING || pose == RoccoPose.SITTING_DOWN)
        {
            return "Sentado";
        }
        return rocco.isFollowing() ? "Te sigue" : "Esperando acá";
    }

    private static String mode()
    {
        return switch (PersonalityProfile.mode())
        {
            case CHILL -> "Chill";
            case SIN_FILTRO -> "Sin filtro";
            case PERSONALIZADO -> "Personalizado";
        };
    }

    private static String bondName(Relationship bond)
    {
        return switch (bond)
        {
            case STRANGER -> "Recién se conocen";
            case ACQUAINTANCE -> "Conocidos";
            case FRIEND -> "Amigos";
            case BEST_FRIEND -> "Mejores amigos";
        };
    }

    private RoccoMenu() {}
}
