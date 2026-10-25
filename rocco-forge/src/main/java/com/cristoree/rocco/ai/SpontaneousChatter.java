package com.cristoree.rocco.ai;

import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.ai.personality.PersonalityTexts;
import com.cristoree.rocco.ai.personality.Traits;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.util.Text;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// Cuando hay silencio un rato, Rocco dice algo por su cuenta: un recordatorio de algo que paso,
// un comentario de lo que lleva el jugador o de lo que hay alrededor, un dato, una pregunta. Cada
// cuanto lo decide el rasgo "comentarios por su cuenta"; que decir lo elige el modelo, con una
// idea distinta cada vez y con lo que estuvo haciendo el jugador desde la ultima vez.
public final class SpontaneousChatter
{
    private static final long MINUTE = 1200L;
    private static final int REMEMBERED_IDEAS = 4;

    // "Callate un rato": no habla por su cuenta por un buen rato. "Habla mas": al reves.
    private static final Pattern QUIET = Pattern.compile(
            "\\b(callate|calla|silencio|shh+|no hables|deja de hablar|cerra el pico|cierra el pico|basta de hablar|hablas mucho)\\b");
    private static final Pattern CHATTY = Pattern.compile(
            "\\b(habla mas|hablame mas|charla mas|deci algo|di algo|estas muy callado|estas callado)\\b");

    private long lastActivity = -1L;
    private long gap;
    private long mutedUntil;
    private long chattyUntil;
    private final Deque<String> recentIdeas = new ArrayDeque<>();

    // Lo que habia hecho el jugador la ultima vez que se miro, para contar lo de ahora.
    private long walkedAtStart = -1L;
    private int minedAtStart;
    private int killsAtStart;
    private int craftedAtStart;
    private long statsSince;

    // Alguien hablo (el jugador o Rocco): el silencio arranca de nuevo.
    public void activity(long now, PersonalityProfile profile, RandomSource random)
    {
        this.lastActivity = now;
        this.gap = pickGap(profile, now, random);
    }

    // El jugador le hablo: ademas de cortar el silencio, mira si le pidio que se calle o que hable.
    public void heard(String message, long now)
    {
        String text = Text.normalize(message);
        if (QUIET.matcher(text).find())
        {
            this.mutedUntil = now + 20 * MINUTE;
            this.chattyUntil = 0L;
        }
        else if (CHATTY.matcher(text).find())
        {
            this.chattyUntil = now + 30 * MINUTE;
            this.mutedUntil = 0L;
        }
    }

    public boolean due(long now, PersonalityProfile profile, RandomSource random)
    {
        if (this.lastActivity < 0L)
        {
            // Recien creado (el mundo recien abierto): el primer silencio arranca ahora.
            activity(now, profile, random);
            return false;
        }
        return now >= this.mutedUntil && now - this.lastActivity >= this.gap;
    }

    private long pickGap(PersonalityProfile profile, long now, RandomSource random)
    {
        long[] range = switch (profile.spontaneous())
        {
            case POCOS -> new long[]{6 * MINUTE, 10 * MINUTE};
            case NORMAL -> new long[]{3 * MINUTE, 6 * MINUTE};
            case MUCHOS -> new long[]{90 * 20L, 3 * MINUTE};
        };
        long value = range[0] + (long) (random.nextDouble() * (range[1] - range[0]));
        return now < this.chattyUntil ? value / 2 : value;
    }

    // La nota que recibe el modelo en vez de un mensaje del jugador.
    public String note(RoccoEntity rocco, ServerPlayer player, long now, PersonalityProfile profile,
                       boolean remembersSomething, RandomSource random)
    {
        long minutes = Math.max(1L, (now - this.lastActivity) / MINUTE);
        String idea = pickIdea(profile, remembersSomething, random);
        return PersonalityTexts.spontaneousNote()
                .replace("{{minutes}}", String.valueOf(minutes))
                .replace("{{idea}}", idea)
                .replace("{{activity}}", activity(rocco, player, now));
    }

    private String pickIdea(PersonalityProfile profile, boolean remembersSomething, RandomSource random)
    {
        Map<String, String> ideas = PersonalityTexts.spontaneousIdeas();
        List<String> pool = new ArrayList<>();
        for (String key : ideas.keySet())
        {
            if (this.recentIdeas.contains(key))
            {
                continue;
            }
            if ((key.equals("memory") || key.equals("promise")) && !remembersSomething)
            {
                continue;
            }
            int weight = switch (key)
            {
                // Traer de vuelta lo que paso es lo mas gracioso que tiene: pesa mas.
                case "memory", "promise" -> 3;
                case "fact" -> profile.facts() == Traits.Amount.POCOS ? 0 : profile.facts() == Traits.Amount.MUCHOS ? 3 : 1;
                case "question" -> profile.questions() == Traits.Questions.POCAS ? 0 : profile.questions() == Traits.Questions.MUCHAS ? 3 : 1;
                case "world" -> 1;
                default -> 2;
            };
            for (int i = 0; i < weight; i++)
            {
                pool.add(key);
            }
        }
        if (pool.isEmpty())
        {
            this.recentIdeas.clear();
            pool.addAll(ideas.keySet());
        }

        String chosen = pool.get(random.nextInt(pool.size()));
        this.recentIdeas.addLast(chosen);
        while (this.recentIdeas.size() > REMEMBERED_IDEAS)
        {
            this.recentIdeas.removeFirst();
        }
        return ideas.getOrDefault(chosen, "say whatever fits the moment");
    }

    // "in the last 5 minutes: walked 3 blocks, mined nothing, killed nothing; has barely moved
    // from the same spot for 8 minutes". Con esto sale el "ibas a ser productivo, no?".
    private String activity(RoccoEntity rocco, ServerPlayer player, long now)
    {
        ServerStatsCounter stats = player.getStats();
        long walked = (long) stats.getValue(Stats.CUSTOM.get(Stats.WALK_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.SWIM_ONE_CM))
                + stats.getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
        int mined = 0;
        for (Block block : ForgeRegistries.BLOCKS)
        {
            mined += stats.getValue(Stats.BLOCK_MINED.get(block));
        }
        int kills = stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS));
        int crafted = 0;
        for (net.minecraft.world.item.Item item : ForgeRegistries.ITEMS)
        {
            crafted += stats.getValue(Stats.ITEM_CRAFTED.get(item));
        }

        List<String> parts = new ArrayList<>();
        if (this.walkedAtStart >= 0L)
        {
            long minutes = Math.max(1L, (now - this.statsSince) / MINUTE);
            long blocks = (walked - this.walkedAtStart) / 100L;
            int newMined = mined - this.minedAtStart;
            int newKills = kills - this.killsAtStart;
            int newCrafted = crafted - this.craftedAtStart;
            parts.add("in the last " + minutes + " minutes: walked " + blocks + " blocks, "
                    + (newMined == 0 ? "mined nothing" : "mined " + newMined + " blocks") + ", "
                    + (newKills == 0 ? "killed nothing" : "killed " + newKills + " mobs") + ", "
                    + (newCrafted == 0 ? "crafted nothing" : "crafted " + newCrafted + " items"));
        }
        this.walkedAtStart = walked;
        this.minedAtStart = mined;
        this.killsAtStart = kills;
        this.craftedAtStart = crafted;
        this.statsSince = now;

        int stillMinutes = rocco.ownerStillTicks() / 1200;
        if (stillMinutes >= 3)
        {
            parts.add("has barely moved from the same spot for " + stillMinutes + " minutes");
        }
        if (player.blockPosition().getY() < 40)
        {
            parts.add("is underground (Y " + player.blockPosition().getY() + ")");
        }
        if (player.isInWater())
        {
            parts.add("is in the water");
        }
        if (player.isPassenger())
        {
            parts.add("is riding something");
        }
        return parts.isEmpty() ? "nothing special to report" : String.join("; ", parts);
    }
}
