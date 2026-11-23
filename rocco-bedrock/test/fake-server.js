// Una imitacion chica de @minecraft/server para correr los scripts de Rocco en Node.
export const log = { messages: [], warns: [], events: [], sounds: [], particles: [], spawned: [] };

let tick = 0;
let nextRunId = 1;
const timeouts = new Map();
const intervals = new Map();

function signal() {
  const subs = [];
  return {
    subs,
    subscribe(fn, opts) { subs.push({ fn, opts }); return fn; },
    unsubscribe(fn) { const i = subs.findIndex((s) => s.fn === fn); if (i >= 0) subs.splice(i, 1); },
    fire(ev) { for (const s of [...subs]) s.fn(ev); },
  };
}

export const CommandPermissionLevel = { Any: 0, GameDirectors: 1, Admin: 2 };
export const CustomCommandParamType = { String: "String", Integer: "Integer" };
export const CustomCommandStatus = { Success: 0, Failure: 1 };

export class ItemStack {
  constructor(typeId, amount = 1) {
    this.typeId = typeId.includes(":") ? typeId : "minecraft:" + typeId;
    this.amount = amount;
    this.nameTag = undefined;
  }
  getComponent(id) {
    const foods = ["beef", "bread", "apple", "cooked_beef", "porkchop", "torta_frita", "asado_de_tira", "mate", "roco_cola"];
    if (id === "minecraft:food" && foods.some((f) => this.typeId.endsWith(f))) return { nutrition: 4 };
    return undefined;
  }
  clone() { const s = new ItemStack(this.typeId, this.amount); return s; }
}

export class Container {
  constructor(size) { this.size = size; this.slots = new Array(size).fill(undefined); }
  getItem(i) { const s = this.slots[i]; return s ? s.clone() : undefined; }
  setItem(i, s) { this.slots[i] = s ? s.clone() : undefined; }
  addItem(stack) {
    let left = stack.amount;
    for (let i = 0; i < this.size && left > 0; i++) {
      const s = this.slots[i];
      if (s && s.typeId === stack.typeId && s.amount < 64) { const add = Math.min(64 - s.amount, left); s.amount += add; left -= add; }
    }
    for (let i = 0; i < this.size && left > 0; i++) {
      if (!this.slots[i]) { const add = Math.min(64, left); this.slots[i] = new ItemStack(stack.typeId, add); left -= add; }
    }
    return left > 0 ? new ItemStack(stack.typeId, left) : undefined;
  }
}

let nextEntityId = 1;
export class Entity {
  constructor(typeId, dimension, location) {
    this.id = String(nextEntityId++);
    this.typeId = typeId;
    this.dimension = dimension;
    this.location = { ...location };
    this.isValid = true;
    this.nameTag = "";
    this.props = {};
    this.dyn = {};
    this.events = [];
    this.rotation = { x: 0, y: 0 };
    this.components = {};
    if (typeId === "rocco:rocco") {
      Object.assign(this.props, { "rocco:anim": 0, "rocco:seq": 0, "rocco:face": 0, "rocco:talk": false, "rocco:eating": false, "rocco:plant": true,
        "rocco:pack": 0, "rocco:garden": false, "rocco:mate": false, "rocco:bits": 0, "rocco:armor_head": 0, "rocco:armor_chest": 0,
        "rocco:armor_legs": 0, "rocco:armor_feet": 0, "rocco:weapon": 0, "rocco:weapon_mat": 0, "rocco:shield": false });
      this.components["minecraft:inventory"] = { container: new Container(54) };
      this.components["minecraft:health"] = health(40);
      this.components["minecraft:tameable"] = { tame: (p) => { this.owner = p; return true; } };
    }
    if (typeId === "rocco:truco_silla") {
      this.props["rocco:for_rocco"] = false;
      const riders = [];
      this.components["minecraft:rideable"] = { addRider: (e) => { riders.push(e); return true; }, ejectRiders: () => { riders.length = 0; }, riders };
    }
    if (typeId === "rocco:truco_mesa") this.props["rocco:size"] = 0;
    if (typeId === "rocco:truco_carta") this.props["rocco:card"] = 40;
  }
  getProperty(k) { if (!(k in this.props)) throw new Error("propiedad desconocida " + k + " en " + this.typeId); return this.props[k]; }
  setProperty(k, v) {
    if (!this.isValid) throw new Error("entidad invalida");
    if (!(k in this.props)) throw new Error("propiedad desconocida " + k + " en " + this.typeId);
    if (typeof this.props[k] !== typeof v) throw new Error("tipo incorrecto para " + k + ": " + typeof v);
    this.props[k] = v;
  }
  getDynamicProperty(k) { return this.dyn[k]; }
  setDynamicProperty(k, v) { if (!this.isValid) throw new Error("entidad invalida"); this.dyn[k] = v; }
  getComponent(id) { return this.components[id.includes(":") ? id : "minecraft:" + id]; }
  triggerEvent(e) { if (!this.isValid) throw new Error("entidad invalida"); this.events.push(e); log.events.push(this.typeId + " " + e); }
  teleport(loc, opts) { this.location = { ...loc }; if (opts?.dimension) this.dimension = opts.dimension; if (opts?.rotation) this.rotation = opts.rotation; }
  setRotation(r) { this.rotation = r; }
  getViewDirection() { return { x: 0, y: 0, z: 1 }; }
  remove() { this.isValid = false; this.dimension.entities = this.dimension.entities.filter((e) => e !== this); world.afterEvents.entityRemove.fire({ removedEntityId: this.id, typeId: this.typeId }); }
  applyDamage(n) { const h = this.components["minecraft:health"]; if (h) h.currentValue -= n; return true; }
  addEffect() {}
}

function health(max) {
  return { currentValue: max, effectiveMax: max, setCurrentValue(v) { this.currentValue = v; return true; }, resetToMaxValue() { this.currentValue = max; } };
}

export class Player extends Entity {
  constructor(name, dimension, location) {
    super("minecraft:player", dimension, location);
    this.name = name;
    this.isSneaking = false;
    this.isSleeping = false;
    this.inbox = [];
    this.components["minecraft:inventory"] = { container: new Container(36) };
    this.components["minecraft:health"] = health(20);
    const self = this;
    this.components["minecraft:equippable"] = {
      getEquipment(slot) { return slot === "Mainhand" ? self.getComponent("inventory").container.getItem(0) : undefined; },
      setEquipment(slot, s) { if (slot === "Mainhand") self.getComponent("inventory").container.setItem(0, s); return true; },
    };
    this.onScreenDisplay = { setActionBar: (t) => { this.actionbar = t; } };
  }
  sendMessage(m) { const t = typeof m === "string" ? m : JSON.stringify(m); this.inbox.push(t); log.messages.push(this.name + " <- " + t); }
}

class Block {
  constructor(air, solid) { this.isAir = air; this.isSolid = solid; }
}

class Dimension {
  constructor(id) { this.id = id; this.entities = []; }
  getEntities(q) { return this.entities.filter((e) => !q?.type || e.typeId === q.type); }
  spawnEntity(type, loc) {
    const e = new Entity(type, this, loc);
    this.entities.push(e);
    log.spawned.push(type);
    return e;
  }
  spawnItem(stack, loc) { log.events.push("item " + stack.typeId + " x" + stack.amount); return new Entity("minecraft:item", this, loc); }
  spawnParticle(id) { log.particles.push(id); }
  playSound(id) { log.sounds.push(id); }
  getBlock(loc) { return loc.y < 0 ? new Block(false, true) : new Block(true, false); }
}

const dims = { "minecraft:overworld": new Dimension("minecraft:overworld"), "minecraft:nether": new Dimension("minecraft:nether"), "minecraft:the_end": new Dimension("minecraft:the_end") };
const worldDyn = {};

export const world = {
  players: [],
  getDimension: (id) => dims[id],
  getPlayers() { return this.players.filter((p) => p.isValid); },
  getDynamicProperty: (k) => worldDyn[k],
  setDynamicProperty: (k, v) => { worldDyn[k] = v; },
  getTimeOfDay: () => 6000,
  afterEvents: {
    playerSpawn: signal(), playerInteractWithEntity: signal(), entityHitEntity: signal(), entityHurt: signal(),
    dataDrivenEntityTrigger: signal(), entityRemove: signal(), itemCompleteUse: signal(), playerLeave: signal(),
  },
  beforeEvents: { playerLeave: signal(), chatSend: signal() },
};

export const system = {
  get currentTick() { return tick; },
  run(fn) { return this.runTimeout(fn, 0); },
  runTimeout(fn, delay = 0) { const id = nextRunId++; timeouts.set(id, { at: tick + Math.max(0, delay), fn }); return id; },
  runInterval(fn, every = 1) { const id = nextRunId++; intervals.set(id, { every: Math.max(1, every), fn, next: tick + every }); return id; },
  clearRun(id) { timeouts.delete(id); intervals.delete(id); },
  sendScriptEvent(id, message) { log.events.push("scriptevent " + id); system.afterEvents.scriptEventReceive.fire({ id, message }); },
  beforeEvents: { startup: signal() },
  afterEvents: { scriptEventReceive: signal() },
};

export const commands = {};

export function startup() {
  system.beforeEvents.startup.fire({ customCommandRegistry: { registerCommand(def, fn) { commands[def.name] = fn; } } });
}

export function advance(n = 1, onError = (e) => { throw e; }) {
  for (let i = 0; i < n; i++) {
    tick++;
    for (const [id, t] of [...timeouts]) {
      if (t.at <= tick) { timeouts.delete(id); try { t.fn(); } catch (e) { onError(e); } }
    }
    for (const iv of intervals.values()) {
      if (iv.next <= tick) { iv.next = tick + iv.every; try { iv.fn(); } catch (e) { onError(e); } }
    }
  }
}

export function addPlayer(name) {
  const p = new Player(name, dims["minecraft:overworld"], { x: 0.5, y: 0, z: 0.5 });
  world.players.push(p);
  return p;
}
