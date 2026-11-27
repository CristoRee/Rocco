## Rocco 1.6.1 (NeoForge 1.21.1 / 26.3)

**First release for NeoForge.** It's the same Rocco as on Forge 1.20.1, with everything in the description: chats with or without AI, follows you flying, swimming and by boat, looks after your home, works, cooks, enchants, drinks mate, plays truco, makes asado and tortas fritas, fights with his 3D armor and weapons, backpacks, music from links, voice and microphone.

- **1.21.1:** needs NeoForge 21.1.150 or newer and GeckoLib 4.9.3 or newer. A world from 1.20.1 keeps Rocco, his weapon, his inventory and what's inside his backpacks.
- **26.3:** needs NeoForge 26.3 and GeckoLib 5.5.7 or newer (the one for 26.3, not 4.x).

Includes everything from Rocco 1.6.1:
- **Microphone and voice on servers, with your own key:** on a server that doesn't have Rocco's microphone or voice set up, each player can use their own key in **Mods > Rocco > Config > My keys** (Deepgram for the mic, Fish Audio for the voice). Your PC turns your voice into text and the server only gets the text; Rocco's voice is made on your PC with your key. Keys never leave your PC. If the server has them, everything works as before. The owner can turn this off with `stt.allowClientTranscription` and `voice.allowClientVoice` in `config/rocco-common.toml`.
- **The right message when the mic can't be used:** in your own world it points to the Microphone settings; on a server it tells you to add your key in My keys or ask the owner, or that it's disabled there. Once every 20 seconds at most, in your language, and nothing is recorded. On a server, the config screen shows where the mic and the voice come from ("provided by the server" or "with your key").
- **Your own voice and mic provider:** besides Fish Audio and Deepgram, the Voice and Microphone settings have "Your own provider", for any service compatible with OpenAI's API (OpenAI, Kokoro on your PC, Groq's Whisper...).
- **Creepers ignore him:** he fights them, and they never chase him or explode because of him. With you, as always.
- **Much faster when the AI gets stuck:** if a model is overloaded or slow, he switches to a fast one right away.
- **When the voice fails, you know why:** a clear yellow message, once per problem, and `/rocco ia` shows the voice and mic status.

---

## Rocco 1.6.1 (NeoForge 1.21.1 / 26.3, español)

**Primera versión para NeoForge.** Es el mismo Rocco que en Forge 1.20.1, con todo lo de la descripción: charla con o sin IA, te sigue volando, nadando y en bote, cuida la casa, trabaja, cocina, encanta, toma mate, juega al truco, hace asado y tortas fritas, pelea con su armadura y sus armas 3D, mochilas, música desde links, voz y micrófono.

- **1.21.1:** necesita NeoForge 21.1.150 o más nuevo y GeckoLib 4.9.3 o más nuevo. Un mundo de 1.20.1 conserva a Rocco, su arma, su inventario y lo que llevan sus mochilas.
- **26.3:** necesita NeoForge 26.3 y GeckoLib 5.5.7 o más nuevo (el de 26.3, no el 4.x).

Trae todo lo de Rocco 1.6.1:
- **Micrófono y voz en servidores, con tu propia clave:** en un servidor que no tiene configurado el micrófono o la voz de Rocco, cada jugador puede usar su clave en **Mods > Rocco > Config > Mis claves** (Deepgram para el micrófono, Fish Audio para la voz). Tu PC pasa tu voz a texto y al servidor le llega solo el texto; la voz de Rocco se genera en tu PC con tu clave. Las claves nunca salen de tu PC. Si el servidor las tiene, todo sigue como antes. El dueño lo puede apagar con `stt.allowClientTranscription` y `voice.allowClientVoice` en `config/rocco-common.toml`.
- **El aviso correcto cuando no se puede usar el micrófono:** en tu mundo te manda a la config del Micrófono; en un servidor te dice que pongas tu clave en Mis claves o se lo pidas al dueño, o que ahí está desactivado. Como mucho uno cada 20 segundos, en tu idioma, y sin grabar nada. En un servidor, la pantalla de config muestra de dónde sale el micrófono y la voz ("lo da el servidor" o "con tu clave").
- **Voz y micrófono con proveedor propio:** además de Fish Audio y Deepgram, en Voz y en Micrófono está "Proveedor propio", para cualquier servicio compatible con la API de OpenAI (OpenAI, Kokoro en tu PC, el Whisper de Groq...).
- **Los creepers lo ignoran:** él los ataca, y ellos nunca lo persiguen ni explotan por él. Con vos, como siempre.
- **Mucho más rápido cuando la IA se traba:** si un modelo está saturado o lento, cambia enseguida a uno rápido.
- **Cuando la voz falla, se entiende por qué:** un aviso amarillo claro, una sola vez por problema, y `/rocco ia` muestra cómo están la voz y el micrófono.
