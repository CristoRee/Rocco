// Rocco para Minecraft Bedrock: aca se conectan los eventos del juego con el resto.
import {
  world, system, Player, CommandPermissionLevel, CustomCommandParamType, CustomCommandStatus,
} from "@minecraft/server";
import { isRocco, roccoOf, rt, state, ownerId, play, say, setFace, sound, forget } from "./rocco.js";
import { line } from "./offline.js";
import { safe, now } from "./util.js";
import { tickAll, tickPresence, onFirstJoin, onReturn, rememberLeaving, beginAwakening, spawnFor } from "./life.js";
import { openMenu, giveHeld, equipBackpack } from "./menu.js";
import { hear, onScriptEvent, aiAvailable, aiStatus } from "./chat.js";
import { onRoccoHit, onRoccoHurt, onDied, rise, gearSlot, equipFrom } from "./combat.js";
import { eatFood, playerDrankMate, busy } from "./activities.js";
import { trucoMateDrunk, endAllFor } from "./truco/session.js";
import { heldItem, setHeldItem, isFood } from "./items.js";

// ---------------------------------------------------------------- comandos

system.beforeEvents.startup.subscribe((event) => {
  const reg = event.customCommandRegistry;
  reg.registerCommand(
    {
      name: "rocco:decir",
      description: "Le decis algo a tu Rocco. Ej: /rocco:decir \"hola, como andas?\"",
      permissionLevel: CommandPermissionLevel.Any,
      cheatsRequired: false,
      mandatoryParameters: [{ name: "mensaje", type: CustomCommandParamType.String }],
    },
    (origin, mensaje) => {
      const player = origin.sourceEntity;
      if (!(player instanceof Player)) return { status: CustomCommandStatus.Failure, message: "Solo un jugador puede hablarle." };
      system.run(() => {
        const rocco = roccoOf(player);
        if (!rocco) player.sendMessage({ translate: "rocco.command.no_rocco" });
        else hear(rocco, player, String(mensaje));
      });
      return { status: CustomCommandStatus.Success };
    },
  );
  reg.registerCommand(
    {
      name: "rocco:menu",
      description: "Abre el menu de tu Rocco.",
      permissionLevel: CommandPermissionLevel.Any,
      cheatsRequired: false,
    },
    (origin) => {
      const player = origin.sourceEntity;
      if (!(player instanceof Player)) return { status: CustomCommandStatus.Failure };
      system.run(() => {
        const rocco = roccoOf(player);
        if (!rocco) player.sendMessage({ translate: "rocco.command.no_rocco" });
        else openMenu(rocco, player);
      });
      return { status: CustomCommandStatus.Success };
    },
  );
  reg.registerCommand(
    {
      name: "rocco:ia",
      description: "Como anda la IA de Rocco.",
      permissionLevel: CommandPermissionLevel.Any,
      cheatsRequired: false,
    },
    (origin) => {
      const player = origin.sourceEntity;
      if (!(player instanceof Player)) return { status: CustomCommandStatus.Failure };
      system.run(() => {
        const error = aiStatus();
        if (error === null) player.sendMessage({ translate: "rocco.ia.off_detail" });
        else if (error) player.sendMessage({ rawtext: [{ translate: "rocco.ia.error" }, { text: " " + error }] });
        else player.sendMessage({ translate: "rocco.menu.status.ai_on" });
      });
      return { status: CustomCommandStatus.Success };
    },
  );
  // Si perdiste a tu Rocco (o queres uno nuevo): brota otra vez al lado tuyo.
  reg.registerCommand(
    {
      name: "rocco:brotar",
      description: "Hace brotar a tu Rocco al lado tuyo (si no tenes uno).",
      permissionLevel: CommandPermissionLevel.GameDirectors,
      cheatsRequired: false,
    },
    (origin) => {
      const player = origin.sourceEntity;
      if (!(player instanceof Player)) return { status: CustomCommandStatus.Failure };
      system.run(() => {
        if (roccoOf(player)) player.sendMessage({ translate: "rocco.command.already" });
        else spawnFor(player);
      });
      return { status: CustomCommandStatus.Success };
    },
  );
});

// ---------------------------------------------------------------- entrar y salir

world.afterEvents.playerSpawn.subscribe((event) => {
  if (!event.initialSpawn) return;
  const player = event.player;
  system.runTimeout(() => {
    if (!player.isValid) return;
    safe("brotar", () => onFirstJoin(player));
    safe("regalo", () => onReturn(player));
  }, 60);
});

world.beforeEvents.playerLeave.subscribe((event) => {
  const id = event.player.id;
  system.run(() => {
    safe("hora", () => world.setDynamicProperty("rocco:left:" + id, Date.now()));
  });
});

// ---------------------------------------------------------------- tocar a Rocco

world.afterEvents.playerInteractWithEntity.subscribe((event) => {
  const rocco = event.target;
  const player = event.player;
  if (!isRocco(rocco)) return;
  system.run(() => safe("tocar", () => interact(rocco, player)));
});

function interact(rocco, player) {
  const s = state(rocco);
  if (s === "plant") {
    beginAwakening(rocco, player);
    return;
  }
  if (s === "awakening" || s === "rising") return;
  if (s === "grave") {
    if (ownerId(rocco) === player.id) rise(rocco);
    return;
  }
  if (ownerId(rocco) && ownerId(rocco) !== player.id) {
    player.sendMessage({ rawtext: [{ text: "<" + (rocco.nameTag || "Rocco") + "> " + line("menu_not_yours") }] });
    return;
  }
  const held = heldItem(player);
  if (held && !player.isSneaking) {
    if (held.typeId.startsWith("rocco:mochila_")) {
      equipBackpack(rocco, player, held);
      return;
    }
    if (gearSlot(held.typeId) && held.typeId !== "rocco:botella_rota") {
      setHeldItem(player, undefined);
      equipFrom(rocco, player, held);
      say(rocco, line("armor_equip"), "smug");
      return;
    }
    if (held.typeId !== "rocco:mate" && isFood(held) && !busy(rocco)) {
      feed(rocco, player, held);
      return;
    }
  }
  openMenu(rocco, player);
}

function feed(rocco, player, held) {
  const bad = /rotten|spider_eye|poisonous|pufferfish|chorus/.test(held.typeId);
  if (held.amount <= 1) setHeldItem(player, undefined);
  else {
    held.amount -= 1;
    setHeldItem(player, held);
  }
  eatFood(rocco, bad);
  sound(rocco, "rocco.chew", 0.8);
  const health = rocco.getComponent("minecraft:health");
  if (health && !bad) safe("curar", () => health.setCurrentValue(Math.min(health.effectiveMax, health.currentValue + 6)));
  system.runTimeout(() => {
    if (rocco.isValid) say(rocco, line("eat"), bad ? "sad" : "bliss");
  }, 30);
}

// ---------------------------------------------------------------- peleas y la tumba

world.afterEvents.entityHitEntity.subscribe((event) => {
  const attacker = event.damagingEntity;
  if (!isRocco(attacker)) return;
  const r = rt(attacker);
  r.lastHitAt = now();
  safe("golpe", () => onRoccoHit(attacker, event.hitEntity));
});

world.afterEvents.entityHurt.subscribe((event) => {
  const hurt = event.hurtEntity;
  if (!isRocco(hurt)) return;
  rt(hurt).lastHitAt = now();
  safe("lastimado", () => onRoccoHurt(hurt, event.damage, event.damageSource));
});

world.afterEvents.dataDrivenEntityTrigger.subscribe((event) => {
  if (event.eventId !== "rocco:died" || !isRocco(event.entity)) return;
  const rocco = event.entity;
  system.run(() => safe("tumba", () => onDied(rocco)));
}, { entityTypes: ["rocco:rocco"], eventTypes: ["rocco:died"] });

world.afterEvents.entityRemove.subscribe((event) => {
  if (event.typeId === "rocco:rocco") forget(event.removedEntityId);
});

// ---------------------------------------------------------------- tomar mate y Roco Cola

world.afterEvents.itemCompleteUse.subscribe((event) => {
  const player = event.source;
  const id = event.itemStack?.typeId;
  if (!player || !id) return;
  if (id === "rocco:mate") {
    system.run(() => {
      if (trucoMateDrunk(player)) return;
      const rocco = roccoOf(player);
      if (rocco) playerDrankMate(player, rocco);
      safe("mate", () => player.addEffect("haste", 200, { amplifier: 0, showParticles: false }));
    });
    return;
  }
  if (id === "rocco:roco_cola" || id === "rocco:roco_cola_half") {
    system.run(() => {
      safe("cola", () => {
        player.addEffect("speed", 20 * 30, { amplifier: 0 });
        player.addEffect("haste", 20 * 30, { amplifier: 0 });
      });
      safe("sonido", () => player.dimension.playSound("rocco.cola_refresh", player.location));
      const rocco = roccoOf(player);
      if (rocco && state(rocco) === "active" && !busy(rocco) && Math.random() < 0.5) {
        system.runTimeout(() => {
          if (!rocco.isValid) return;
          play(rocco, "react_burp");
          say(rocco, line("cola_after"), "smug");
        }, 30);
      }
    });
  }
});

// ---------------------------------------------------------------- la IA (paquete aparte)

system.afterEvents.scriptEventReceive.subscribe((event) => {
  if (event.id.startsWith("rocco:")) safe("IA", () => onScriptEvent(event.id, event.message));
});

// ---------------------------------------------------------------- el reloj

system.runInterval(() => tickAll(), 1);
system.runInterval(() => safe("presencia", () => tickPresence()), 1200);

world.afterEvents.playerLeave?.subscribe((event) => {
  system.run(() => {
    for (const p of world.getPlayers()) if (p.id === event.playerId) endAllFor(p);
  });
});

console.warn("[Rocco] listo");
export { setFace };
