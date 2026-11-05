# Economy Core 0.10.2

*NeoForge 1.21.1 · 2026-11-04*

> **Money to start with, and counters that look the part.** New players can be given credits the first time they join, and the exchange and the shop are furniture now rather than plain cubes.

## ⚠️ Important Notes

- The exchange and the shop are wooden now: they are broken with an axe rather than a pickaxe

## ✨ New Features

- **Welcome balance.** `players.welcomeBalance` in `economycore-server.toml` pays a player that many credits the first time they join the world, with a message telling them. It is 0 by default, which gives nothing. Players who had already joined are not paid when it is set or raised, and nobody is paid twice. It shows in the transaction log as `welcome`, and comes back after a crash like any other movement of credits
- **The exchange is a cashier's counter.** A mahogany cabinet under a marble counter, with a cashier's cage behind it and coins and notes on top. Its outline follows the model and turns with the way it faces
- **The shop is a display case.** A cabinet with two doors under a glass vitrine framed in gold, with goods on a shelf. Both share the materials of TableGames' tables, so a casino floor looks like one place

## 🐛 Bug Fixes

- The exchange and the shop no longer hide the faces of the blocks next to them

## 📦 Dependencies

- No changes

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Dinero para empezar, y mostradores que parecen mostradores.** Los jugadores nuevos pueden recibir créditos la primera vez que entran, y la casa de cambio y la tienda ahora son muebles en vez de cubos.

## ⚠️ Notas Importantes

- La casa de cambio y la tienda ahora son de madera: se rompen con un hacha en vez de con un pico

## ✨ Nuevas Características

- **Dinero de bienvenida.** `players.welcomeBalance` en `economycore-server.toml` le da esa cantidad de créditos a un jugador la primera vez que entra al mundo, con un mensaje que se lo dice. Por defecto es 0, que no da nada. A los jugadores que ya habían entrado no se les paga al ponerlo o subirlo, y a nadie se le paga dos veces. Aparece en el log de transacciones como `welcome`, y se recupera tras un crash como cualquier otro movimiento de créditos
- **La casa de cambio es un mostrador de caja.** Un mueble de caoba bajo un mostrador de mármol, con una ventanilla de cajero detrás y monedas y billetes encima. Su contorno sigue el modelo y gira según hacia dónde mira
- **La tienda es una vitrina.** Un mueble con dos puertas bajo una vitrina de cristal con marco dorado y mercancía en un estante. Las dos comparten los materiales de las mesas de TableGames, para que la sala de un casino parezca un solo lugar

## 🐛 Correcciones

- La casa de cambio y la tienda ya no ocultan las caras de los bloques de al lado

## 📦 Dependencias

- Sin cambios

</details>
