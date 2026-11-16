package com.cristoree.rocco.entity;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.storage.ValueInput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

// Datos guardados: desde 1.21.6 las entidades se guardan con ValueInput/ValueOutput. Rocco sigue
// usando un CompoundTag con las mismas claves de siempre, asi un mundo de 1.21.1 carga igual.
public final class Nbt
{
    // Todo lo que trae el guardado, como un CompoundTag.
    public static CompoundTag of(ValueInput input)
    {
        CompoundTag tag = new CompoundTag();
        for (String key : input.keySet())
        {
            input.read(key, ExtraCodecs.NBT).ifPresent(value -> tag.put(key, value));
        }
        return tag;
    }

    public static boolean hasUUID(CompoundTag tag, String key)
    {
        return tag.read(key, UUIDUtil.CODEC).isPresent();
    }

    @Nullable
    public static UUID getUUID(CompoundTag tag, String key)
    {
        return tag.read(key, UUIDUtil.CODEC).orElse(null);
    }

    public static void putUUID(CompoundTag tag, String key, UUID value)
    {
        tag.store(key, UUIDUtil.CODEC, value);
    }

    private Nbt() {}
}
