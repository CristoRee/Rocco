// Un proveedor de IA de mentira con la API de OpenAI: anota lo que le piden y contesta fijo.
const http = require("http");
const fs = require("fs");
http.createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    fs.appendFileSync(__dirname + "/mockai.log", req.method + " " + req.url + " " + body.length + " bytes\n");
    if (req.url.endsWith("/models")) {
      res.writeHead(200, { "Content-Type": "application/json" });
      return res.end(JSON.stringify({ data: [{ id: "prueba-70b-versatile" }] }));
    }
    let model = "";
    try { const j = JSON.parse(body); model = j.model; fs.appendFileSync(__dirname + "/mockai.log", "modelo=" + model + " sistema=" + j.messages[0].content.length + " usuario=" + j.messages[1].content + "\n"); } catch {}
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(JSON.stringify({ choices: [{ message: { content: "@smug Todo de diez, obvio. Mirá cómo bailo.\n#accion: bailar" } }] }));
  });
}).listen(18085, "127.0.0.1", () => console.log("mock listo"));
