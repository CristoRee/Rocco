package com.cristoree.rocco.truco;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Donde va cada cosa en la mesa del truco (ROCCO-V14 7.2 y 7.3). Todo en px desde el centro de la
// mesa, con la mesa orientada como Rocco: x a su derecha y -z hacia adelante (Rocco mira hacia -z).
// La usan el servidor (para armar la mesa) y el cliente (para dibujar las cartas).
public final class TrucoLayout
{
    public enum Size
    {
        CHICA("mesa_chica", 16, 16),
        MEDIANA("mesa_mediana", 32, 18),
        GRANDE("mesa_grande", 48, 18);

        public final String bone;
        public final double width;
        public final double depth;

        Size(String bone, double width, double depth)
        {
            this.bone = bone;
            this.width = width;
            this.depth = depth;
        }

        public static Size byId(int id)
        {
            Size[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, id))];
        }
    }

    // La tapa de la mesa esta a 12,5 px del piso.
    public static final double TOP = 12.5D;

    private static final double[][] CHICA = {{0, 12.5}, {14, 0}, {0, -14}, {-14, 0}};
    private static final double[][] MEDIANA = {{-8, 13.5}, {8, 15}, {22, 0}, {8, -15}, {-8, -15}, {-22, 0}};
    private static final double[][] GRANDE = {{-16, 13.5}, {0, 15}, {16, 15}, {30, 0}, {16, -15}, {0, -15}, {-16, -15}, {-30, 0}};

    // Cuantos juegan (sin contar a Rocco) -> mesa, lugares ocupados (el 0 siempre es de Rocco) y si Rocco
    // juega o arbitra. Con 2 jugadores depende de la opcion: de a tres (juega) o mano a mano (arbitra).
    public record Plan(Size size, int[] places, boolean roccoPlays)
    {
        // Jugadores de la partida (n de TrucoGame).
        public int gamePlayers()
        {
            return this.roccoPlays ? this.places.length : this.places.length - 1;
        }
    }

    public static Plan plan(int humans, boolean twoManoAMano)
    {
        return switch (humans)
        {
            case 1 -> new Plan(Size.CHICA, new int[]{0, 2}, true);
            case 2 -> new Plan(Size.CHICA, new int[]{0, 1, 3}, !twoManoAMano);
            case 3 -> new Plan(Size.CHICA, new int[]{0, 1, 2, 3}, true);
            // Rocco arbitra y los cuatro juegan 2 contra 2: el lugar 5 queda sin silla.
            case 4 -> new Plan(Size.MEDIANA, new int[]{0, 1, 2, 3, 4}, false);
            case 5 -> new Plan(Size.MEDIANA, new int[]{0, 1, 2, 3, 4, 5}, true);
            // Con 7 sillas se saca el lugar 3.
            case 6 -> new Plan(Size.GRANDE, new int[]{0, 1, 2, 4, 5, 6, 7}, false);
            default -> new Plan(Size.GRANDE, new int[]{0, 1, 2, 3, 4, 5, 6, 7}, true);
        };
    }

    public static double[] place(Size size, int place)
    {
        double[][] table = switch (size)
        {
            case CHICA -> CHICA;
            case MEDIANA -> MEDIANA;
            case GRANDE -> GRANDE;
        };
        return table[Math.max(0, Math.min(table.length - 1, place))];
    }

    // Hacia donde mira la silla de ese lugar (unitario, en el marco de la mesa): de frente al borde
    // que tiene adelante.
    public static double[] facing(Size size, int place)
    {
        double[] at = place(size, place);
        if (Math.abs(at[1]) > size.depth / 2.0D)
        {
            return new double[]{0.0D, -Math.signum(at[1])};
        }
        return new double[]{-Math.signum(at[0]), 0.0D};
    }

    // ---------- del marco de la mesa al mundo ----------

    public static Vec3 forward(float yaw)
    {
        return Vec3.directionFromRotation(0.0F, yaw);
    }

    // Un punto en px del marco de la mesa (x, alto, z) en el mundo, desde el centro de la mesa.
    public static Vec3 toWorld(Vec3 center, float yaw, double x, double y, double z)
    {
        Vec3 f = forward(yaw);
        Vec3 r = new Vec3(-f.z, 0.0D, f.x);
        return center.add(r.scale(x / 16.0D)).add(0.0D, y / 16.0D, 0.0D).add(f.scale(-z / 16.0D));
    }

    // La direccion (en el mundo) que en el marco de la mesa es (dx, dz).
    public static Vec3 directionToWorld(float yaw, double dx, double dz)
    {
        Vec3 f = forward(yaw);
        Vec3 r = new Vec3(-f.z, 0.0D, f.x);
        return r.scale(dx).add(f.scale(-dz));
    }

    public static float yawOf(Vec3 direction)
    {
        return (float) (Math.atan2(-direction.x, direction.z) * (180.0D / Math.PI));
    }

    public static Vec3 chairPosition(Vec3 center, float tableYaw, Size size, int place)
    {
        double[] at = place(size, place);
        return toWorld(center, tableYaw, at[0], 0.0D, at[1]);
    }

    public static float chairYaw(float tableYaw, Size size, int place)
    {
        double[] d = facing(size, place);
        return yawOf(directionToWorld(tableYaw, d[0], d[1]));
    }

    // Un punto en px del marco de un asiento (x a su derecha, y arriba, -z hacia la mesa; el origen
    // en el piso debajo del asiento), pasado al marco de la mesa (px).
    public static double[] seatToTable(Size size, int place, double sx, double sz)
    {
        double[] at = place(size, place);
        double[] f = facing(size, place);
        double rx = -f[1];
        double rz = f[0];
        return new double[]{at[0] + rx * sx + f[0] * -sz, at[1] + rz * sx + f[1] * -sz};
    }

    // ---------- buscar lugar ----------

    // Donde se para Rocco (su silla), hacia donde mira y el centro de la mesa.
    public record Spot(Vec3 rocco, float yaw, Vec3 center) {}

    // Un piso firme dentro de 'radius' bloques que entre la mesa y todas las sillas, lo mas cerca
    // posible y, si se puede, con Rocco mirando hacia 'facing'.
    @Nullable
    public static Spot find(Level level, Vec3 from, float facing, Size size, int[] places, int radius)
    {
        List<BlockPos> candidates = new ArrayList<>();
        BlockPos base = BlockPos.containing(from);
        for (int dy = -1; dy <= 1; dy++)
        {
            for (int dx = -radius; dx <= radius; dx++)
            {
                for (int dz = -radius; dz <= radius; dz++)
                {
                    if (dx * dx + dz * dz <= radius * radius)
                    {
                        candidates.add(base.offset(dx, dy, dz));
                    }
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(from.x, from.y + 0.5D, from.z)
                + Math.abs(pos.getY() - base.getY()) * 4.0D));

        // Las cuatro direcciones, primero la que mas se parece a la pedida.
        float snapped = Math.round(facing / 90.0F) * 90.0F;
        float[] yaws = {snapped, snapped + 90.0F, snapped - 90.0F, snapped + 180.0F};
        double[] roccoPlace = place(size, 0);
        for (BlockPos pos : candidates)
        {
            Vec3 rocco = new Vec3(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
            for (float yaw : yaws)
            {
                // Rocco queda en su lugar: el centro de la mesa sale de ahi.
                Vec3 f = forward(yaw);
                Vec3 r = new Vec3(-f.z, 0.0D, f.x);
                Vec3 center = rocco.subtract(r.scale(roccoPlace[0] / 16.0D)).add(f.scale(roccoPlace[1] / 16.0D));
                if (fits(level, center, yaw, size, places))
                {
                    return new Spot(rocco, yaw, center);
                }
            }
        }
        return null;
    }

    private static boolean fits(Level level, Vec3 center, float yaw, Size size, int[] places)
    {
        int floor = (int) Math.floor(center.y + 0.001D);
        // La mesa: el centro, las cuatro esquinas y los medios de los bordes.
        double hw = size.width / 2.0D - 1.0D;
        double hd = size.depth / 2.0D - 1.0D;
        for (double x : new double[]{-hw, 0.0D, hw})
        {
            for (double z : new double[]{-hd, 0.0D, hd})
            {
                if (!clear(level, toWorld(center, yaw, x, 0.0D, z), floor))
                {
                    return false;
                }
            }
        }
        for (int place : places)
        {
            if (!clear(level, chairPosition(center, yaw, size, place), floor))
            {
                return false;
            }
        }
        return true;
    }

    // Piso firme abajo y dos bloques libres arriba.
    private static boolean clear(Level level, Vec3 at, int floor)
    {
        BlockPos feet = BlockPos.containing(at.x, floor + 0.5D, at.z);
        BlockPos below = feet.below();
        if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
        {
            return false;
        }
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && level.getFluidState(feet).isEmpty();
    }

    private TrucoLayout() {}
}
