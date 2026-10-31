# Economy Core 0.10.0

*NeoForge 1.21.1 · 2026-10-30*

> **The first public beta.** Credits for every player, an exchange, a shop, and clerks to put behind their counters.

## ⚠️ Important Notes

- This is a beta. Saved data may still change before 1.0, so back up your world before updating
- Mods built on Economy Core should use only the `economycore.api` package; everything else may change in any release

## ✨ New Features

### Credits

- Every player has an account in credits, saved with the world
- Every movement is logged, and whatever a crash interrupted is put back when the server starts
- `/credits` shows your balance; `/credits pay` sends credits to another player

### The exchange and the shop

- **The exchange** trades any item for credits and back, at prices set in game, with an optional buyback surcharge
- **The shop** sells items, enchanted or renamed ones included, exactly as they were listed, and never buys back
- Prices are checked against the real crafting recipes before they are accepted, so they cannot be farmed for profit

### Three ways in

- **Blocks**: an exchange and a shop to place in the world
- **Clerks**: an exchange clerk and a shop clerk, placed with a spawn egg or `/summon`. They stay put, never take damage or despawn, and turn their head to nearby players. Three outfits each; crouch and right-click in creative with an empty hand to change it, hit in creative to remove
- **Commands**: `/exchange` and `/shop` open them from anywhere, and can be switched off in the server config

### Staff

- Moderators and administrators run the economy without operator rights, each with a staff key of their own
- `/credits balance <player>` shows anybody's balance, online or not

## 📦 Dependencies

- **NeoForge** — 21.1 or newer, for Minecraft 1.21.1

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **La primera beta pública.** Créditos para cada jugador, una casa de cambio, una tienda y empleados para ponerlos detrás del mostrador.

## ⚠️ Notas Importantes

- Es una beta. Los datos guardados aún pueden cambiar antes de la 1.0, así que haz una copia de tu mundo antes de actualizar
- Los mods que se construyan sobre Economy Core deben usar solo el paquete `economycore.api`; todo lo demás puede cambiar en cualquier versión

## ✨ Nuevas Características

### Créditos

- Cada jugador tiene una cuenta en créditos, guardada con el mundo
- Cada movimiento queda registrado, y lo que un crash dejó a medias se repone al arrancar el servidor
- `/credits` muestra tu saldo; `/credits pay` envía créditos a otro jugador

### La casa de cambio y la tienda

- **La casa de cambio** cambia cualquier objeto por créditos y de vuelta, a precios fijados dentro del juego, con un recargo de recompra opcional
- **La tienda** vende objetos, incluso encantados o renombrados, tal cual se pusieron a la venta, y nunca compra
- Los precios se comparan con las recetas de crafteo reales antes de aceptarse, para que no se puedan farmear con ganancia

### Tres formas de entrar

- **Bloques**: una casa de cambio y una tienda para poner en el mundo
- **Empleados**: un cajero de la casa de cambio y un empleado de la tienda, que se ponen con su huevo generador o con `/summon`. No se mueven, no reciben daño ni desaparecen, y giran la cabeza hacia los jugadores cercanos. Tres atuendos cada uno; en creativo, agáchate y haz clic derecho con la mano vacía para cambiarlo, y golpéalo para quitarlo
- **Comandos**: `/exchange` y `/shop` los abren desde cualquier sitio, y se pueden apagar en la configuración del servidor

### Staff

- Moderadores y administradores manejan la economía sin ser operadores, cada uno con su propia llave de staff
- `/credits balance <jugador>` muestra el saldo de cualquiera, conectado o no

## 📦 Dependencias

- **NeoForge** — 21.1 o más nuevo, para Minecraft 1.21.1

</details>
