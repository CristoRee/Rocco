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
| "bailá", "aplaudí", "festejá" | Baila tarareando, aplaude o salta con confeti. Solo, festeja tus logros y baila cuando estás tranquilo. |
| "esta es mi casa" / "llevame a casa" | Recuerda lugares y te guía hasta ellos. |
| "traeme carbón", "guardá todo", "ordená los cofres" | Busca, guarda y ordena las cosas de tus cofres. |
| "talá un árbol", "conseguime 10 de madera", "iluminá" | Junta materiales de la naturaleza y pone antorchas donde está oscuro. |
| "criá las vacas", "andá a pescar", "comprale pan al aldeano" | Cría animales, pesca con su caña (con boya y todo) y comercia con los aldeanos. |

Clic derecho sobre Rocco abre su tapa: un inventario de 36 lugares. En modo Chill, adentro de su cabeza hay un jardincito con flores en vez de la carne (se cambia en **Config > General > Adentro de su tapa**).

**Agachado + clic derecho** abre su menú: qué está haciendo, qué tan amigos son y si tiene la magia lista, con botones para lo de todos los días (seguir o dejar de seguir, vení, sentate, a dormir, su inventario, guardar todo, ordenar cofres, cuidar la huerta, cebarte un mate, baile, callarse un rato), sus lugares guardados (llevame, guardar uno nuevo, olvidar) y accesos a la personalidad y la configuración. En multijugador, el menú es solo del dueño.

### Regalos y sorpresas

- **Al volver:** si estuviste desconectado al menos una hora, Rocco te deja en su tapa lo que juntó mientras no estabas (si no le entra, te tira lo que sobra). Cuanto más tiempo, más y mejor: a la hora, un poco de carbón o hierro; al día, diamantes, manzanas doradas o perlas; a la semana (lo máximo), netherita, oro, diamantes, botellas de experiencia y más.
- **De vez en cuando:** te regala alguna cosita rara que encontró (un palito perfecto, una piedra con forma de corazón...) o se le cae su diario. Tenés unos segundos para agarrarlo antes de que lo levante él.
- **En broma:** algunas cosas que le pidas en broma tienen su regalito con nombre.

Se apagan en **Config > Vida y rutina > Regalos**.

### Te acompaña a todos lados

- **Volando:** si planeás con elytra (o volás en creativo), agarra sus hojitas, las convierte en alas y vuela a tu lado. Con cohete va a toda velocidad, en picada pliega las alas y al aterrizar se las vuelve a poner en la cabeza.
- **En el agua:** flota con su flotador de patito y nada a tu lado a tu ritmo; si buceás, se pone las antiparras y el snorkel y bucea con vos. Nunca se ahoga.
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

- **Voz:** con tu clave de Fish Audio y el ID de la voz en **Config > Voz**, Rocco dice todo en voz alta con esa voz (lo que inventa la IA y también sus frases de siempre). Sin Fish Audio, las frases de siempre que tengan su audio grabado en el mod suenan igual, sin clave; y si Fish Audio falla (sin crédito o sin conexión), mientras tanto usa esas. La voz sale de la boca de Rocco y respeta el volumen "Voz/Habla" del juego.
- **Micrófono (Deepgram):** activalo en **Config > Micrófono** y poné tu clave. La tecla para hablar se cambia en **Config > Teclas** (por defecto, la V). Se puede usar manteniendo la tecla o en modo de detección de voz.

## Configuración

**Mods > Rocco > Config** arranca con lo que hay que conectar, juntos y con una luz que dice si ya está listo: **IA (cerebro)**, **Voz** y **Micrófono**. Abajo están los demás temas: General, Personalidad, Charla, Movimiento, Vida y rutina, Trabajo, Magia e IA avanzada. Arriba hay un buscador que encuentra cualquier opción por nombre o por lo que hace. Pasando el mouse por una opción se ve qué hace y su valor por defecto, y el botón ↺ la vuelve a ese valor. Los cambios se guardan al salir.

## Comandos

| Comando | Qué hace |
|---|---|
| `/rocco estado` | Cómo está Rocco: claves, qué está haciendo, su poder de encantar. |
| `/rocco ia` | Estado de cada proveedor de IA, con links para conseguir claves. |
| `/rocco llamar` | Lo trae al lado tuyo esté donde esté. |
| `/rocco lugares` | Los lugares que recuerda, con distancia y dirección. |
| `/rocco despertar` | Hace brotar a un Rocco que todavía es una plantita. |
| `/rocco personalidad` | El modo de personalidad actual. |
| `/rocco personalidad chill \| sin_filtro \| personalizado` | Cambia el modo. |
| `/rocco personalidad reset` | Escribe la personalidad actual en tu archivo, para editarla. |
| `/rocco setup` | Abre la pantalla de bienvenida. |

## Personalidad

Rocco es tu compañero de aventuras: te toma el pelo, se acuerda de lo que dijiste y de lo que prometiste, y te lo saca en cara cuando menos te lo esperás ("¿No que tu bebida favorita era el café?"). Cuando hay silencio un rato, comenta solo lo que estás haciendo, lo que llevás encima o lo que hay alrededor, y trata de no repetirse.

En **Mods > Rocco > Config > Personalidad** hay tres modos:

- **Chill** (el de fábrica): amigable y respetuoso, aunque un poco creído.
- **Sin filtro**: Rocco completo. Burlón, con malas palabras y humor negro.
- **Personalizado**: elegís cada rasgo (cómo te trata, el sarcasmo, el ego, los celos, cuánto habla, sus miedos, su historia...). También se le puede escribir una historia propia, notas extra o usar un archivo de personalidad entero (`config/rocco/personalidad.txt`).

El dialecto (latino neutro, rioplatense, España o automático) y el apodo con el que te llama valen en los tres modos. Los cambios se notan desde la próxima respuesta, sin reiniciar.

También imita voces de personajes y famosos si se lo pedís ("hablá como Vegeta"). La voz real de cada personaje la elegís vos en **Config > Voz > Imitaciones**: el mod no trae voces de personas reales.

## Bienvenida

La primera vez que abrís el juego aparece una pantalla para dejar a Rocco listo: la clave de IA (con un botón para probarla), la voz, el micrófono, la tecla para hablarle y la personalidad. Se puede volver a abrir con `/rocco setup` o desde la config.

## Privacidad

Las claves quedan solo en tu archivo de configuración (o en variables de entorno, ver los comentarios de la config). Lo que le decís a Rocco se manda únicamente al proveedor de IA, voz o micrófono que vos configures.

## Licencia

Rocco es una obra original de **CristoRee**. Se puede jugar, mostrar en videos y directos e incluir en modpacks gratuitos dando crédito; no se puede vender, resubir ni modificar sin permiso. Ver [LICENSE.md](LICENSE.md).

Minecraft es marca de Mojang AB. Rocco no está afiliado a Mojang ni a Microsoft.

---

### English

Rocco is an AI adventure companion for Minecraft 1.20.1 (Forge + GeckoLib). He follows you, farms (planting the seeds he has or finds in your chests), crafts using real recipes (and tells you exactly what's missing), cooks, enchants your gear once a day, drinks mate (and shares one with you if you ask), remembers places, breeds animals, fishes, trades with villagers, sorts your chests, brings you gifts when you come back after being away, has a sneak + right-click menu, and can talk out loud (Fish Audio) and listen through your microphone (Deepgram). He speaks Spanish and works without any API key thanks to built-in lines; a free key from Groq, Gemini or OpenRouter (or a local Ollama) makes him chat for real.
