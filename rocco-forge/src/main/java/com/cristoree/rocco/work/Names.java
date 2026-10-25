package com.cristoree.rocco.work;

import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Traduce entre lo que dice la gente ("3 palitos", "el pico de piedra") y los items del juego,
// y al reves: arma frases como "3 de roca y 2 palos" con los nombres del idioma del juego.
public final class Names
{
    // Lo que pidieron: uno o varios items posibles ("tablas" sirve de cualquier madera).
    public record Query(String text, List<Item> items, int count, boolean all)
    {
        public boolean found()
        {
            return !this.items.isEmpty();
        }

        public Item first()
        {
            return this.items.isEmpty() ? Items.AIR : this.items.get(0);
        }

        public Predicate<ItemStack> filter()
        {
            return stack -> this.items.contains(stack.getItem());
        }

        public boolean generic()
        {
            return this.items.size() > 1;
        }
    }

    private static final Pattern NUMBER = Pattern.compile("\\b(\\d{1,4})\\b");
    private static final Map<String, Integer> NUMBER_WORDS = Map.ofEntries(
            Map.entry("un", 1), Map.entry("una", 1), Map.entry("uno", 1), Map.entry("dos", 2),
            Map.entry("tres", 3), Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6),
            Map.entry("siete", 7), Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10),
            Map.entry("once", 11), Map.entry("doce", 12), Map.entry("quince", 15), Map.entry("veinte", 20),
            Map.entry("treinta", 30), Map.entry("cuarenta", 40), Map.entry("cincuenta", 50),
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("ten", 10));
    private static final Pattern STACK = Pattern.compile("\\b(un|una|1) (stack|pila|pilon)\\b");
    private static final Pattern ALL = Pattern.compile("\\b(todo|todos|todas|todito|all|everything)\\b");

    private static final Pattern FILLER = Pattern.compile(
            "\\b(el|la|los|las|lo|unos|unas|un|una|uno|al|del|me|te|mi|tu|ese|esa|esos|esas|este|esta|estos|estas|"
            + "eso|esto|algo|de|por favor|porfa|ahora|ya|aca|ahi|alla|mas|cosas?|items?|mio|tuyo|para mi)\\b");

    private static final Map<String, String> WORDS = new HashMap<>();
    private static final Map<String, TagKey<Item>> GROUPS = new HashMap<>();
    private static final Map<TagKey<Item>, String> GROUP_FALLBACK = Map.of(
            ItemTags.PLANKS, "minecraft:oak_planks",
            ItemTags.LOGS, "minecraft:oak_log",
            ItemTags.SAPLINGS, "minecraft:oak_sapling",
            ItemTags.WOOL, "minecraft:white_wool",
            ItemTags.COALS, "minecraft:coal");

    static
    {
        String[][] materials = {
                {"madera", "wooden"}, {"piedra", "stone"}, {"hierro", "iron"}, {"oro", "golden"},
                {"diamante", "diamond"}, {"netherita", "netherite"}};
        String[][] tools = {
                {"pico", "pickaxe"}, {"hacha", "axe"}, {"pala", "shovel"}, {"azada", "hoe"}, {"espada", "sword"}};
        for (String[] tool : tools)
        {
            for (String[] material : materials)
            {
                WORDS.put(tool[0] + " de " + material[0], "minecraft:" + material[1] + "_" + tool[1]);
                WORDS.put(tool[0] + " " + material[0], "minecraft:" + material[1] + "_" + tool[1]);
            }
        }

        String[][] armorMaterials = {
                {"cuero", "leather"}, {"malla", "chainmail"}, {"hierro", "iron"}, {"oro", "golden"},
                {"diamante", "diamond"}, {"netherita", "netherite"}};
        String[][] armor = {
                {"casco", "helmet"}, {"pechera", "chestplate"}, {"peto", "chestplate"},
                {"pantalones", "leggings"}, {"pantalon", "leggings"}, {"grebas", "leggings"}, {"botas", "boots"}};
        for (String[] piece : armor)
        {
            for (String[] material : armorMaterials)
            {
                WORDS.put(piece[0] + " de " + material[0], "minecraft:" + material[1] + "_" + piece[1]);
            }
        }

        word("minecraft:crafting_table", "mesa de crafteo", "mesa de crafting", "mesa de trabajo", "mesa de crafteos",
                "mesa", "crafting table", "workbench", "mesa de fabricacion");
        word("minecraft:furnace", "horno", "furnace");
        word("minecraft:smoker", "ahumador", "smoker");
        word("minecraft:blast_furnace", "alto horno", "blast furnace");
        word("minecraft:chest", "cofre", "baul", "chest");
        word("minecraft:ender_chest", "cofre de ender", "cofre del ender", "cofre ender", "ender chest",
                "enderchest", "cofre del end");
        word("minecraft:barrel", "barril");
        word("minecraft:torch", "antorcha", "torch");
        word("minecraft:stick", "palo", "palito", "stick", "baston");
        word("minecraft:cobblestone", "roca", "piedra", "piedra labrada", "adoquin", "cobble", "cobblestone");
        word("minecraft:stone", "piedra lisa", "stone");
        word("minecraft:coal", "carbon", "coal");
        word("minecraft:charcoal", "carbon vegetal", "charcoal");
        word("minecraft:iron_ingot", "lingote de hierro", "hierro", "iron");
        word("minecraft:gold_ingot", "lingote de oro", "oro", "gold");
        word("minecraft:copper_ingot", "lingote de cobre", "cobre", "copper");
        word("minecraft:netherite_ingot", "lingote de netherita", "netherita");
        word("minecraft:diamond", "diamante", "diamond");
        word("minecraft:emerald", "esmeralda", "emerald");
        word("minecraft:lapis_lazuli", "lapislazuli", "lapis");
        word("minecraft:redstone", "redstone", "polvo de redstone");
        word("minecraft:bread", "pan", "bread");
        word("minecraft:potato", "papa", "patata", "potato");
        word("minecraft:baked_potato", "papa asada", "papas asadas", "patata asada", "baked potato");
        word("minecraft:carrot", "zanahoria", "carrot");
        word("minecraft:wheat", "trigo", "wheat");
        word("minecraft:wheat_seeds", "semillas", "semilla", "semillas de trigo", "seeds");
        word("minecraft:beetroot", "remolacha", "beetroot");
        word("minecraft:beetroot_seeds", "semillas de remolacha");
        word("minecraft:melon_slice", "sandia", "melon", "rodaja de sandia");
        word("minecraft:melon_seeds", "semillas de sandia", "semillas de melon");
        word("minecraft:pumpkin", "calabaza", "zapallo", "pumpkin");
        word("minecraft:pumpkin_seeds", "semillas de calabaza", "semillas de zapallo");
        word("minecraft:nether_wart", "verruga", "verruga del nether", "nether wart");
        word("minecraft:torchflower_seeds", "semillas de antorchaflor", "antorchaflor");
        word("minecraft:apple", "manzana", "apple");
        word("minecraft:beef", "carne", "carne cruda", "bife crudo", "beef");
        word("minecraft:cooked_beef", "carne asada", "bife", "filete", "churrasco", "steak");
        word("minecraft:porkchop", "cerdo", "chuleta", "chuleta cruda", "porkchop");
        word("minecraft:cooked_porkchop", "cerdo asado", "chuleta asada", "chuleta cocida");
        word("minecraft:chicken", "pollo", "pollo crudo", "chicken");
        word("minecraft:cooked_chicken", "pollo asado", "pollo cocido");
        word("minecraft:mutton", "cordero", "oveja cruda", "mutton");
        word("minecraft:cooked_mutton", "cordero asado", "cordero cocido");
        word("minecraft:cod", "pescado", "bacalao", "cod");
        word("minecraft:cooked_cod", "pescado asado", "bacalao asado", "pescado cocido");
        word("minecraft:salmon", "salmon");
        word("minecraft:cooked_salmon", "salmon asado", "salmon cocido");
        word("minecraft:rabbit", "conejo", "conejo crudo");
        word("minecraft:cooked_rabbit", "conejo asado", "conejo cocido");
        word("minecraft:cake", "torta", "pastel", "cake");
        word("minecraft:cookie", "galleta", "galletita", "cookie");
        word("minecraft:glass", "vidrio", "cristal", "glass");
        word("minecraft:sand", "arena", "sand");
        word("minecraft:gravel", "grava", "gravel");
        word("minecraft:dirt", "tierra", "dirt");
        word("minecraft:clay_ball", "arcilla", "bola de arcilla");
        word("minecraft:brick", "ladrillo", "brick");
        word("minecraft:string", "hilo", "cuerda", "string");
        word("minecraft:feather", "pluma", "feather");
        word("minecraft:arrow", "flecha", "arrow");
        word("minecraft:bow", "arco", "bow");
        word("minecraft:shield", "escudo", "shield");
        word("minecraft:bucket", "balde", "cubo", "bucket");
        word("minecraft:water_bucket", "balde de agua", "cubo de agua");
        word("minecraft:fishing_rod", "cana de pescar", "cana", "fishing rod");
        word("minecraft:shears", "tijeras", "tijera", "shears");
        word("minecraft:flint_and_steel", "mechero", "encendedor", "yesquero");
        word("minecraft:flint", "pedernal", "flint");
        word("minecraft:leather", "cuero", "leather");
        word("minecraft:book", "libro", "book");
        word("minecraft:paper", "papel", "paper");
        word("minecraft:sugar_cane", "cana de azucar", "sugar cane");
        word("minecraft:sugar", "azucar", "sugar");
        word("minecraft:egg", "huevo", "egg");
        word("minecraft:milk_bucket", "leche", "balde de leche");
        word("minecraft:ladder", "escalera", "escalera de mano", "ladder");
        word("minecraft:lever", "palanca", "lever");
        word("minecraft:hopper", "tolva", "hopper");
        word("minecraft:bone", "hueso", "bone");
        word("minecraft:bone_meal", "harina de hueso", "polvo de hueso", "bone meal");
        word("minecraft:obsidian", "obsidiana", "obsidian");
        word("minecraft:oak_sapling", "roble", "arbolito de roble", "brote de roble");
        word("minecraft:birch_sapling", "abedul", "arbolito de abedul");
        word("minecraft:spruce_sapling", "abeto", "pino", "arbolito de abeto");
        word("minecraft:jungle_sapling", "jungla", "arbolito de jungla");
        word("minecraft:acacia_sapling", "acacia", "arbolito de acacia");
        word("minecraft:dark_oak_sapling", "roble oscuro", "arbolito de roble oscuro");
        word("minecraft:cherry_sapling", "cerezo", "arbolito de cerezo");
        word("minecraft:mangrove_propagule", "mangle", "propagulo de mangle");
        word("minecraft:white_bed", "cama", "bed");
        word("minecraft:oak_door", "puerta", "door");
        word("minecraft:oak_fence", "valla", "cerca", "fence");
        word("minecraft:oak_trapdoor", "trampilla", "trapdoor");
        word("minecraft:bowl", "cuenco", "bol", "bowl");
        word("minecraft:mushroom_stew", "guiso de hongos", "sopa de hongos", "estofado de hongos");
        word("minecraft:golden_apple", "manzana dorada", "manzana de oro");
        word("minecraft:iron_ore", "mineral de hierro");
        word("minecraft:raw_iron", "hierro crudo", "hierro en bruto");
        word("minecraft:raw_gold", "oro crudo", "oro en bruto");
        word("minecraft:raw_copper", "cobre crudo", "cobre en bruto");

        group(ItemTags.PLANKS, "tabla", "tablas", "tablon", "tablones", "planks", "tablas de madera");
        group(ItemTags.LOGS, "tronco", "troncos", "madera", "log", "logs", "wood");
        group(ItemTags.SAPLINGS, "arbolito", "arbol", "brote", "plantin", "sapling", "arboles", "arbolitos");
        group(ItemTags.WOOL, "lana", "wool");
        group(ItemTags.COALS, "combustible de carbon");
    }

    private static void word(String id, String... names)
    {
        for (String name : names)
        {
            WORDS.put(name, id);
        }
    }

    private static void group(TagKey<Item> tag, String... names)
    {
        for (String name : names)
        {
            GROUPS.put(name, tag);
        }
    }

    // Bichos por su nombre en castellano (singular y plural), al id de Minecraft.
    private static final Map<String, String> ENTITIES = new HashMap<>();

    static
    {
        entity("cow", "vaca", "vacas", "toro", "toros");
        entity("sheep", "oveja", "ovejas");
        entity("pig", "cerdo", "cerdos", "chancho", "chanchos", "puerco", "puercos");
        entity("chicken", "gallina", "gallinas", "pollo", "pollos");
        entity("rabbit", "conejo", "conejos");
        entity("horse", "caballo", "caballos");
        entity("donkey", "burro", "burros");
        entity("mule", "mula", "mulas");
        entity("wolf", "lobo", "lobos", "perro", "perros");
        entity("cat", "gato", "gatos");
        entity("llama", "llama", "llamas");
        entity("goat", "cabra", "cabras");
        entity("bee", "abeja", "abejas");
        entity("fox", "zorro", "zorros");
        entity("turtle", "tortuga", "tortugas");
        entity("panda", "panda", "pandas");
        entity("frog", "rana", "ranas");
        entity("camel", "camello", "camellos");
        entity("axolotl", "axolote", "axolotes", "ajolote", "ajolotes");
        entity("spider", "arana", "aranas");
        entity("cave_spider", "arana de cueva", "aranas de cueva");
        entity("skeleton", "esqueleto", "esqueletos");
        entity("zombie", "zombi", "zombis", "zombie", "zombies");
        entity("creeper", "creeper", "creepers");
        entity("enderman", "enderman", "endermans");
        entity("witch", "bruja", "brujas");
        entity("slime", "slime", "slimes", "limo", "limos");
        entity("phantom", "fantasma", "fantasmas", "phantom");
        entity("pillager", "saqueador", "saqueadores", "pillager");
        entity("drowned", "ahogado", "ahogados");
        entity("husk", "momia", "momias", "husk");
        entity("silverfish", "lepisma", "lepismas", "pececillo de plata");
        entity("cod", "bacalao", "bacalaos", "pez", "peces");
        entity("salmon", "salmon", "salmones");
        entity("squid", "calamar", "calamares");
    }

    private static void entity(String id, String... names)
    {
        for (String name : names)
        {
            ENTITIES.put(name, id);
        }
    }

    // "arana" -> "spider". El texto tiene que venir normalizado (sin tildes). null si no lo conoce.
    @org.jetbrains.annotations.Nullable
    public static String entityFor(String word)
    {
        return ENTITIES.get(word);
    }

    public static String normalize(String text)
    {
        String lower = text.toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .replaceAll("[^a-z0-9:_ ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // "minecraft:stone_pickaxe 2", "2 palitos", "todo", "un stack de antorchas"...
    public static Query parse(String text)
    {
        String clean = normalize(text);
        int count = 0;
        boolean all = false;

        Matcher stack = STACK.matcher(clean);
        if (stack.find())
        {
            count = 64;
            clean = stack.replaceAll(" ");
        }

        Matcher digits = NUMBER.matcher(clean);
        if (count == 0 && digits.find())
        {
            count = Integer.parseInt(digits.group(1));
            clean = clean.substring(0, digits.start()) + " " + clean.substring(digits.end());
        }

        if (count == 0)
        {
            for (String token : clean.split(" "))
            {
                Integer value = NUMBER_WORDS.get(token);
                if (value != null && value > 1)
                {
                    count = value;
                    clean = clean.replaceFirst("\\b" + token + "\\b", " ");
                    break;
                }
            }
        }

        if (ALL.matcher(clean).find())
        {
            all = true;
            clean = ALL.matcher(clean).replaceAll(" ");
        }

        String subject = FILLER.matcher(" " + clean + " ").replaceAll(" ").replaceAll("\\s+", " ").trim();
        // "de" se fue con el relleno: se vuelve a probar el texto con "de" para "pico de piedra".
        List<Item> items = resolve(clean.replaceAll("\\s+", " ").trim(), subject);

        return new Query(subject, items, count, all);
    }

    private static List<Item> resolve(String withConnectors, String subject)
    {
        if (subject.isEmpty() && withConnectors.isEmpty())
        {
            return List.of();
        }

        // 1) Un id de verdad.
        for (String token : withConnectors.split(" "))
        {
            if (token.contains(":") || token.contains("_"))
            {
                Item item = Inv.item(token.contains(":") ? token : "minecraft:" + token);
                if (item != Items.AIR)
                {
                    return List.of(item);
                }
            }
        }

        // 2) Diccionario: con conectores ("pico de piedra") y sin ellos, singular y plural.
        for (String candidate : candidates(withConnectors, subject))
        {
            String id = WORDS.get(candidate);
            if (id != null && Inv.item(id) != Items.AIR)
            {
                return List.of(Inv.item(id));
            }
            TagKey<Item> tag = GROUPS.get(candidate);
            if (tag != null)
            {
                List<Item> members = tagged(tag);
                if (!members.isEmpty())
                {
                    return members;
                }
                // Sin tags cargados (pasa antes de entrar a un mundo) queda el representante.
                Item fallback = Inv.item(GROUP_FALLBACK.getOrDefault(tag, "minecraft:air"));
                if (fallback != Items.AIR)
                {
                    return List.of(fallback);
                }
            }
        }

        // 3) Nombre visible en el idioma del juego, o el id sin el "minecraft:".
        String wanted = subject.isEmpty() ? withConnectors : subject;
        Item exact = null;
        Item partial = null;
        int partialLength = Integer.MAX_VALUE;

        for (Item item : ForgeRegistries.ITEMS)
        {
            if (item == Items.AIR)
            {
                continue;
            }

            String name = normalize(name(item));
            String path = pathOf(item).replace('_', ' ');

            if (name.equals(withConnectors) || name.equals(wanted) || path.equals(wanted) || singular(name).equals(singular(wanted)))
            {
                exact = item;
                break;
            }
            if ((name.contains(wanted) || path.contains(wanted)) && name.length() < partialLength)
            {
                partial = item;
                partialLength = name.length();
            }
        }

        if (exact != null)
        {
            return List.of(exact);
        }
        return partial == null || wanted.length() < 3 ? List.of() : List.of(partial);
    }

    private static List<String> candidates(String withConnectors, String subject)
    {
        List<String> out = new ArrayList<>();
        for (String base : new String[]{withConnectors, subject})
        {
            if (base.isEmpty())
            {
                continue;
            }
            out.add(base);
            out.add(singular(base));
            // "pico de piedra para mi" -> prueba tambien las primeras palabras.
            String[] words = base.split(" ");
            for (int length = Math.min(words.length - 1, 4); length >= 1; length--)
            {
                String head = String.join(" ", java.util.Arrays.copyOfRange(words, 0, length));
                out.add(head);
                out.add(singular(head));
            }
        }
        return out;
    }

    // "antorchas" -> "antorcha", "palitos de madera" -> "palito de madera".
    static String singular(String text)
    {
        StringBuilder out = new StringBuilder();
        for (String word : text.split(" "))
        {
            String one = word;
            if (word.length() > 4 && word.endsWith("es") && !word.endsWith("ses"))
            {
                String base = word.substring(0, word.length() - 2);
                one = WORDS.containsKey(base) || GROUPS.containsKey(base) ? base : word.substring(0, word.length() - 1);
            }
            else if (word.length() > 3 && word.endsWith("s"))
            {
                one = word.substring(0, word.length() - 1);
            }
            if (!out.isEmpty())
            {
                out.append(' ');
            }
            out.append(one);
        }
        return out.toString();
    }

    public static List<Item> tagged(TagKey<Item> tag)
    {
        var tags = ForgeRegistries.ITEMS.tags();
        List<Item> out = tags == null ? new ArrayList<>() : new ArrayList<>(tags.getTag(tag).stream().toList());
        // El de roble primero: es el que la gente imagina cuando dice "tablas".
        out.sort((a, b) -> Boolean.compare(!pathOf(a).startsWith("oak_"), !pathOf(b).startsWith("oak_")));
        return out;
    }

    // ---------- de items a texto ----------

    public static String name(Item item)
    {
        return Language.getInstance().getOrDefault(item.getDescriptionId(), pathOf(item).replace('_', ' '));
    }

    public static String name(ItemStack stack)
    {
        return stack.hasCustomHoverName() ? stack.getHoverName().getString() : name(stack.getItem());
    }

    public static String name(String id)
    {
        return name(Inv.item(id));
    }

    // "3 de Roca", "1 Mesa de crafteo", "2 Palos".
    public static String amount(String id, int count)
    {
        return count + " de " + name(id);
    }

    // Un grupo de opciones se nombra por lo que tienen en comun cuando se puede.
    public static String options(List<String> options)
    {
        if (options.size() == 1)
        {
            return name(options.get(0));
        }
        if (options.stream().allMatch(id -> id.endsWith("_planks")))
        {
            return "tablas (de cualquier madera)";
        }
        if (options.stream().allMatch(id -> id.endsWith("_log") || id.endsWith("_wood") || id.endsWith("_stem") || id.endsWith("_hyphae")))
        {
            return "troncos (de cualquier madera)";
        }
        if (options.contains("minecraft:cobblestone"))
        {
            return name("minecraft:cobblestone");
        }
        if (options.stream().allMatch(id -> id.endsWith("_wool")))
        {
            return "lana";
        }
        if (options.contains("minecraft:coal"))
        {
            return name("minecraft:coal");
        }
        return name(options.get(0)) + " (o parecido)";
    }

    public static String missing(List<CraftPlanner.Missing> missing)
    {
        List<String> parts = new ArrayList<>();
        for (CraftPlanner.Missing item : missing)
        {
            parts.add(item.count() + " de " + options(item.options()));
        }
        return joinList(parts);
    }

    public static String stacks(List<ItemStack> stacks)
    {
        Map<String, Integer> totals = new java.util.LinkedHashMap<>();
        for (ItemStack stack : stacks)
        {
            totals.merge(name(stack), stack.getCount(), Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        totals.forEach((name, count) -> parts.add(count + " de " + name));
        return joinList(parts);
    }

    // "a", "a y b", "a, b y c".
    public static String joinList(List<String> parts)
    {
        if (parts.isEmpty())
        {
            return "";
        }
        if (parts.size() == 1)
        {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " y " + parts.get(parts.size() - 1);
    }

    private static String pathOf(Item item)
    {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.getPath();
    }

    private Names() {}
}
