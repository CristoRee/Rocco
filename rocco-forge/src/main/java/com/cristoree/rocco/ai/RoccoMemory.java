package com.cristoree.rocco.ai;

import com.cristoree.rocco.RoccoMod;
import com.cristoree.rocco.util.Text;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Lo que Rocco recuerda de cada jugador, para traerlo de vuelta cuando menos se lo esperan:
// promesas, proyectos, metidas de pata, apuestas, contradicciones.
//  - De este mundo: se guarda con el mundo (y se pierde con el), con la hora del juego.
//  - Del jugador como persona (su apodo, su bebida favorita): en config/rocco/memoria/, vale en
//    todos los mundos, con la fecha real.
// Las notas las decide la IA con las lineas #recordar / #recordar_siempre / #olvidar.
public final class RoccoMemory
{
    public record Note(String text, long time) {}

    private static final int MAX_WORLD = 40;
    private static final int MAX_PERSON = 30;
    private static final int SHOWN_WORLD = 14;
    private static final int SHOWN_PERSON = 12;
    private static final int MAX_CHARS = 180;
    private static final long TICKS_PER_DAY = 24000L;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<UUID, List<Note>> PERSON = new HashMap<>();

    // ---------- guardar y olvidar ----------

    public static void remember(ServerPlayer player, String note, boolean person)
    {
        String text = note.replaceAll("\\s+", " ").trim();
        if (text.isEmpty())
        {
            return;
        }
        if (text.length() > MAX_CHARS)
        {
            text = text.substring(0, MAX_CHARS).trim();
        }

        List<Note> notes = person ? personNotes(player.getUUID()) : worldNotes(player.getServer()).of(player.getUUID());
        long now = person ? System.currentTimeMillis() : player.getServer().overworld().getGameTime();

        // La misma nota otra vez: se refresca la fecha en vez de duplicarla.
        String key = Text.normalize(text);
        notes.removeIf(existing -> Text.normalize(existing.text()).equals(key));
        notes.add(new Note(text, now));
        while (notes.size() > (person ? MAX_PERSON : MAX_WORLD))
        {
            notes.remove(0);
        }

        if (person)
        {
            savePerson(player.getUUID(), notes);
        }
        else
        {
            worldNotes(player.getServer()).setDirty();
        }
        debug("[Rocco] Recuerdo {} de {}: {}", person ? "siempre" : "en este mundo", player.getGameProfile().getName(), text);
    }

    // Saca las notas que tengan todas esas palabras (sin tildes ni mayusculas). Devuelve cuantas.
    public static int forget(ServerPlayer player, String words)
    {
        List<String> wanted = new ArrayList<>();
        for (String word : Text.normalize(words).split("[^a-z0-9]+"))
        {
            if (word.length() >= 3)
            {
                wanted.add(word);
            }
        }
        if (wanted.isEmpty())
        {
            return 0;
        }

        List<Note> world = worldNotes(player.getServer()).of(player.getUUID());
        List<Note> person = personNotes(player.getUUID());
        int before = world.size() + person.size();
        world.removeIf(note -> matchesAll(note, wanted));
        boolean personChanged = person.removeIf(note -> matchesAll(note, wanted));

        int removed = before - world.size() - person.size();
        if (removed > 0)
        {
            worldNotes(player.getServer()).setDirty();
            if (personChanged)
            {
                savePerson(player.getUUID(), person);
            }
            debug("[Rocco] Olvide {} nota(s) de {} con: {}", removed, player.getGameProfile().getName(), words);
        }
        return removed;
    }

    private static boolean matchesAll(Note note, List<String> words)
    {
        String text = Text.normalize(note.text());
        return words.stream().allMatch(text::contains);
    }

    public static boolean isEmpty(ServerPlayer player)
    {
        return worldNotes(player.getServer()).of(player.getUUID()).isEmpty() && personNotes(player.getUUID()).isEmpty();
    }

    // ---------- para el bloque RIGHT NOW ----------

    // "about them as a person: su bebida favorita es el cafe (3 days ago); in this world: dijo que
    // hoy iba a ser productivo (25 minutes ago)". Vacio si no recuerda nada.
    public static String describe(ServerPlayer player)
    {
        List<String> parts = new ArrayList<>();

        List<Note> person = personNotes(player.getUUID());
        long nowMillis = System.currentTimeMillis();
        List<String> personParts = new ArrayList<>();
        for (Note note : last(person, SHOWN_PERSON))
        {
            personParts.add(note.text() + " (" + realAgo(nowMillis - note.time()) + ")");
        }
        if (!personParts.isEmpty())
        {
            parts.add("about them as a person: " + String.join("; ", personParts));
        }

        List<Note> world = worldNotes(player.getServer()).of(player.getUUID());
        long nowTicks = player.getServer().overworld().getGameTime();
        List<String> worldParts = new ArrayList<>();
        for (Note note : last(world, SHOWN_WORLD))
        {
            worldParts.add(note.text() + " (" + gameAgo(nowTicks - note.time()) + ")");
        }
        if (!worldParts.isEmpty())
        {
            parts.add("in this world: " + String.join("; ", worldParts));
        }

        return String.join(" | ", parts);
    }

    private static List<Note> last(List<Note> notes, int count)
    {
        return notes.size() <= count ? notes : notes.subList(notes.size() - count, notes.size());
    }

    // Un dia de Minecraft dura 20 minutos: hasta la hora de juego se cuenta en minutos, que es
    // como lo diria una persona ("hace 25 minutos dijiste que ibas a ser productivo").
    static String gameAgo(long ticks)
    {
        long minutes = Math.max(0L, ticks) / 1200L;
        if (minutes < 60)
        {
            return minutes <= 1 ? "just now" : minutes + " minutes of play ago";
        }
        long days = ticks / TICKS_PER_DAY;
        return days == 1 ? "1 in-game day ago" : days + " in-game days ago";
    }

    static String realAgo(long millis)
    {
        long minutes = Math.max(0L, millis) / 60_000L;
        if (minutes < 60)
        {
            return minutes <= 1 ? "just now" : minutes + " minutes ago";
        }
        long hours = minutes / 60;
        if (hours < 24)
        {
            return hours == 1 ? "1 hour ago" : hours + " hours ago";
        }
        long days = hours / 24;
        return days == 1 ? "yesterday" : days + " days ago";
    }

    // ---------- lo del jugador como persona: archivo propio ----------

    private static synchronized List<Note> personNotes(UUID player)
    {
        return PERSON.computeIfAbsent(player, RoccoMemory::loadPerson);
    }

    private static Path personFile(UUID player)
    {
        return FMLPaths.CONFIGDIR.get().resolve("rocco").resolve("memoria").resolve(player + ".json");
    }

    private static List<Note> loadPerson(UUID player)
    {
        Path file = personFile(player);
        try
        {
            if (Files.exists(file))
            {
                List<Note> loaded = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8),
                        new TypeToken<List<Note>>() {}.getType());
                if (loaded != null)
                {
                    List<Note> clean = new ArrayList<>();
                    for (Note note : loaded)
                    {
                        if (note != null && note.text() != null && !note.text().isBlank())
                        {
                            clean.add(note);
                        }
                    }
                    return clean;
                }
            }
        }
        catch (IOException | RuntimeException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] No pude leer la memoria de {}: {}", player, e.toString());
        }
        return new ArrayList<>();
    }

    private static void savePerson(UUID player, List<Note> notes)
    {
        Path file = personFile(player);
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, GSON.toJson(notes), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            RoccoMod.LOGGER.warn("[Rocco] No pude guardar la memoria de {}: {}", player, e.toString());
        }
    }

    // ---------- lo de este mundo: va con el guardado del mundo ----------

    private static WorldNotes worldNotes(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(WorldNotes::load, WorldNotes::new, "rocco_memory");
    }

    static final class WorldNotes extends SavedData
    {
        private final Map<UUID, List<Note>> notes = new HashMap<>();

        List<Note> of(UUID player)
        {
            return this.notes.computeIfAbsent(player, key -> new ArrayList<>());
        }

        static WorldNotes load(CompoundTag tag)
        {
            WorldNotes data = new WorldNotes();
            CompoundTag players = tag.getCompound("Players");
            for (String key : players.getAllKeys())
            {
                UUID id;
                try
                {
                    id = UUID.fromString(key);
                }
                catch (IllegalArgumentException e)
                {
                    continue;
                }
                List<Note> list = data.of(id);
                for (Tag entry : players.getList(key, Tag.TAG_COMPOUND))
                {
                    CompoundTag note = (CompoundTag) entry;
                    list.add(new Note(note.getString("Text"), note.getLong("Time")));
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag)
        {
            CompoundTag players = new CompoundTag();
            this.notes.forEach((id, list) ->
            {
                if (list.isEmpty())
                {
                    return;
                }
                ListTag entries = new ListTag();
                for (Note note : list)
                {
                    CompoundTag entry = new CompoundTag();
                    entry.putString("Text", note.text());
                    entry.putLong("Time", note.time());
                    entries.add(entry);
                }
                players.put(id.toString(), entries);
            });
            tag.put("Players", players);
            return tag;
        }
    }

    private static void debug(String message, Object... args)
    {
        if (com.cristoree.rocco.config.RoccoCommonConfig.DEBUG_LOGGING.get())
        {
            RoccoMod.LOGGER.info(message, args);
        }
    }

    private RoccoMemory() {}
}
