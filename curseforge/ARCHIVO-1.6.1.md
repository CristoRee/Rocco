## Rocco 1.6.1

**New**
- **Your own voice and mic provider:** besides Fish Audio and Deepgram, the Voice and Microphone settings now have "Your own provider", for any service compatible with OpenAI's API. Voice: OpenAI, or a free one on your PC like Kokoro. Microphone: Groq (Whisper, with a free tier), OpenAI or a Whisper on your PC. You set the address, key, model and (for the voice) the voice name. With your own voice provider there are no voice emotions or impression voices.

**Changed**
- **When the voice fails, you know why:** if Fish Audio rejects the key, runs out of credit or can't connect, Rocco says so in yellow with what to do (before it was a gray line that went unnoticed and he just went silent). He says it only once per problem, so the chat doesn't fill up, and `/rocco ia` now also shows the voice and the mic, with their last error.
- **Roco Cola in his free time:** now and then (3% of the times he picks something to do on his own, with at least 10 minutes between one and the next) he has a Roco Cola.
- **"Get me bread" with no amount** makes one batch of food (one bread, 3 wheat) instead of 16. Materials like wood or stone are still 16.
- **When he's missing something and you didn't ask for an amount,** he says what he needs ("I'm missing wheat") instead of an exact number. If you asked for an amount, he still gives the numbers.
- **The mate sounds** (pouring, sips, the straw and the "aaah") are louder.
- **Slightly faster replies:** the connection to the AI, the voice and the mic is reused between requests instead of being opened again each time.

**Fixed**
- **A tiny boat floating in place** during tortas fritas (also in the asado, the truco and the broken bottle) when Rocco walked around.
- **Messages that weren't orders were taken as orders** and answered with canned lines instead of the AI. "Conseguí pan para nosotros" ("I got bread for us") was read as "get bread" and he answered "I'm missing 48 wheat to make bread". Now anything that tells him something ("yo...", "recién...", "te traje...", "para nosotros"), and the -ir verbs that read the same as an order or as the past ("conseguí", "abrí", "subí"), are answered by the AI when there is one.
- **1.21.1:** Rocco's health over his head wasn't showing, and neither was the fishing bobber of his rod (both were drawn facing backwards).
- **One of the hearts over his head** could go missing.

---

## Rocco 1.6.1 (español)

**Nuevo**
- **Voz y micrófono con proveedor propio:** además de Fish Audio y Deepgram, en la configuración de Voz y de Micrófono está "Proveedor propio", para cualquier servicio compatible con la API de OpenAI. Voz: OpenAI, o uno gratis en tu PC como Kokoro. Micrófono: Groq (Whisper, con capa gratis), OpenAI o un Whisper en tu PC. Se completa la dirección, la clave, el modelo y (en la voz) el nombre de la voz. Con voz propia no hay emociones en la voz ni voces de imitación.

**Cambiado**
- **Cuando la voz falla, se entiende por qué:** si Fish Audio rechaza la clave, se queda sin crédito o sin conexión, Rocco lo avisa en amarillo con qué hacer (antes era una línea gris que pasaba de largo y se quedaba mudo sin explicación). Avisa una sola vez por problema, para no llenar el chat, y `/rocco ia` ahora muestra también la voz y el micrófono, con su último error.
- **La Roco Cola en su rato libre:** cada tanto (el 3 % de las veces que elige algo para hacer solo, con 10 minutos como mínimo entre una y otra) se toma una Roco Cola.
- **"Conseguime pan" sin cantidad** hace una tanda de comida (un pan, 3 de trigo) en vez de 16. Los materiales como madera o piedra siguen siendo 16.
- **Cuando le falta algo y no le pediste una cantidad,** dice qué le falta ("me falta trigo") en vez de un número exacto. Si le pediste una cantidad, sigue diciendo los números.
- **Los sonidos del mate** (cebar, los sorbos, la bombilla y el "aaah") se escuchan más fuerte.
- **Respuestas un poco más rápidas:** la conexión con la IA, la voz y el micrófono se reutiliza entre pedidos en vez de abrirse de nuevo cada vez.

**Arreglado**
- **Un barquito en miniatura flotando quieto** durante las tortas fritas (también en el asado, el truco y la botella rota) cuando Rocco caminaba.
- **Mensajes que no eran órdenes se tomaban como órdenes** y se contestaban con frases predeterminadas en vez de la IA. "Conseguí pan para nosotros" se entendía como "conseguí pan" y respondía "Me faltan 48 de trigo para hacer pan". Ahora lo que le contás ("yo...", "recién...", "te traje...", "para nosotros") y los verbos en -ir que se escriben igual como orden o como pasado ("conseguí", "abrí", "subí") los contesta la IA, si hay una.
- **1.21.1:** no se veía la vida de Rocco sobre su cabeza, ni la boya de su caña de pescar (las dos se dibujaban mirando para atrás).
- **Uno de los corazones sobre su cabeza** podía no dibujarse.
