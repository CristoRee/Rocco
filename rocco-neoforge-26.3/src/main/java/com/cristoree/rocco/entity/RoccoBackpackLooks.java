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

    // Desde 26.1 los datos del mundo se guardan con un codec. El id "minecraft:rocco_backpack_looks" usa el mismo
    // archivo de antes (data/rocco_backpack_looks.dat), asi un mundo de 1.21.1 conserva lo que tenia.
    static final net.minecraft.world.level.saveddata.SavedDataType<RoccoBackpackLooks> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            net.minecraft.resources.Identifier.withDefaultNamespace(FILE), RoccoBackpackLooks::new,
            net.minecraft.nbt.CompoundTag.CODEC.xmap(RoccoBackpackLooks::load, data -> data.save(new net.minecraft.nbt.CompoundTag())), net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Map<UUID, Integer> unlocked = new HashMap<>();

    // Suma lo que trae y devuelve todo lo que ese dueño tiene desbloqueado.
    static int merge(MinecraftServer server, UUID owner, int looks)
    {
        RoccoBackpackLooks data = server.overworld().getDataStorage()
                .computeIfAbsent(TYPE);
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
        CompoundTag owners = tag.getCompoundOrEmpty("Owners");
        for (String key : owners.keySet())
        {
            try
            {
                data.unlocked.put(UUID.fromString(key), owners.getIntOr(key, 0));
            }
            catch (IllegalArgumentException ignored)
            {
                // Una clave rota no tiene que impedir cargar el resto.
            }
        }
        return data;
    }

    CompoundTag save(CompoundTag tag)
    {
        CompoundTag owners = new CompoundTag();
        this.unlocked.forEach((owner, looks) -> owners.putInt(owner.toString(), looks));
        tag.put("Owners", owners);
        return tag;
    }
}
