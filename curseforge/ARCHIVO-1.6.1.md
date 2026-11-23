## Rocco 1.6.1 (Forge 1.20.1)

Changes since 1.6.0.

**New**
- **Your own voice and mic provider:** besides Fish Audio and Deepgram, the Voice and Microphone settings now have "Your own provider", for any service compatible with OpenAI's API. Voice: OpenAI, or a free one on your PC like Kokoro. Microphone: Groq (Whisper, with a free tier), OpenAI or a Whisper on your PC. You set the address, key, model and (for the voice) the voice name. With your own voice provider there are no voice emotions or impression voices.

**Changed**
- **Fights creepers:** he now attacks them by default, and creepers ignore him: if he hits them they don't turn to chase him, and they never explode because of him. With you, as always in Minecraft.
- **When the voice fails, you know why:** if Fish Audio rejects the key, runs out of credit or can't connect, Rocco says so in yellow with what to do (before it was a gray line that went unnoticed and he just went silent). He says it only once per problem, so the chat doesn't fill up, and `/rocco ia` now also shows the voice and the mic, with their last error.
- **Roco Cola in his free time:** now and then (3% of the times he picks something to do on his own, with at least 10 minutes between one and the next) he has a Roco Cola.
- **"Get me bread" with no amount** makes one batch of food (one bread, 3 wheat) instead of 16. Materials like wood or stone are still 16.
- **When he's missing something and you didn't ask for an amount,** he says what he needs ("I'm missing wheat") instead of an exact number. If you asked for an amount, he still gives the numbers.
- **The mate sounds** (pouring, sips, the straw and the "aaah") are louder.
- **Much faster when the AI gets stuck:** if the model is overloaded or takes over 10 seconds, he right away tries another fast model from the same AI (first the one Rocco ships with) and keeps using it for a while, instead of insisting on the slow one. The AI still answers, but in seconds instead of a minute. A retired model is replaced by the fast default one, and with more than one AI set up he also asks the next one after 3.5 seconds. The connection to the AI, the voice and the mic is also reused between requests.

**Fixed**
- **The welcome-back gift** sometimes didn't come: if the game closed abruptly (from the launcher, a freeze, turning off the PC) the time you left wasn't saved. Now it's also saved while you play.
- **A tiny boat floating in place** during tortas fritas (also in the asado, the truco and the broken bottle) when Rocco walked around.
- **Messages that weren't orders were taken as orders** and answered with canned lines instead of the AI. "Conseguí pan para nosotros" ("I got bread for us") was read as "get bread" and he answered "I'm missing 48 wheat to make bread". Now anything that tells him something ("yo...", "recién...", "te traje...", "para nosotros"), and the -ir verbs that read the same as an order or as the past ("conseguí", "abrí", "subí"), are answered by the AI when there is one.
- **One of the hearts over his head** could go missing.
- **In truco, drinking the mate** no longer covers the table with pink regeneration particles (you still get regeneration).
- **In the menus, the little Rocco** no longer shows his name and hearts above him.
- **If he already had an enemy targeted while doing something else** (like drinking mate), he sometimes didn't drop it to go fight.

---

## Rocco 1.6.1 (Forge 1.20.1, español)

Cambios desde la 1.6.0.

**Nuevo**
- **Voz y micrófono con proveedor propio:** además de Fish Audio y Deepgram, en la configuración de Voz y de Micrófono está "Proveedor propio", para cualquier servicio compatible con la API de OpenAI. Voz: OpenAI, o uno gratis en tu PC como Kokoro. Micrófono: Groq (Whisper, con capa gratis), OpenAI o un Whisper en tu PC. Se completa la dirección, la clave, el modelo y (en la voz) el nombre de la voz. Con voz propia no hay emociones en la voz ni voces de imitación.

**Cambiado**
- **Pelea con los creepers:** ahora los ataca de fábrica, y los creepers lo ignoran: si les pega no se dan vuelta a perseguirlo y nunca explotan por él. Con vos, como siempre en Minecraft.
- **Cuando la voz falla, se entiende por qué:** si Fish Audio rechaza la clave, se queda sin crédito o sin conexión, Rocco lo avisa en amarillo con qué hacer (antes era una línea gris que pasaba de largo y se quedaba mudo sin explicación). Avisa una sola vez por problema, para no llenar el chat, y `/rocco ia` ahora muestra también la voz y el micrófono, con su último error.
- **La Roco Cola en su rato libre:** cada tanto (el 3 % de las veces que elige algo para hacer solo, con 10 minutos como mínimo entre una y otra) se toma una Roco Cola.
- **"Conseguime pan" sin cantidad** hace una tanda de comida (un pan, 3 de trigo) en vez de 16. Los materiales como madera o piedra siguen siendo 16.
- **Cuando le falta algo y no le pediste una cantidad,** dice qué le falta ("me falta trigo") en vez de un número exacto. Si le pediste una cantidad, sigue diciendo los números.
- **Los sonidos del mate** (cebar, los sorbos, la bombilla y el "aaah") se escuchan más fuerte.
- **Contesta mucho más rápido cuando la IA se traba:** si el modelo está saturado o tarda más de 10 segundos, prueba enseguida otro modelo rápido de la misma IA (primero el que trae Rocco de fábrica) y sigue con ese un rato, en vez de insistir con el lento. Contesta igual la IA, pero en segundos y no en un minuto. Un modelo retirado se reemplaza por el rápido de fábrica, y con más de una IA configurada le pregunta también a la siguiente a los 3,5 segundos. Además, la conexión con la IA, la voz y el micrófono se reutiliza entre pedidos.

**Arreglado**
- **El regalo al volver** a veces no llegaba: si el juego se cerraba de golpe (desde el launcher, un cuelgue, apagar la PC) no quedaba anotado cuándo te fuiste. Ahora se anota también mientras jugás.
- **Un barquito en miniatura flotando quieto** durante las tortas fritas (también en el asado, el truco y la botella rota) cuando Rocco caminaba.
- **Mensajes que no eran órdenes se tomaban como órdenes** y se contestaban con frases predeterminadas en vez de la IA. "Conseguí pan para nosotros" se entendía como "conseguí pan" y respondía "Me faltan 48 de trigo para hacer pan". Ahora lo que le contás ("yo...", "recién...", "te traje...", "para nosotros") y los verbos en -ir que se escriben igual como orden o como pasado ("conseguí", "abrí", "subí") los contesta la IA, si hay una.
- **Uno de los corazones sobre su cabeza** podía no dibujarse.
- **En el truco, al tomar el mate** ya no te tapan la mesa las partículas rosas de la regeneración (la regeneración sigue igual).
- **En los menús, el Rocco chiquito** ya no muestra su nombre ni sus corazones encima.
- **Si ya tenía un enemigo marcado mientras hacía otra cosa** (por ejemplo, tomando mate), a veces no lo dejaba para ir a pelear.
