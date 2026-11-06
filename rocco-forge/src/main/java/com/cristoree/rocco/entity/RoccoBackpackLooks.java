package com.cristoree.rocco.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Las apariencias de mochila desbloqueadas en este mundo, por dueño. Van con el guardado del mundo,
// no con Rocco: si se muere para siempre y nace otro, lo ganado sigue ahi. Cada bit es una mochila
// (el bit 0 es la 1).
final class RoccoBackpackLooks extends SavedData
{
    private static final String FILE = "rocco_backpack_looks";

    private final Map<UUID, Integer> unlocked = new HashMap<>();

    // Suma lo que trae y devuelve todo lo que ese dueño tiene desbloqueado.
    static int merge(MinecraftServer server, UUID owner, int looks)
    {
        RoccoBackpackLooks data = server.overworld().getDataStorage()
                .computeIfAbsent(RoccoBackpackLooks::load, RoccoBackpackLooks::new, FILE);
        int known = data.unlocked.getOrDefault(owner, 0);
        int merged = known | looks;
        if (merged != known)
        {
            data.unlocked.put(owner, merged);
            data.setDirty();
        }
        return merged;
    }

    private static RoccoBackpackLooks load(CompoundTag tag)
    {
        RoccoBackpackLooks data = new RoccoBackpackLooks();
        CompoundTag owners = tag.getCompound("Owners");
        for (String key : owners.getAllKeys())
        {
            try
            {
                data.unlocked.put(UUID.fromString(key), owners.getInt(key));
            }
            catch (IllegalArgumentException ignored)
            {
                // Una clave rota no tiene que impedir cargar el resto.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        CompoundTag owners = new CompoundTag();
        this.unlocked.forEach((owner, looks) -> owners.putInt(owner.toString(), looks));
        tag.put("Owners", owners);
        return tag;
    }
}
