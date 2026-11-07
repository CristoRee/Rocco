# Rocco

**Un compañero de aventuras con IA para Minecraft 1.20.1 (Forge).**

Rocco es un milagro de la naturaleza: brota de la tierra al lado tuyo y te acompaña a todos lados. Charla con su propia personalidad (engreído, pero querible), te sigue, trabaja la huerta, craftea, cocina, encanta tus herramientas, se toma unos mates y, si querés, te habla en voz alta y te escucha por micrófono.

Funciona **sin ninguna cuenta ni clave**: trae cientos de frases propias, charla de lo que le cuentes, comenta lo que pasa y entiende órdenes en castellano. Con una clave de IA gratuita charla de verdad, y las órdenes simples las sigue resolviendo solo para no gastar la IA.

---

## Requisitos

| | Versión |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.10 o más nuevo |
| [GeckoLib](https://www.curseforge.com/minecraft/mc-mods/geckolib) | 4.7.4 o más nuevo |

El mod tiene que estar instalado en el cliente y, si jugás en un servidor, también en el servidor.

## Instalación

1. Instalá Forge 1.20.1.
2. Copiá `rocco-forge-1.20.1-<versión>.jar` y GeckoLib a la carpeta `mods`.
3. En el juego, usá el huevo generador de Rocco (pestaña de huevos generadores) y hacele clic derecho a la plantita para que brote.

## Qué sabe hacer

Se le habla por el chat (o por micrófono). Algunos ejemplos:

| Pedile... | Y Rocco... |
|---|---|
| "seguime", "quedate quieto", "sentate", "levantate", "vení" | Se mueve como le digas. Si está lejos o en otra zona, aparece al lado tuyo. |
| "crafteame un pico de piedra" | Lee la receta real, te dice exactamente qué falta, lo busca en los cofres si hace falta y lo fabrica. |
| "plantá papas", "cosechá", "cuidá la huerta" | Siembra con las semillas que tenga (si le faltan, las busca en los cofres), cosecha lo maduro, replanta y guarda la cosecha en su cofre. |
| "cociná las papas" | Usa el mejor horno que haya, pone el combustible justo y te da el resultado. |
| "encantame este pico" / "...con eficiencia 5" | Una vez por día: al azar como una mesa de encantamientos, o solo el encantamiento que pidas. |
| "tomate un mate" | Ceba y se toma unos mates. También lo hace solo cuando estás tranquilo en tu casa. |
| "pasame un mate" | Te ceba uno y te lo ofrece: clic derecho para agarrarlo, lo tomás en tres sorbos y se lo devolvés con clic derecho para que te cebe otro. Si no lo agarrás, se lo toma él. |
| "bailá", "aplaudí", "festejá" | Baila (dos bailes nuevos en bucle, de 8 segundos cada vuelta) con una de sus 3 cancioncitas (no suenan si hay un disco o su música puesta), aplaude o salta con confeti. Solo, festeja tus logros y baila cuando estás tranquilo. Si suena un disco en un tocadiscos cerca, va y baila hasta que se termina. |
| "poné esto: https://..." | Pone la música de un link y la canta y la baila al ritmo de la canción. Ver **Música** abajo. |
| "esta es mi casa" / "llevame a casa" | Recuerda lugares y te guía hasta ellos. |
| "dame una Roco Cola" | Te da una bien helada. Tomala y mirá lo que pasa. Ver **Roco Cola** abajo. |
| "traeme carbón", "guardá todo", "ordená los cofres" | Busca, guarda y ordena las cosas de tus cofres. Entiende "las herramientas", "la armadura", "las armas" o "el equipo", también juntos. Lo que tiene puesto no lo guarda nunca, y con "guardá todo" se queda con su mejor herramienta de cada tipo para trabajar. Busca en los cofres de toda la casa: hasta 32 bloques, y hasta 64 para traerte algo. Si le preguntás "¿dónde guardaste?", te dice en qué cofre. |
| "talá un árbol", "conseguime 10 de madera", "iluminá" | Junta materiales de la naturaleza y pone antorchas donde está oscuro. |
| "criá las vacas", "dales de comer a los animales", "esquilá las ovejas" | Les da de comer de a pares para que tengan crías, y esquila las ovejas (si no tiene tijeras, se las hace con 2 de hierro). |
| "quedate cuidando la casa", "cuidá los animales" | Se queda en tu casa mientras explorás. Ver **Cuidar la casa** abajo. |
| "andá a pescar", "comprale pan al aldeano" | Pesca con su caña (con boya y todo) y comercia con los aldeanos. |

Clic derecho sobre Rocco abre su tapa: un inventario de 27 lugares, que crece con las **mochilas**. En modo Chill, adentro de su cabeza hay un jardincito con flores en vez de la carne (se cambia en **Config > General > Adentro de su tapa**).

**Agachado + clic derecho** abre su menú: qué está haciendo, qué tan amigos son y si tiene la magia lista, con botones para lo de todos los días (seguir o dejar de seguir, vení, sentate, a dormir, subirse al lobo o bajarse, su inventario, guardar todo, ordenar cofres, cuidar la casa, quitarle la mochila, cebarte un mate, darte una Roco Cola, baile, callarse un rato), sus lugares guardados (llevame, guardar uno nuevo, olvidar) y accesos a la personalidad y la configuración. En multijugador, el menú es solo del dueño.

### Armadura, armas y pelea

- **Su equipo:** en el menú (agachado + clic derecho), **Equipo** abre sus lugares de casco, pechera, pantalones, botas, arma y escudo, con tu inventario abajo. También por chat: "ponete la armadura" (se pone lo mejor que tenga) o "sacate la armadura". Al estrenar algo se mira y posa.
- **Cada material, su estilo:** coronita con gemas (oro), cuerno de unicornio de cristal (diamante), orejitas, colita y botitas de gato (netherite), pluma de caballero (hierro), moño rosa (cota de malla), margarita (cuero, que se tiñe) y caparazón de tortuga. Las espadas, hachas, tridente, arco, ballesta y escudo tienen su propio modelo 3D.
- **Te defiende:** ataca lo que te lastima (o te tiene de objetivo), lo que vos atacás y lo que le pega a él. Si está sentado o tomando mate, lo deja y salta. También le podés pedir que ataque: "atacá a ese zombie", "matalo" (al que estás mirando) o "tirale flechas". A quién puede atacar se elige en su menú: mobs hostiles (de fábrica), pacíficos o jugadores. A vos nunca. Con arco o ballesta usa las flechas de su inventario y se queda a buena distancia; con escudo se cubre.

### Vida, tumba y el cofre de ender

- **10 corazones:** se cura solo medio corazón cada 1,5 segundos (a oscuras, más lento) y sus corazones se ven sobre su cabeza mientras le falte vida.
- **Come y toma pociones:** la comida le da saciedad y se cura más rápido un rato. En el menú de **Equipo** elegís si come solo cuando está lastimado, si toma pociones y si se come manzanas doradas en apuros. Por chat lo hace cuando se lo pidas, y también le podés dar de comer en la mano (clic derecho con la comida o la poción). Come a mordiscos y toma la poción con su animación, y pone cara de asco si es algo feo.
- **Abrirla en tu mano:** clic derecho con la mochila en la mano. Sus lugares son los mismos que le va a sumar a Rocco. Si te morís, se te cae con lo que lleva, como cualquier cosa de tu inventario.
- **Si Rocco se muere:** no pierde la mochila que tiene puesta. Lo que llevaba en el inventario cae al piso, salvo con el poder del cofre de ender.
- **Elegir cómo se ve:** en **Equipo**, el botón con el ícono de la mochila abre **Apariencia de la mochila**. Elegí una y se ve esa, con cualquier mochila que le pongas, pero la capacidad sigue siendo la de la real y al sacársela te da la real. Tocá la elegida de nuevo o **Su mochila real** para anularla. Cada apariencia se desbloquea la primera vez que Rocco se pone esa mochila en ese mundo.
- **Traerlo siempre:** **Mods > Rocco > Config > Traer a Rocco**, una tecla en **Controles > Rocco** (sin asignar de fábrica) o `/rocco llamar`.
- **El poder del cofre de ender:** dale un cofre de ender (o ponelo cerca para que se lo coma) y desde ahí no pierde sus cosas al morir.

### Mochilas

Rocco arranca con 27 lugares (3 filas). Cada mochila le suma filas, y las cosas que van en esos lugares viajan adentro de la mochila.

| Mochila | Suma | Total | Se fabrica con |
|---|---|---|---|
| Morralito de hoja | 1 fila | 36 | Semillita de Rocco, hojas, hilo y cuero |
| Mochila de viajero | 2 filas | 54 | Morralito, hierro, lana roja, cuero y cofre |
| Mochila de aventurero | 3 filas | 81 | Mochila de viajero, oro, perla de ender, cofres y farol |
| Mochila de expedición | 4 filas | 117 | Mochila de aventurero, diamantes, cofres de ender, lana cian y brújula |
| Mochila del héroe | 5 filas | 162 | Mochila de expedición, bloques de oro, netherita, caparazones de shulker y amatista |
| Mochila de la amistad | 6 filas (una página entera) | 216 | Mochila del héroe, Flor de la Amistad, fragmentos de eco, caparazones de shulker y estrella del Nether |

- **Ponérsela:** clic derecho a Rocco con la mochila en la mano. Se la prueba y se muestra.
- **Sacársela:** el botón **Quitar mochila** de su menú, o "sacate la mochila". Te la da con lo que lleva adentro.
- **Mejorarla:** en la mesa de crafteo, con la mochila anterior. Lo que lleva adentro se conserva.
- **Páginas:** cada página tiene hasta 6x9 lugares. Si hay más de una, arriba de la ventana aparecen los botones para pasar de una a otra, sin soltar lo que tengas en el cursor.
- **Si se muere:** la mochila la sigue teniendo puesta, aunque lo que llevaba cae al piso (salvo con el poder del cofre de ender).

**Los dos regalos:** cuando se hacen amigos, a Rocco le crece algo en la cabeza y te da la **Semillita**: con ella empezás. Y después de matar al Ender Dragon, al Wither y al Warden, dos días de juego juntos más tarde y en un momento tranquilo, te da la **Flor de la Amistad**, con la que se fabrica la última. Si no tenés lugar en el inventario, espera. Cada uno, una sola vez. Con la Semillita, las recetas aparecen en tu libro.

### A caballo y en tu lobo

- **Caballo, burro o mula ensillados:** si te sigue y te subís, se sube adelante tuyo. Va como un nene en el caballo: saluda al galope y se baja cuando te bajás. Se apaga en **Config > Movimiento**.
- **Tu lobo:** agachado + clic derecho con la mano vacía a tu lobo, con Rocco cerca, le das (o le sacás) permiso de subirse a ese lobo. Se sube si te alejás, o si le decís "subite al lobo". Se baja si el lobo se sienta, se moja, va a pelear, o si le decís "bajate".

### Cuidar la casa

Decile "quedate cuidando la casa" (o el botón **Cuidar la casa** de su menú) y se queda ahí mientras vos explorás. Cada medio minuto da una vuelta:

- **La huerta:** cosecha lo maduro, replanta y guarda la cosecha (se queda con un poco de trigo y zanahorias para los animales).
- **Los animales** (vacas, ovejas, cerdos, gallinas, conejos y cabras): esquila las ovejas, les da de comer de a pares para que tengan crías hasta **16 por especie**, y si hay más adultos que eso saca de a poco los que sobran (te avisa la primera vez). Junta lo que dejan en el piso (lana, huevos, plumas, carne, cuero) y lo guarda en los cofres.
- **Nunca toca** animales con nombre, atados, montados o domesticados.
- **Mientras no estás, la casa sigue cargada:** la huerta crece y los animales se mueven aunque estés lejos. Si lo atacan, se defiende y sigue.
- **Cuando volvés** te cuenta qué hizo ("coseché 34 cosas, nacieron 3 crías y esquilé 4 ovejas").
- **Para que deje:** "seguime", "vení" o el botón **Dejar de cuidar**. También se puede pedir solo la huerta ("cuidá la huerta") o solo los animales ("cuidá los animales").

El tope, sacar los que sobran, esquilar, usar el hierro de los cofres para las tijeras y dejar la casa cargada se cambian en **Config > Trabajo > Cuidar la casa**.

### Música

Pasale un link a Rocco y lo pone: suena desde él (camina con la música, como un parlante con patas) y mientras tanto canta con su micrófono y a veces baila, al ritmo de la canción. Las canciones lentas las canta suave, y el rap y el trap con actitud.

- **Qué links andan:** archivos de audio (MP3, OGG, M4A, FLAC, WAV) y radios por internet (MP3, AAC/AAC+, listas .m3u, .m3u8 y .pls): links que ya son audio. Si tenés la página de la radio, buscá ahí el link del audio o el de la lista. Los de YouTube, Spotify, SoundCloud, Twitch, Bandcamp y otras apps de videos o música no: sacarles el sonido va contra sus condiciones o entra por caminos que no ofrecen, y Rocco te pide otro link.
- **Lo que le pasás es cosa tuya:** Rocco no guarda ni comparte el audio, cada juego abre el link por su cuenta, como un navegador. Pasale solo música que tengas derecho a escuchar: tuya, con licencia libre (Creative Commons, dominio público) o radios que se puedan oír por internet.
- **Por chat:** "poné esto https://...", "pausá la música", "seguí con la música", "pará la música", "subí/bajá el volumen".
- **En multijugador** todos los que están cerca escuchan lo mismo al mismo tiempo, y el que llega tarde entra en el punto justo. Cada uno la escucha desde su juego.
- **El volumen** es el de **Discos** (Opciones > Música y sonidos), más el de **Config > Vida y rutina > Música**. Mientras Rocco habla, la música baja un poco.

### Roco Cola

Su gaseosa favorita. Pedile "tomate una Roco Cola" (si le decís "coca", te corrige) y se toma una entera, con tapita voladora, tres tragos y un "¡aaah!". Muy de vez en cuando se toma una solo.

Decile **"dame una Roco Cola"** y te da una a vos (una cada 5 minutos). Tiene 2 tragos: llena, a la mitad y botella vacía. Cada trago refresca (rapidez y prisa, 20 segundos) y a los 2,5 segundos sale un **eructo gigante**: un ventarrón con hojas que empuja a los mobs que tenés adelante, sin hacerles daño. Rocco tiene algo para decir al respecto.

### Regalos y sorpresas

- **Al volver:** si estuviste desconectado al menos una hora, Rocco te deja en su tapa lo que juntó mientras no estabas (si no le entra, te tira lo que sobra). Cuanto más tiempo, más y mejor: a la hora, un poco de carbón o hierro; al día, diamantes, manzanas doradas o perlas; a la semana (lo máximo), netherita, oro, diamantes, botellas de experiencia y más.
- **De vez en cuando:** te regala alguna cosita rara que encontró (un palito perfecto, una piedra con forma de corazón...) o se le cae su diario. Tenés unos segundos para agarrarlo antes de que lo levante él.
- **En broma:** algunas cosas que le pidas en broma tienen su regalito con nombre.

Se apagan en **Config > Vida y rutina > Regalos**.

### Te acompaña a todos lados

- **Volando:** si planeás con elytra (o volás en creativo), agarra sus hojitas, las convierte en alas y vuela a tu lado. Con cohete va a toda velocidad, en picada pliega las alas y al aterrizar se las vuelve a poner en la cabeza.
- **En el agua:** flota con su flotador de patito y nada a tu lado a tu ritmo, y cuando salís se trepa a la orilla; si buceás, se pone las antiparras y el snorkel y bucea con vos. Nunca se ahoga.
- **En bote:** no se sube al tuyo: tiene su propio barquito de calabaza y rema al costado.
- **Nunca se pierde:** si quedó lejos, en una zona descargada, del otro lado de un portal o cayó al vacío, aparece al lado tuyo. Si estás nadando lejos de la costa, cae al agua junto a vos.

## IA gratuita (opcional)

En **Mods > Rocco > Config > IA (cerebro)** se puede poner una clave gratuita de:

- **Groq**: https://console.groq.com/keys
- **Gemini**: https://aistudio.google.com/app/apikey
- **OpenRouter**: https://openrouter.ai/keys
- **Ollama** (IA local en tu PC, sin internet): https://ollama.com/download

Con más de una, si una se queda sin cupo, Rocco pasa sola a la siguiente. Las órdenes simples ("seguime", "guardá todo", "crafteame un pico") las resuelve sin preguntarle a la IA, para no gastar pedidos; se puede apagar con **Ahorrar IA en órdenes simples**. También se puede usar cualquier IA paga compatible con el formato de OpenAI (grupo *Proveedor propio*). `/rocco ia` muestra el estado de cada una.

## Voz y micrófono (opcional)

- **Voz:** con tu clave de Fish Audio y el ID de la voz en **Config > Voz**, Rocco dice todo en voz alta con esa voz (lo que inventa la IA y también sus frases de siempre). Sin Fish Audio, Rocco habla por escrito en el chat (sus sonidos y animaciones siguen igual). En un servidor, la clave y la voz son las del que lo hospeda. La voz sale de la boca de Rocco y respeta el volumen "Voz/Habla" del juego.
- **Micrófono (Deepgram):** activalo en **Config > Micrófono** y poné tu clave. La tecla para hablar se cambia en **Config > Teclas** (por defecto, la V). Se puede usar manteniendo la tecla o en modo de detección de voz.

## Configuración

**Mods > Rocco > Config** arranca con lo que hay que conectar, juntos y con una luz que dice si ya está listo: **IA (cerebro)**, **Voz** y **Micrófono**. Abajo están los demás temas: General, Personalidad, Charla, Movimiento, Vida y rutina, Trabajo, Magia e IA avanzada. Arriba hay un buscador que encuentra cualquier opción por nombre o por lo que hace. Pasando el mouse por una opción se ve qué hace y su valor por defecto, y el botón ↺ la vuelve a ese valor. Los cambios se guardan al salir.

## Comandos

| Comando | Qué hace |
|---|---|
| `/rocco estado` | Cómo está Rocco: claves, qué está haciendo, su poder de encantar. |
| `/rocco ia` | Estado de cada proveedor de IA, con links para conseguir claves. |
| `/rocco llamar` | Lo trae al lado tuyo esté donde esté (si está atado a un poste, lo desata). |
| `/rocco lugares` | Los lugares que recuerda, con distancia y dirección. |
| `/rocco despertar` | Hace brotar a un Rocco que todavía es una plantita. |
| `/rocco personalidad` | El modo de personalidad actual. |
| `/rocco personalidad chill \| sin_filtro \| personalizado` | Cambia el modo. |
| `/rocco personalidad reset` | Escribe la personalidad actual en tu archivo, para editarla. |
| `/rocco setup` | Abre la pantalla de bienvenida. |
| `/rocco tutorial` | Abre la guía rápida de Rocco. |

## Personalidad

Rocco es tu compañero de aventuras: te toma el pelo, se acuerda de lo que dijiste y de lo que prometiste, y te lo saca en cara cuando menos te lo esperás ("¿No que tu bebida favorita era el café?"). Cuando hay silencio un rato, comenta solo lo que estás haciendo, lo que llevás encima o lo que hay alrededor, y trata de no repetirse.

En **Mods > Rocco > Config > Personalidad** hay tres modos:

- **Chill** (el de fábrica): amigable y respetuoso, aunque un poco creído.
- **Sin filtro**: Rocco completo. Burlón, con malas palabras y humor negro.
- **Personalizado**: elegís cada rasgo (cómo te trata, el sarcasmo, el ego, los celos, cuánto habla, sus miedos, su historia...). También se le puede escribir una historia propia, notas extra o usar un archivo de personalidad entero (`config/rocco/personalidad.txt`).

El dialecto (latino neutro, rioplatense, España o automático) y el apodo con el que te llama valen en los tres modos. Los cambios se notan desde la próxima respuesta, sin reiniciar.

También imita voces de personajes y famosos si se lo pedís ("hablá como Vegeta"). Con Fish Audio configurado busca sola una voz pública de ese personaje en Fish Audio (se puede apagar), o usa la que vos le pongas en **Config > Voz > Imitaciones**. El mod no trae voces de personas reales: las voces públicas las sube la comunidad de Fish Audio, bajo sus términos.

## Guía rápida

La primera vez que nace un Rocco tuyo aparece una guía de 7 páginas con lo más importante, con Rocco moviéndose al lado. Se vuelve a ver con `/rocco tutorial`, con el botón **?** de su menú o con una tecla (**Controles > Rocco**, sin asignar de fábrica).

## Bienvenida

La primera vez que abrís el juego aparece una sola pantalla con lo esencial: la clave de IA (con un botón para probarla), la voz y la personalidad. Todo es opcional: Rocco funciona igual sin nada de eso. **Más opciones** lleva a toda la configuración, donde también están el micrófono y su botón de prueba. Se vuelve a abrir con `/rocco setup` o desde la config.

## Privacidad y servicios externos

Sin claves, Rocco no se conecta a ningún lado. Solo si vos ponés una clave, el mod se conecta a ese servicio:

| Servicio | Cuándo | Qué se manda |
|---|---|---|
| IA (Groq, Gemini, OpenRouter, Ollama o un proveedor propio) | Cuando le hablás y no es una orden simple | Lo que le dijiste, la charla reciente, su personalidad y un resumen de lo que pasa en el juego (vida, inventario, cofres cerca, hora). |
| Fish Audio | Cuando habla en voz alta, o busca la voz de un personaje para imitarlo | El texto que va a decir, o el nombre del personaje. |
| Deepgram | Mientras mantenés la tecla del micrófono (o con detección de voz) | El audio de tu micrófono. |
| El sitio del link de una canción | Cuando vos u otro jugador cerca le pasa un link a Rocco | Tu juego abre ese link para escucharlo, como un navegador: ese sitio ve tu conexión. Un link de otro jugador que apunte a tu computadora o a tu red local no se abre. Se apaga en **Config > Vida y rutina > Música > Escuchar su música**. |

Las claves quedan solo en tu archivo de configuración (o en variables de entorno, ver los comentarios de la config). El mod no junta datos ni manda nada a CristoRee ni a ningún otro lado.

Para la música, Rocco trae adentro lavaplayer y otras librerías de código abierto: ver `META-INF/THIRD-PARTY-NOTICES.txt` en el jar.

## Licencia

Rocco es una obra original de **CristoRee**. Se puede jugar, mostrar en videos y directos e incluir en modpacks gratuitos dando crédito; no se puede vender, resubir ni modificar sin permiso. Ver [LICENSE.md](LICENSE.md).

Minecraft es marca de Mojang AB. Rocco no está afiliado a Mojang ni a Microsoft.

---

### English

Rocco is an AI adventure companion for Minecraft 1.20.1 (Forge + GeckoLib). He follows you, farms (planting the seeds he has or finds in your chests), crafts using real recipes (and tells you exactly what's missing), cooks, enchants your gear once a day, drinks mate (and shares one with you if you ask), remembers places, breeds animals, looks after your home (crops and animals, even while you are far away), fishes, trades with villagers, sorts your chests, brings you gifts when you come back after being away, has a sneak + right-click menu, and can talk out loud (Fish Audio) and listen through your microphone (Deepgram). He wears armor (with a cute style per material) and 3D weapons, fights by your side (you choose who he may attack), wears up to 6 backpacks that grow his inventory (up to 216 slots, in pages), rides in front of you on your horse, gives you Roco Cola (mind the burp), plays music from links (audio files and internet radios) and sings and dances to it, heals with sunlight, comes back buried next to your respawn point if he dies, keeps his items forever if he eats an ender chest, and loves Roco Cola. He speaks Spanish and works without any API key thanks to built-in lines; a free key from Groq, Gemini or OpenRouter (or a local Ollama) makes him chat for real.

**External services:** without keys, Rocco connects to nothing (except the site of a music link someone gives him, which your game opens to play it). Only if you add a key does it talk to that service (your messages go to the AI provider you chose, the spoken text to Fish Audio, your microphone audio to Deepgram). Nothing is collected or sent anywhere else.
