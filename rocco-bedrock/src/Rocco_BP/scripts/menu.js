// El menu de Rocco (tocarlo): lo mismo que el menu de Java, con formularios de Bedrock.
import { ItemStack } from "@minecraft/server";
import { ActionFormData, ModalFormData } from "@minecraft/server-ui";
import { rt, data, setData, json, setJson, say, displayName, ownerId, play, sound } from "./rocco.js";
import { line } from "./offline.js";
import { tr, safe } from "./util.js";
import { hear, doVerb, aiAvailable, teleportToOwner } from "./chat.js";
import { inventoryOf, listItems, heldItem, setHeldItem, giveToPlayer, addItem } from "./items.js";
import { equipFrom, unequipAll, gearSlot, gear } from "./combat.js";
import { busy } from "./activities.js";

const CAPACITY = [27, 36, 45, 54, 54, 54, 54];

function title(rocco) {
  return { rawtext: [{ text: displayName(rocco) }] };
}

function statusBody(rocco) {
  const health = rocco.getComponent("minecraft:health");
  const hp = health ? Math.ceil(health.currentValue) + "/" + Math.ceil(health.effectiveMax) : "?";
  const follow = data(rocco, "follow", true);
  const mode = data(rocco, "mode", "chill");
  return {
    rawtext: [
      { text: "§a♥ " + hp + "§r\n" },
      { translate: follow ? "rocco.menu.status.following" : "rocco.menu.status.staying" },
      { text: "\n" },
      { translate: "rocco.menu.status.mode" },
      { text: " " },
      { translate: "rocco.menu.mode." + mode },
      { text: "\n" },
      { translate: aiAvailable() ? "rocco.menu.status.ai_on" : "rocco.menu.status.ai_off" },
    ],
  };
}

export function openMenu(rocco, player) {
  if (ownerId(rocco) && ownerId(rocco) !== player.id) {
    player.sendMessage({ rawtext: [{ text: "<" + displayName(rocco) + "> " + line("menu_not_yours") }] });
    return;
  }
  const r = rt(rocco);
  const follow = data(rocco, "follow", true);
  const hunting = data(rocco, "hunt", true);
  const items = [
    ["talk", "rocco.menu.talk", "textures/items/book_writable"],
    [follow ? "stay" : "follow", follow ? "rocco.menu.stay" : "rocco.menu.follow", "textures/items/lead"],
    ["come", "rocco.menu.come", "textures/items/ender_pearl"],
    ["inventory", "rocco.menu.inventory", "textures/blocks/chest_front"],
    ["gear", "rocco.menu.gear", "textures/items/iron_chestplate"],
    ["mate", "rocco.menu.mate", "textures/items/rocco/mate"],
    ["truco", "rocco.menu.truco", "textures/entity/rocco/cartas/espada_1"],
    ["asado", "rocco.menu.asado", "textures/items/rocco/asado_de_tira"],
    ["tortas", "rocco.menu.tortas", "textures/items/rocco/torta_frita"],
    ["cola", "rocco.menu.cola", "textures/items/rocco/roco_cola"],
    ["dance", "rocco.menu.dance", "textures/items/record_cat"],
    [r.pose === "stand" ? "sit" : "stand", r.pose === "stand" ? "rocco.menu.sit" : "rocco.menu.stand", "textures/items/saddle"],
    ["hunt", hunting ? "rocco.menu.hunt_on" : "rocco.menu.hunt_off", "textures/items/iron_sword"],
    ["quiet", "rocco.menu.quiet", "textures/items/name_tag"],
    ["places", "rocco.menu.places", "textures/items/map_filled"],
    ["personality", "rocco.menu.personality", "textures/items/rocco/flor_amistad"],
    ["help", "rocco.menu.help", "textures/items/book_normal"],
  ];
  const form = new ActionFormData().title(title(rocco)).body(statusBody(rocco));
  for (const [, key, icon] of items) form.button({ translate: key }, icon);
  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined || !rocco.isValid) return;
    choose(rocco, player, items[res.selection][0]);
  }).catch((e) => console.warn("[Rocco] menu: " + e));
}

function choose(rocco, player, id) {
  switch (id) {
    case "talk":
      return talkForm(rocco, player);
    case "stay":
      say(rocco, line("wait"), "bored");
      return doVerb(rocco, player, "esperar");
    case "follow":
      say(rocco, line("follow"), "happy");
      return doVerb(rocco, player, "seguir");
    case "come":
      return teleportToOwner(rocco);
    case "inventory":
      return inventoryForm(rocco, player);
    case "gear":
      return gearForm(rocco, player);
    case "mate":
      return doVerb(rocco, player, "cebar_mate");
    case "truco":
      return doVerb(rocco, player, "jugar_truco");
    case "asado":
      return doVerb(rocco, player, "asado");
    case "tortas":
      return doVerb(rocco, player, "tortas_fritas");
    case "cola":
      return doVerb(rocco, player, "dar_cola");
    case "dance":
      say(rocco, line("dance_ack"), "happy");
      return doVerb(rocco, player, "bailar");
    case "sit":
      return doVerb(rocco, player, "sentarse");
    case "stand":
      return doVerb(rocco, player, "levantarse");
    case "hunt": {
      const on = !data(rocco, "hunt", true);
      setData(rocco, "hunt", on);
      safe("cazar", () => rocco.triggerEvent(on ? "rocco:hunt_on" : "rocco:hunt_off"));
      player.sendMessage({ translate: on ? "rocco.menu.hunt_on" : "rocco.menu.hunt_off" });
      return;
    }
    case "quiet": {
      const quiet = Date.now() < data(rocco, "quiet_until", 0);
      setData(rocco, "quiet_until", quiet ? 0 : Date.now() + 20 * 60 * 1000);
      setData(rocco, "chatty_until", quiet ? Date.now() + 30 * 60 * 1000 : 0);
      say(rocco, line(quiet ? "chatty" : "shush"), quiet ? "happy" : "bored");
      return;
    }
    case "places":
      return placesForm(rocco, player);
    case "personality":
      return personalityForm(rocco, player);
    case "help":
      return helpForm(rocco, player);
  }
}

function talkForm(rocco, player) {
  new ModalFormData()
    .title(title(rocco))
    .textField({ translate: "rocco.menu.talk.label" }, { translate: "rocco.menu.talk.placeholder" })
    .show(player)
    .then((res) => {
      if (res.canceled || !res.formValues) return;
      const text = String(res.formValues[0] ?? "").trim();
      if (text) hear(rocco, player, text);
    });
}

// ---------------------------------------------------------------- inventario

export function capacity(rocco) {
  return CAPACITY[data(rocco, "pack", 0)] ?? 27;
}

function usedSlots(container) {
  return listItems(container).length;
}

/** Le da a Rocco lo que tenes en la mano (si le entra). */
export function giveHeld(rocco, player) {
  const held = heldItem(player);
  if (!held) {
    player.sendMessage({ translate: "rocco.menu.inventory.empty_hand" });
    return;
  }
  const inv = inventoryOf(rocco);
  if (!inv) return;
  if (usedSlots(inv) >= capacity(rocco)) {
    say(rocco, line("pickup_full"), "bored");
    return;
  }
  setHeldItem(player, undefined);
  const left = addItem(inv, held);
  if (left) giveToPlayer(player, left);
  sound(rocco, "rocco.backpack_open", 0.7);
  play(rocco, "interact");
}

function inventoryForm(rocco, player) {
  const inv = inventoryOf(rocco);
  const stacks = listItems(inv);
  const form = new ActionFormData()
    .title({ translate: "rocco.menu.inventory" })
    .body({ rawtext: [{ translate: "rocco.menu.inventory.body" }, { text: " " + stacks.length + "/" + capacity(rocco) }] })
    .button({ translate: "rocco.menu.inventory.give" }, "textures/ui/upload_glyph");
  for (const { stack } of stacks) form.button(stack.amount + " × " + niceName(stack));
  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined || !rocco.isValid) return;
    if (res.selection === 0) {
      giveHeld(rocco, player);
      return;
    }
    const pick = stacks[res.selection - 1];
    if (!pick || !inv) return;
    const now = inv.getItem(pick.slot);
    if (!now) return;
    inv.setItem(pick.slot, undefined);
    giveToPlayer(player, now);
    inventoryForm(rocco, player);
  });
}

function niceName(stack) {
  const raw = stack.typeId.replace(/^[a-z_]+:/, "").replace(/_/g, " ");
  return stack.nameTag || raw.charAt(0).toUpperCase() + raw.slice(1);
}

// ---------------------------------------------------------------- equipo y mochila

function gearForm(rocco, player) {
  const g = gear(rocco);
  const worn = Object.values(g).filter(Boolean).map((id) => id.replace(/^[a-z_]+:/, "").replace(/_/g, " "));
  const pack = data(rocco, "pack", 0);
  const form = new ActionFormData()
    .title({ translate: "rocco.menu.gear" })
    .body({ rawtext: [{ translate: "rocco.menu.gear.body" }, { text: "\n" + (worn.length ? worn.join(", ") : "-") }] })
    .button({ translate: "rocco.menu.gear.equip" }, "textures/items/iron_chestplate")
    .button({ translate: "rocco.menu.gear.unequip" }, "textures/ui/refresh_light");
  if (pack > 0) form.button({ translate: "rocco.menu.gear.pack_off" }, "textures/items/rocco/mochila_" + pack);
  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined || !rocco.isValid) return;
    if (res.selection === 0) {
      const held = heldItem(player);
      if (held && held.typeId.startsWith("rocco:mochila_")) {
        equipBackpack(rocco, player, held);
        return;
      }
      if (!held || !gearSlot(held.typeId)) {
        say(rocco, line("equip_nothing"), "bored");
        return;
      }
      setHeldItem(player, held.amount > 1 ? new ItemStack(held.typeId, held.amount - 1) : undefined);
      equipFrom(rocco, player, held);
      say(rocco, line("armor_equip"), "smug");
    } else if (res.selection === 1) {
      if (!Object.values(gear(rocco)).some(Boolean)) {
        say(rocco, line("unequip_nothing"), "bored");
        return;
      }
      unequipAll(rocco, player);
      say(rocco, line("unequip_done"), "happy");
    } else if (res.selection === 2) {
      removeBackpack(rocco, player);
    }
  });
}

export function equipBackpack(rocco, player, held) {
  const tier = Number(held.typeId.replace("rocco:mochila_", "")) || 0;
  const before = data(rocco, "pack", 0);
  setHeldItem(player, undefined);
  if (before > 0) giveToPlayer(player, new ItemStack("rocco:mochila_" + before, 1));
  setData(rocco, "pack", tier);
  safe("mochila", () => rocco.setProperty("rocco:pack", tier));
  const seen = json(rocco, "packs_seen", []);
  if (!seen.includes(tier)) {
    seen.push(tier);
    setJson(rocco, "packs_seen", seen);
  }
  play(rocco, "backpack_show");
  sound(rocco, "rocco.backpack_equip", 0.8);
}

function removeBackpack(rocco, player) {
  const tier = data(rocco, "pack", 0);
  if (!tier) {
    say(rocco, line("backpack_none"), "bored");
    return;
  }
  setData(rocco, "pack", 0);
  safe("mochila", () => rocco.setProperty("rocco:pack", 0));
  giveToPlayer(player, new ItemStack("rocco:mochila_" + tier, 1));
  // Lo que ya no le entra, te lo da.
  const inv = inventoryOf(rocco);
  const stacks = listItems(inv);
  for (const { slot, stack } of stacks.slice(CAPACITY[0])) {
    inv.setItem(slot, undefined);
    giveToPlayer(player, stack);
  }
  say(rocco, line("backpack_removed"), "happy");
}

// ---------------------------------------------------------------- lugares

function placesForm(rocco, player) {
  const places = json(rocco, "places", {});
  const names = Object.keys(places);
  const form = new ActionFormData()
    .title({ translate: "rocco.menu.places" })
    .body({ translate: "rocco.menu.places.body" })
    .button({ translate: "rocco.menu.places.save" }, "textures/ui/color_plus");
  for (const n of names) form.button(n);
  form.show(player).then((res) => {
    if (res.canceled || res.selection === undefined || !rocco.isValid) return;
    if (res.selection === 0) {
      new ModalFormData()
        .title({ translate: "rocco.menu.places.save" })
        .textField({ translate: "rocco.menu.places.name" }, "casa")
        .show(player)
        .then((r2) => {
          if (r2.canceled || !r2.formValues) return;
          const name = String(r2.formValues[0] ?? "").trim().slice(0, 32);
          if (!name) {
            say(rocco, line("place_need_name"), "bored");
            return;
          }
          const all = json(rocco, "places", {});
          if (Object.keys(all).length >= 20 && !all[name]) {
            say(rocco, line("place_full"), "bored");
            return;
          }
          const l = player.location;
          all[name] = { x: Math.floor(l.x), y: Math.floor(l.y), z: Math.floor(l.z), d: player.dimension.id };
          setJson(rocco, "places", all);
          say(rocco, line("place_saved", { place: name, name }), "smug");
        });
      return;
    }
    const name = names[res.selection - 1];
    const p = places[name];
    if (!p) return;
    placeOptions(rocco, player, name, p);
  });
}

function placeOptions(rocco, player, name, p) {
  new ActionFormData()
    .title(name)
    .body("X " + p.x + "  Y " + p.y + "  Z " + p.z + "\n" + p.d.replace("minecraft:", ""))
    .button({ translate: "rocco.menu.places.forget" }, "textures/ui/trash")
    .show(player)
    .then((res) => {
      if (res.canceled || res.selection !== 0) return;
      const all = json(rocco, "places", {});
      delete all[name];
      setJson(rocco, "places", all);
      say(rocco, line("place_forgotten", { place: name, name }), "happy");
    });
}

// ---------------------------------------------------------------- personalidad y ayuda

function personalityForm(rocco, player) {
  const mode = data(rocco, "mode", "chill");
  new ActionFormData()
    .title({ translate: "rocco.menu.personality" })
    .body({ translate: "rocco.menu.personality.body" })
    .button({ rawtext: [{ text: mode === "chill" ? "§a✓ " : "" }, { translate: "rocco.menu.mode.chill" }] })
    .button({ rawtext: [{ text: mode === "sin_filtro" ? "§a✓ " : "" }, { translate: "rocco.menu.mode.sin_filtro" }] })
    .show(player)
    .then((res) => {
      if (res.canceled || res.selection === undefined || !rocco.isValid) return;
      const next = res.selection === 0 ? "chill" : "sin_filtro";
      setData(rocco, "mode", next);
      safe("jardin", () => rocco.setProperty("rocco:garden", next === "chill"));
      play(rocco, "wave");
    });
}

function helpForm(rocco, player) {
  new ActionFormData()
    .title({ translate: "rocco.menu.help" })
    .body({ translate: aiAvailable() ? "rocco.help.body_ai" : "rocco.help.body" })
    .button({ translate: "gui.rocco.truco.yes" })
    .show(player);
}

export { busy };
