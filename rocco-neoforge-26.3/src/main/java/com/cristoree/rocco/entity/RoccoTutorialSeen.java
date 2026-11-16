package com.cristoree.rocco.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

// Quienes ya vieron la guia en este mundo: le aparece a cada jugador la primera vez que le nace un
// Rocco aca. Va con el guardado del mundo, asi en un mundo nuevo vuelve a salir.
final class RoccoTutorialSeen extends SavedData
{
    private static final String FILE = "rocco_tutorial_seen";

    // Desde 26.1 los datos del mundo se guardan con un codec. El id "minecraft:rocco_tutorial_seen" usa el mismo
    // archivo de antes (data/rocco_tutorial_seen.dat), asi un mundo de 1.21.1 conserva lo que tenia.
    static final net.minecraft.world.level.saveddata.SavedDataType<RoccoTutorialSeen> TYPE = new net.minecraft.world.level.saveddata.SavedDataType<>(
            net.minecraft.resources.Identifier.withDefaultNamespace(FILE), RoccoTutorialSeen::new,
            net.minecraft.nbt.CompoundTag.CODEC.xmap(RoccoTutorialSeen::load, data -> data.save(new net.minecraft.nbt.CompoundTag())), net.minecraft.util.datafix.DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private final Set<UUID> players = new HashSet<>();

    // true solo la primera vez que se pregunta por ese jugador en este mundo (y lo anota).
    static boolean firstTime(MinecraftServer server, UUID player)
    {
        RoccoTutorialSeen data = server.overworld().getDataStorage()
                .computeIfAbsent(TYPE);
        if (!data.players.add(player))
        {
            return false;
        }
        data.setDirty();
        return true;
    }

    private static RoccoTutorialSeen load(CompoundTag tag)
    {
        RoccoTutorialSeen data = new RoccoTutorialSeen();
        for (Tag one : tag.getListOrEmpty("Players"))
        {
            try
            {
                data.players.add(net.minecraft.core.UUIDUtil.uuidFromIntArray(((net.minecraft.nbt.IntArrayTag) one).getAsIntArray()));
            }
            catch (IllegalArgumentException ignored)
            {
                // Uno roto no tiene que impedir cargar el resto.
            }
        }
        return data;
    }

    CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        this.players.forEach(player -> list.add(new net.minecraft.nbt.IntArrayTag(net.minecraft.core.UUIDUtil.uuidToIntArray(player))));
        tag.put("Players", list);
        return tag;
    }
}
