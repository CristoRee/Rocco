package com.cristoree.rocco.work;

import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Lee un pedido de encantamiento: "el pico con eficiencia 5", "esta espada con filo V",
// "minecraft:diamond_pickaxe minecraft:efficiency 5". Separa la parte del item de la del
// encantamiento. Si no nombra ninguno, es un encantamiento al azar como el de una mesa.
public final class EnchantRequest
{
    // item: lo que queda para saber QUE encantar. level 0 = el maximo de ese encantamiento.
    public record Parsed(String item, @Nullable Enchantment enchantment, int level)
    {
        public boolean specific()
        {
            return this.enchantment != null;
        }
    }

    // Nombres en castellano (los oficiales y los que usa la gente) a su id.
    private static final Map<String, String> WORDS = new HashMap<>();

    static
    {
        word("efficiency", "eficiencia", "eficacia");
        word("fortune", "fortuna");
        word("silk_touch", "toque de seda", "toque sedoso", "seda");
        word("unbreaking", "irrompibilidad", "irrompible", "durabilidad");
        word("mending", "reparacion", "remiendo", "reparar");
        word("sharpness", "filo", "afilado", "afilada", "nitidez");
        word("smite", "castigo", "golpe sagrado");
        word("bane_of_arthropods", "perdicion de los artropodos", "perdicion de artropodos", "artropodos");
        word("fire_aspect", "aspecto igneo", "aspecto de fuego");
        word("knockback", "empuje");
        word("looting", "botin", "saqueo");
        word("sweeping", "filo arrasador", "barrido", "golpe de barrido");
        word("protection", "proteccion");
        word("fire_protection", "proteccion contra el fuego", "proteccion de fuego", "proteccion contra fuego");
        word("blast_protection", "proteccion contra explosiones", "proteccion de explosiones");
        word("projectile_protection", "proteccion contra proyectiles", "proteccion de proyectiles");
        word("feather_falling", "caida de pluma", "caida suave", "caida de plumas");
        word("respiration", "respiracion");
        word("aqua_affinity", "afinidad acuatica", "afinidad al agua");
        word("thorns", "espinas");
        word("depth_strider", "agilidad acuatica", "paso profundo", "zancada acuatica");
        word("frost_walker", "paso helado", "pies de escarcha", "caminante helado");
        word("soul_speed", "velocidad de alma", "velocidad del alma");
        word("swift_sneak", "sigilo rapido", "agacharse rapido");
        word("power", "poder");
        word("punch", "retroceso");
        word("flame", "llama");
        word("infinity", "infinidad", "infinito");
        word("luck_of_the_sea", "suerte marina", "suerte del mar");
        word("lure", "atraccion", "senuelo");
        word("loyalty", "lealtad");
        word("impaling", "empalamiento");
        word("riptide", "propulsion acuatica");
        word("channeling", "canalizacion");
        word("multishot", "multidisparo", "disparo multiple");
        word("quick_charge", "carga rapida");
        word("piercing", "perforacion");
        word("binding_curse", "maldicion de ligamiento");
        word("vanishing_curse", "maldicion de desaparicion");
    }

    private static void word(String id, String... names)
    {
        for (String name : names)
        {
            WORDS.put(name, "minecraft:" + id);
        }
    }

    private static final Pattern ID = Pattern.compile("\\b([a-z0-9_.-]+:[a-z0-9_/.-]+)\\b");
    private static final Map<String, Integer> NUMBERS = Map.of(
            "uno", 1, "dos", 2, "tres", 3, "cuatro", 4, "cinco", 5,
            "i", 1, "ii", 2, "iii", 3, "iv", 4, "v", 5);

    public static Parsed parse(String argument)
    {
        String text = Names.normalize(argument == null ? "" : argument);

        // 1) Un id de encantamiento escrito tal cual (lo que suele mandar la IA).
        Matcher ids = ID.matcher(text);
        while (ids.find())
        {
            ResourceLocation key = ResourceLocation.tryParse(ids.group(1));
            Enchantment found = key == null ? null : ForgeRegistries.ENCHANTMENTS.getValue(key);
            if (found != null)
            {
                return finish(text, ids.start(), ids.end(), found);
            }
        }

        // 2) Por nombre: el mas largo que aparezca (asi "proteccion contra el fuego" gana a "proteccion").
        String bestName = null;
        Enchantment best = null;
        for (Map.Entry<String, Enchantment> entry : names().entrySet())
        {
            String name = entry.getKey();
            if (containsWord(text, name) && (bestName == null || name.length() > bestName.length()))
            {
                bestName = name;
                best = entry.getValue();
            }
        }
        if (best != null)
        {
            int start = indexOfWord(text, bestName);
            return finish(text, start, start + bestName.length(), best);
        }

        // 3) Mal escrito ("efficiencia", "irrompibilida"): una palabra larga casi igual a un nombre.
        String[] words = text.split(" ");
        for (int i = 0; i < words.length; i++)
        {
            // Palabras cortas o que ya son un item ("botas") no se corrigen: se confundirian.
            int allowed = words[i].length() >= 7 ? 2 : words[i].length() >= 5 ? 1 : 0;
            if (allowed == 0 || Names.parse(words[i]).found())
            {
                continue;
            }
            for (Map.Entry<String, Enchantment> entry : names().entrySet())
            {
                if (!entry.getKey().contains(" ") && distance(words[i], entry.getKey()) <= allowed)
                {
                    int start = indexOfWord(text, words[i]);
                    return finish(text, start, start + words[i].length(), entry.getValue());
                }
            }
        }

        return new Parsed(text, null, 0);
    }

    // Separa el nivel que venga justo despues ("eficiencia 5", "filo V") y deja el resto para el item.
    private static Parsed finish(String text, int start, int end, Enchantment enchantment)
    {
        String after = text.substring(Math.min(end, text.length())).trim();
        int level = 0;
        String[] next = after.split(" ");
        if (next.length > 0 && !next[0].isEmpty())
        {
            String token = next[0].replace("nivel", "").trim();
            if (token.matches("\\d{1,3}"))
            {
                level = Integer.parseInt(token);
                after = after.substring(next[0].length()).trim();
            }
            else if (NUMBERS.containsKey(token))
            {
                level = NUMBERS.get(token);
                after = after.substring(next[0].length()).trim();
            }
            else if (token.isEmpty() && next.length > 1 && next[1].matches("\\d{1,3}"))
            {
                level = Integer.parseInt(next[1]);
            }
        }

        String item = (text.substring(0, start) + " " + after)
                .replaceAll("\\b(con|de|el encantamiento|encantamiento|nivel)\\b", " ")
                .replaceAll("\\s+", " ").trim();
        return new Parsed(item, enchantment, level);
    }

    // Todos los nombres conocidos: los del diccionario, los del idioma del juego y los ids.
    private static Map<String, Enchantment> names()
    {
        Map<String, Enchantment> out = new HashMap<>();
        for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS)
        {
            ResourceLocation key = ForgeRegistries.ENCHANTMENTS.getKey(enchantment);
            if (key != null)
            {
                out.put(key.getPath().replace('_', ' '), enchantment);
            }
            String localized = Names.normalize(Language.getInstance().getOrDefault(enchantment.getDescriptionId(), ""));
            if (!localized.isEmpty())
            {
                out.put(localized, enchantment);
            }
        }
        WORDS.forEach((name, id) ->
        {
            Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(ResourceLocation.tryParse(id));
            if (enchantment != null)
            {
                out.put(name, enchantment);
            }
        });
        return out;
    }

    private static boolean containsWord(String text, String phrase)
    {
        return indexOfWord(text, phrase) >= 0;
    }

    private static int indexOfWord(String text, String phrase)
    {
        Matcher matcher = Pattern.compile("(^|\\s)" + Pattern.quote(phrase) + "($|\\s)").matcher(text);
        return matcher.find() ? matcher.start() + matcher.group(1).length() : -1;
    }

    private static int distance(String a, String b)
    {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++)
        {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++)
        {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++)
            {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }

    private EnchantRequest() {}
}
