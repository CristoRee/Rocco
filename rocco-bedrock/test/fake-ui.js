// Imitacion de @minecraft/server-ui: cada formulario se contesta con ui.respond(form).
export const ui = {
  shown: [],
  respond: () => ({ canceled: true }),
};

class Form {
  constructor(kind) { this.kind = kind; this.buttons = []; this.fields = []; this._title = null; this._body = null; }
  title(t) { this._title = t; return this; }
  body(b) { this._body = b; return this; }
  button(text, icon) { this.buttons.push({ text, icon }); return this; }
  dropdown(label, options, opts) { this.fields.push({ type: "dropdown", label, options, def: opts?.defaultValueIndex ?? 0 }); return this; }
  toggle(label, opts) { this.fields.push({ type: "toggle", label, def: opts?.defaultValue ?? false }); return this; }
  textField(label, placeholder) { this.fields.push({ type: "text", label, placeholder }); return this; }
  show(player) {
    ui.shown.push(this);
    const answer = ui.respond(this, player) ?? { canceled: true };
    return Promise.resolve(answer);
  }
}

export class ActionFormData extends Form { constructor() { super("action"); } }
export class ModalFormData extends Form { constructor() { super("modal"); } }
export class MessageFormData extends Form { constructor() { super("message"); } }
