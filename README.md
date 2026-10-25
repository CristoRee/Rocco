# Rocco

**Un compañero de aventuras con IA para Minecraft 1.20.1 (Forge).**

Rocco es un milagro de la naturaleza: brota de la tierra al lado tuyo y te acompaña a todos lados. Charla con su propia personalidad (engreído, pero querible), te sigue, trabaja la huerta, craftea, cocina, encanta tus herramientas, se toma unos mates y, si querés, te habla en voz alta y te escucha por micrófono.

Funciona **sin ninguna cuenta ni clave**: trae frases propias y entiende órdenes en castellano. Con una clave de IA gratuita charla de verdad.

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
| "plantá papas", "cosechá", "cuidá la huerta" | Siembra sin gastar semillas, cosecha lo maduro, replanta y guarda la cosecha en su cofre. |
| "cociná las papas" | Usa el mejor horno que haya, pone el combustible justo y te da el resultado. |
| "encantame este pico" / "...con eficiencia 5" | Una vez por día: al azar como una mesa de encantamientos, o solo el encantamiento que pidas. |
| "tomate un mate" | Ceba y se toma unos mates. También lo hace solo cuando estás tranquilo en tu casa. |
| "esta es mi casa" / "llevame a casa" | Recuerda lugares y te guía hasta ellos. |
| "traeme carbón", "guardá todo", "ordená los cofres" | Busca, guarda y ordena las cosas de tus cofres. |
| "talá un árbol", "conseguime 10 de madera", "iluminá" | Junta materiales de la naturaleza y pone antorchas donde está oscuro. |
| "criá las vacas", "andá a pescar", "comprale pan al aldeano" | Cría animales, pesca y comercia con los aldeanos. |

Clic derecho sobre Rocco abre su tapa: un inventario de 36 lugares.

## IA gratuita (opcional)

En **Mods > Rocco > Config > ai / providers** se puede poner una clave gratuita de:

- **Groq**: https://console.groq.com/keys
- **Gemini**: https://aistudio.google.com/app/apikey
- **OpenRouter**: https://openrouter.ai/keys
- **Ollama** (IA local en tu PC, sin internet): https://ollama.com/download

Con más de una, si una se queda sin cupo, Rocco pasa sola a la siguiente. También se puede usar cualquier IA paga compatible con el formato de OpenAI (sección *custom*). `/rocco ia` muestra el estado de cada una.

## Voz y micrófono (opcional)

- **Voz (Fish Audio):** poné tu clave y el ID de la voz en **Config > voice**. La voz sale de la boca de Rocco y respeta el volumen "Voz/Habla" del juego.
- **Micrófono (Deepgram):** activá **Config > stt** y poné tu clave. La tecla para hablar se cambia en **Opciones > Controles > Rocco** (por defecto, la V). Se puede usar manteniendo la tecla o en modo de detección de voz.

## Comandos

| Comando | Qué hace |
|---|---|
| `/rocco estado` | Cómo está Rocco: claves, qué está haciendo, su poder de encantar. |
| `/rocco ia` | Estado de cada proveedor de IA, con links para conseguir claves. |
| `/rocco llamar` | Lo trae al lado tuyo esté donde esté. |
| `/rocco lugares` | Los lugares que recuerda, con distancia y dirección. |
| `/rocco despertar` | Hace brotar a un Rocco que todavía es una plantita. |
| `/rocco personalidad [reset]` | Dónde está el archivo de personalidad, o lo vuelve al original. |

## Personalidad

La personalidad está en `config/rocco/personalidad.txt`. Se puede editar con el juego abierto: Rocco la vuelve a leer sola.

## Privacidad

Las claves quedan solo en tu archivo de configuración (o en variables de entorno, ver los comentarios de la config). Lo que le decís a Rocco se manda únicamente al proveedor de IA, voz o micrófono que vos configures.

## Licencia

Rocco es una obra original de **CristoRee**. Se puede jugar, mostrar en videos y directos e incluir en modpacks gratuitos dando crédito; no se puede vender, resubir ni modificar sin permiso. Ver [LICENSE.md](LICENSE.md).

Minecraft es marca de Mojang AB. Rocco no está afiliado a Mojang ni a Microsoft.

---

### English

Rocco is an AI adventure companion for Minecraft 1.20.1 (Forge + GeckoLib). He follows you, farms without seeds, crafts using real recipes (and tells you exactly what's missing), cooks, enchants your gear once a day, drinks mate, remembers places, breeds animals, fishes, trades with villagers, sorts your chests, and can talk out loud (Fish Audio) and listen through your microphone (Deepgram). He speaks Spanish and works without any API key thanks to built-in lines; a free key from Groq, Gemini or OpenRouter (or a local Ollama) makes him chat for real.
