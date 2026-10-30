package com.cristoree.rocco;

import com.cristoree.rocco.ai.OfflineRocco;
import com.cristoree.rocco.ai.personality.PersonalityProfile;
import com.cristoree.rocco.config.RoccoCommonConfig;
import com.cristoree.rocco.entity.RoccoEntity;
import com.cristoree.rocco.entity.RoccoExpression;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

// Las cosas raras de Rocco: muy de vez en cuando te regala algo que encontro (un palito perfecto,
// una florcita) o se le cae su diario, y si le pedis ciertas cosas en broma ("dame marihuana",
// "dame plata") te contesta con un regalito con nombre.
@Mod.EventBusSubscriber(modid = RoccoMod.MODID)
public final class RoccoSurprises
{
    private static final String TAG_DIARY = "rocco_diary";
    // Cuanto tiene el jugador para agarrar el diario antes de que Rocco lo levante de nuevo.
    private static final int DIARY_GRAB_TICKS = 20 * 15;

    // ---------- lo que se guarda en cada Rocco ----------

    // Cuenta regresiva hasta la proxima sorpresa: solo corre mientras estan juntos y tranquilos.
    public static final class State
    {
        private int ticksLeft = firstWait();
        private int diaries;
        @Nullable
        private UUID droppedDiary;
        private int diaryTicks;

        public void save(CompoundTag tag)
        {
            tag.putInt("SurpriseIn", this.ticksLeft);
            tag.putInt("Diaries", this.diaries);
        }

        public void load(CompoundTag tag)
        {
            if (tag.contains("SurpriseIn"))
            {
                this.ticksLeft = tag.getInt("SurpriseIn");
            }
            this.diaries = tag.getInt("Diaries");
        }

        // La primera, despues de media hora juntos (y un poco mas, al azar).
        private static int firstWait()
        {
            return 20 * 60 * (30 + java.util.concurrent.ThreadLocalRandom.current().nextInt(30));
        }
    }

    // ---------- sorpresas por su cuenta ----------

    private record Trinket(Item item, String name, String line, String... lore) {}

    private static final List<Trinket> TRINKETS = List.of(
            new Trinket(Items.STICK, "Palito perfecto", "trinket_stick", "Lo encontre yo.", "Es el mejor palito del mundo."),
            new Trinket(Items.COBBLESTONE, "Piedra con forma de corazon", "trinket_stone", "Si la miras de costado", "y entrecerras los ojos."),
            new Trinket(Items.POPPY, "Florcita", "trinket_flower", "La arranque para vos.", "No le digas a la abeja."),
            new Trinket(Items.FEATHER, "Pluma de la suerte", "trinket_feather", "Era de una gallina.", "Ahora es de la suerte."),
            new Trinket(Items.PAPER, "Dibujo de Rocco", "trinket_drawing", "Sos vos.", "Bueno, es un palito con cara. Pero sos vos."),
            new Trinket(Items.PAPER, "Lista de quejas", "trinket_complaints", "1. Me haces laburar.", "2. No me pagas.", "3. Ver punto 1."),
            new Trinket(Items.BONE, "Hueso de recuerdo", "trinket_bone", "De un esqueleto que conoci.", "Era buena gente."),
            new Trinket(Items.WHEAT_SEEDS, "Semillas magicas", "trinket_seeds", "No son magicas.", "Pero plantalas con fe."),
            new Trinket(Items.GLASS_BOTTLE, "Aire puro del bosque", "trinket_air", "Lo junte para vos.", "Respiralo de a poquito."));

    // Una vez por segundo desde Rocco, con el dueño cerca.
    public static void tick(RoccoEntity rocco, ServerPlayer owner)
    {
        State state = rocco.surprises();
        tickDiary(rocco, state);

        if (!RoccoCommonConfig.SURPRISES_ENABLED.get() || !rocco.isFollowing() || !rocco.isFreeTime()
                || rocco.distanceToSqr(owner) > 6.0D * 6.0D || owner.isSleeping() || owner.isSpectator()
                || rocco.isInWater() || rocco.hostileNearby())
        {
            return;
        }
        state.ticksLeft -= 20;
        if (state.ticksLeft > 0)
        {
            return;
        }

        // La proxima, entre 45 minutos y una hora y media juntos.
        RandomSource random = rocco.getRandom();
        state.ticksLeft = 20 * 60 * (45 + random.nextInt(46));
        if (random.nextFloat() < 0.35F)
        {
            dropDiary(rocco, owner, state);
        }
        else
        {
            Trinket trinket = TRINKETS.get(random.nextInt(TRINKETS.size()));
            rocco.lookAt(owner, 30.0F, 30.0F);
            rocco.sayLine(RoccoExpression.HAPPY, trinket.line());
            give(rocco, owner, named(new ItemStack(trinket.item()), trinket.name(), trinket.lore()));
        }
    }

    private static void give(RoccoEntity rocco, ServerPlayer player, ItemStack stack)
    {
        rocco.showInHand(stack);
        rocco.triggerAnim("main", "interact");
        UUID id = player.getUUID();
        rocco.schedule(8, () ->
        {
            if (rocco.level().getPlayerByUUID(id) instanceof ServerPlayer target)
            {
                RoccoPresents.hand(rocco, target, List.of(stack));
            }
        });
    }

    private static ItemStack named(ItemStack stack, String name, String... lore)
    {
        stack.setHoverName(Component.literal(name).withStyle(ChatFormatting.GOLD));
        ListTag lines = new ListTag();
        for (String line : lore)
        {
            lines.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(line).withStyle(ChatFormatting.GRAY))));
        }
        stack.getOrCreateTagElement("display").put("Lore", lines);
        return stack;
    }

    // ---------- el diario ----------

    // Se le cae de la tapa y tenes unos segundos para agarrarlo antes de que lo levante.
    private static void dropDiary(RoccoEntity rocco, ServerPlayer owner, State state)
    {
        if (!(rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        state.diaries++;
        ItemStack book = diary(rocco, owner, state.diaries);
        ItemEntity entity = new ItemEntity(level, rocco.getX(), rocco.getY() + 0.9D, rocco.getZ(), book);
        double angle = rocco.getRandom().nextDouble() * Math.PI * 2.0D;
        entity.setDeltaMovement(Math.cos(angle) * 0.12D, 0.28D, Math.sin(angle) * 0.12D);
        entity.setPickUpDelay(20);
        entity.setThrower(rocco.getUUID());
        level.addFreshEntity(entity);
        level.playSound(null, rocco.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.NEUTRAL, 1.0F, 1.0F);

        state.droppedDiary = entity.getUUID();
        state.diaryTicks = 0;
        rocco.sayLine(RoccoExpression.SCARED, "diary_drop");
    }

    // Si nadie lo agarro a tiempo, lo levanta el.
    private static void tickDiary(RoccoEntity rocco, State state)
    {
        if (state.droppedDiary == null || !(rocco.level() instanceof ServerLevel level))
        {
            return;
        }
        Entity entity = level.getEntity(state.droppedDiary);
        if (!(entity instanceof ItemEntity item) || !item.isAlive())
        {
            state.droppedDiary = null;
            return;
        }
        state.diaryTicks += 20;
        if (state.diaryTicks >= DIARY_GRAB_TICKS)
        {
            item.discard();
            state.droppedDiary = null;
            level.playSound(null, rocco.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.NEUTRAL, 0.5F, 1.2F);
            rocco.sayLine(RoccoExpression.SMUG, "diary_recovered");
        }
    }

    public static boolean isDiary(ItemStack stack)
    {
        return stack.is(Items.WRITTEN_BOOK) && stack.getTag() != null && stack.getTag().contains(TAG_DIARY);
    }

    // Lo agarraste antes que el.
    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isDiary(event.getStack()))
        {
            return;
        }
        UUID dropped = event.getOriginalEntity().getUUID();
        for (RoccoEntity rocco : player.serverLevel().getEntitiesOfClass(RoccoEntity.class,
                player.getBoundingBox().inflate(32.0D), RoccoEntity::isAlive))
        {
            State state = rocco.surprises();
            if (dropped.equals(state.droppedDiary))
            {
                state.droppedDiary = null;
                boolean owner = rocco.getOwnerId() == null || rocco.getOwnerId().equals(player.getUUID());
                rocco.sayLine(RoccoExpression.SCARED, owner ? "diary_picked" : "diary_picked_other");
                return;
            }
        }
    }

    // Lo que escribio, con humor de Rocco: se queja de que lo explotan, se burla con cariño y
    // de vez en cuando se le escapa algo tierno.
    private static final String[] ENTRIES = {
            "Hoy {player} me hizo craftear tres picos. TRES. Despues perdio los tres en la lava. Yo no digo nada, solo lo anoto.",
            "Me dijo papa otra vez. Respire hondo, conte hasta diez y le cebe un mate. Soy un santo.",
            "Trabajo sin sueldo, sin vacaciones y sin obra social. Si esto no es explotacion, que alguien me explique que es.",
            "{player} construyo una casa. Bah, una caja de tierra con una puerta. Le dije que estaba hermosa. Mentir tambien es cuidar.",
            "Hoy un creeper casi nos vuela. {player} grito como nunca. Yo tambien, pero eso no va en el diario. Ah, ya lo escribi.",
            "Querida Madre Tierra: gracias por el sol, la lluvia y el mate. Lo de {player} lo seguimos hablando.",
            "Hoy me puse a contar las veces que salve a {player}. Me quede sin numeros.",
            "Nota mental: esconder la yerba buena. {player} la encontro y no me dejo ni para un mate.",
            "Sone que era una papa comun y nadie me pedia nada. Fue la mejor noche de mi vida.",
            "{player} se perdio de nuevo. Le dije 'por aca' y fue para el otro lado. Cada dia lo quiero un poco mas y lo entiendo un poco menos.",
            "Hoy cosechamos. Bah, coseche yo. {player} miraba y decia 'que lindo'. Que alguien le pague un sueldo al que mira.",
            "Lista de cosas que {player} hace bien:\n1.\n2.\n3.\n(Sigo pensando.)",
            "Le pregunte a una vaca si era feliz. No me contesto. Igual me cae mejor que el ultimo zombie.",
            "Me pidio que guarde todo. TODO. Tenia 47 piedras distintas. Hay que ser coleccionista de piedras para entenderlo.",
            "Hoy baile un rato solo. Nadie me vio. Fue perfecto.",
            "{player} dice que yo hablo mucho. Lo dice mientras me habla. Interesante.",
            "Secreto: cuando duerme, lo tapo con mis hojitas para que no tenga frio. Si lee esto, lo niego todo.",
            "Me dijo 'traeme carbon'. Fui, volvi, se lo di. Ni gracias. Bueno, si dijo gracias. Pero sin ganas.",
            "Dia de lluvia. Las plantas felices, {player} mojado, yo divino. Un buen dia.",
            "Encontre diamantes y se los di. Me abrazo. Bah, me empujo sin querer. Lo cuento como abrazo.",
            "Hoy un aldeano me quiso vender una papa. A MI. Me fui sin decir nada. Con dignidad.",
            "Calculo que esta semana camine mas que {player} en toda su vida. Y sin quejarme. Bueno, aca me quejo.",
            "{player} se murio otra vez. Le dije donde quedaron sus cosas. Fue, las agarro y se volvio a morir. Arte.",
            "Si algun dia me voy, que se sepa: el mejor mate de este mundo lo cebaba yo.",
            "Me hizo talar medio bosque para una casita. La casita tiene un arbol adentro. No pregunte.",
            "Hoy {player} me dijo que soy su mejor amigo. Lo escribo para acordarme. No para nada mas. Bueno, un poquito me emocione.",
            "Reclamo formal: quiero un dia libre al mes y mate ilimitado. Firma: Rocco, empleado del mes. Unico empleado, pero igual.",
            "Pense en el tema del sueldo. Le voy a cobrar en abrazos. Me debe como trescientos.",
            "Hoy {player} se cayo a un pozo. Yo baje flotando con dignidad. Hay clases y clases.",
            "Mirando las estrellas pense: que lindo seria que alguien me trajera el mate a MI, para variar."};

    private static ItemStack diary(RoccoEntity rocco, ServerPlayer owner, int volume)
    {
        RandomSource random = rocco.getRandom();
        String player = owner.getGameProfile().getName();
        String name = RoccoCommonConfig.COMPANION_NAME.get();

        List<String> pages = new ArrayList<>();
        pages.add("DIARIO DE " + name.toUpperCase(Locale.ROOT) + "\n\nPRIVADO\n\nSi sos " + player
                + ": cerralo YA.\n\nSi no sos " + player + ": igual cerralo.");

        List<String> entries = new ArrayList<>(List.of(ENTRIES));
        int deaths = owner.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
        if (deaths >= 3)
        {
            entries.add("Cuenta oficial: {player} se murio " + deaths + " veces. Yo, cero. Saquen sus conclusiones.");
        }

        // Unos dias para atras, del mas viejo al de hoy.
        long today = rocco.level().getDayTime() / 24000L + 1L;
        long[] days = new long[4];
        long day = today;
        for (int i = days.length - 1; i >= 0; i--)
        {
            days[i] = Math.max(1L, day);
            day -= 1 + random.nextInt(4);
        }
        for (int i = 0; i < days.length && !entries.isEmpty(); i++)
        {
            String entry = entries.remove(random.nextInt(entries.size())).replace("{player}", player);
            boolean sameDay = i > 0 && days[i] == days[i - 1];
            pages.add("Dia " + days[i] + (sameDay ? ", mas tarde" : "") + "\n\n" + entry);
        }
        pages.add("PD: si leiste todo esto, me debes un mate. Y una disculpa.\n\n" + name);

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = book.getOrCreateTag();
        String title = "Diario de " + name + (volume > 1 ? " (tomo " + volume + ")" : "");
        tag.putString("title", title.length() > 32 ? title.substring(0, 32) : title);
        tag.putString("author", name);
        tag.putBoolean("resolved", true);
        ListTag list = new ListTag();
        for (String page : pages)
        {
            list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        }
        tag.put("pages", list);
        tag.putString(TAG_DIARY, rocco.getUUID().toString());
        return book;
    }

    // ---------- regalitos en broma ----------

    // Se lo tienen que pedir: el pedido y la cosa a pocas palabras ("dame un poco de marihuana",
    // "conseguime novia"). Asi "no tengo plata" o "vino un creeper" no son una broma.
    private static final String ASK = "dame|pasame|tenes|tienes|conseguime|consigueme|conseguir|traeme|quiero|queres"
            + "|necesito|vendeme|regalame|convidame|prestame|armame|arma|buscame|fumemos|fumar|tomemos"
            + "|me das|me pasas|me convidas|me prestas|me conseguis|me traes";

    private record Joke(String key, Pattern pattern) {}

    private static Pattern asked(String asks, String subjects)
    {
        return Pattern.compile("\\b(" + asks + ")\\b(\\s+\\w+){0,3}?\\s+(" + subjects + ")\\b");
    }

    private static final List<Joke> JOKES = List.of(
            new Joke("weed", asked(ASK, "marihuana|porro|porros|faso|fasos|weed|cannabis|churro|maria juana")),
            new Joke("money", asked(ASK, "plata|dinero|guita|money|billete|billetes|efectivo|prestamo|mangos")),
            new Joke("drink", asked(ASK, "birra|birras|cerveza|cervezas|fernet|vinito|alcohol|whisky|vodka|trago|chela")),
            new Joke("love", asked(ASK, "novia|novio|cita")));

    // Cuando se dio cada broma por ultima vez (por Rocco), para que no sea una canilla libre.
    private static final Map<String, Long> LAST_JOKE = new ConcurrentHashMap<>();
    private static final long JOKE_COOLDOWN = 20L * 60L * 10L;

    // Que broma pide el mensaje ("weed", "money"...), o null si no pide ninguna.
    @Nullable
    static String jokeFor(String message)
    {
        String text = Normalizer.normalize(message.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        for (Joke joke : JOKES)
        {
            if (joke.pattern().matcher(text).find())
            {
                return joke.key();
            }
        }
        return null;
    }

    // Si el mensaje pide una de las bromas, la hace y devuelve lo que dijo. null si no es una.
    @Nullable
    public static String joke(RoccoEntity rocco, ServerPlayer player, String message)
    {
        if (!RoccoCommonConfig.SURPRISES_ENABLED.get() || rocco.isTravelling())
        {
            return null;
        }
        String joke = jokeFor(message);
        if (joke == null)
        {
            return null;
        }

        String key = rocco.getUUID() + "|" + joke;
        long now = rocco.level().getGameTime();
        Long last = LAST_JOKE.get(key);
        if (last != null && now - last < JOKE_COOLDOWN)
        {
            rocco.setExpressionFromAi(RoccoExpression.SMUG);
            return OfflineRocco.line("joke_again");
        }
        LAST_JOKE.put(key, now);

        // En Chill (para los mas chicos) la marihuana es yerba, para el mate.
        boolean chill = PersonalityProfile.current().usesChillExamples();
        ItemStack gift;
        String line;
        switch (joke)
        {
            case "weed" ->
            {
                gift = chill
                        ? named(new ItemStack(Items.FERN), "Yerba de la buena", "Para el mate.", "Que pensabas?")
                        : named(new ItemStack(Items.FERN), "Marihuana de la buena", "Cien por ciento natural.", "(Es un helecho. No le digas a nadie.)");
                line = chill ? "joke_weed_chill" : "joke_weed";
            }
            case "money" ->
            {
                gift = named(new ItemStack(Items.PAPER), "Billete de Rocco", "Vale por un abrazo.", "Los aldeanos no lo aceptan.");
                line = "joke_money";
            }
            case "drink" ->
            {
                gift = named(new ItemStack(Items.HONEY_BOTTLE), "Birra de abeja", "Cero alcohol, pura miel.", "Salud!");
                line = "joke_drink";
            }
            default ->
            {
                gift = named(new ItemStack(Items.POPPY), "Flor para tu crush", "Dasela vos.", "Yo ya hice bastante.");
                line = "joke_love";
            }
        }
        rocco.setExpressionFromAi(RoccoExpression.SMUG);
        rocco.lookAt(player, 30.0F, 30.0F);
        give(rocco, player, gift);
        return OfflineRocco.line(line);
    }

    private RoccoSurprises() {}
}
