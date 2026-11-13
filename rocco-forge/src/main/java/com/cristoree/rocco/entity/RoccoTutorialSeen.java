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

    private final Set<UUID> players = new HashSet<>();

    // true solo la primera vez que se pregunta por ese jugador en este mundo (y lo anota).
    static boolean firstTime(MinecraftServer server, UUID player)
    {
        RoccoTutorialSeen data = server.overworld().getDataStorage()
                .computeIfAbsent(RoccoTutorialSeen::load, RoccoTutorialSeen::new, FILE);
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
        for (Tag one : tag.getList("Players", Tag.TAG_INT_ARRAY))
        {
            try
            {
                data.players.add(NbtUtils.loadUUID(one));
            }
            catch (IllegalArgumentException ignored)
            {
                // Uno roto no tiene que impedir cargar el resto.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        this.players.forEach(player -> list.add(NbtUtils.createUUID(player)));
        tag.put("Players", list);
        return tag;
    }
}
