# Economy Core 0.11.0

*NeoForge 1.21.1 · 2026-11-04*

> **A purse and a bank.** Every player now keeps credits in two places: a purse to spend from, and a bank to put them away in. New players can be given credits the first time they join, and the exchange and the shop are furniture now rather than plain cubes.

## ⚠️ Important Notes

- Every balance players already had stays where it was, now called the purse, and everyone's bank starts empty. Nothing to do when updating
- Going back to an older version after updating keeps every purse, but loses what was in the banks
- The exchange and the shop are wooden now: they are broken with an axe rather than a pickaxe
- **For mod developers:** `Credits.balance`, `Credits.spendable` and `Settlement.move` without a pocket now mean the purse, so a mod built on 0.10 works unchanged and spends from the purse. The bank is read with `Credits.savings` and moved by naming `Pocket.SAVINGS` in a `Settlement`. `CreditMath` works out shares of an amount in whole credits, with the rounding the caller chooses, for fees and commissions

## ✨ New Features

- **A purse and a bank for every player.** The purse is what everything spends from: the shop, the exchange, `/credits pay`, the tables of mods like TableGames. The bank is put away and cannot be spent until it is taken back out. `/credits deposit <amount>` and `/credits withdraw <amount>` move credits between them, `all` for everything; credits set aside at a game table or in a bid cannot be put away. `/credits` shows both, and so does `/credits balance <player>` for staff
- **Bankers.** A third kind of clerk, in the bank's green and gold, with three outfits of their own. Right-click one, or type `/bank`, for a window with your purse, your bank, an amount and buttons to deposit and withdraw it or all of it. `access.bankCommand` in the config switches `/bank` off, and `/credits deposit` and `/credits withdraw` with it, so players have to go to a banker
- **Losing credits on death.** `/economycore deathloss <percent>` makes players lose that share of their purse when they die, which is what makes the bank worth using. It is off by default, and typing it without a value shows the current one. Only the free part of the purse is touched, never savings nor credits set aside at a game table. What the player keeps is rounded to the nearest credit, halves in their favor: with half of 15 to lose, they keep 8. Nothing is lost under `keepInventory`, and what is lost is the server's income, like the shop's sales. A crash that rolls a death back gives the credits back with it
- **Both are safe from crashes.** Deposits and withdrawals are logged like any other movement of credits, and each pocket is put back on its own after a crash
- **Welcome balance.** `players.welcomeBalance` in `economycore-server.toml` pays a player that many credits the first time they join the world, with a message telling them. It is 0 by default, which gives nothing. Players who had already joined are not paid when it is set or raised, and nobody is paid twice. It shows in the transaction log as `welcome`, and comes back after a crash like any other movement of credits
- **The exchange is a cashier's counter.** A mahogany cabinet under a marble counter, with a cashier's cage behind it and coins and notes on top. Its outline follows the model and turns with the way it faces
- **The shop is a display case.** A cabinet with two doors under a glass vitrine framed in gold, with goods on a shelf. Both share the materials of TableGames' tables, so a casino floor looks like one place
- **Saved data carries a version.** Both of the mod's save files now say which version of their format they are in, so later changes can bring old worlds forward. From this version on, the mod also warns in the log when a world was saved by a newer version than itself

## 🐛 Bug Fixes

- The exchange and the shop no longer hide the faces of the blocks next to them

## 📦 Dependencies

- No changes

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Un monedero y un banco.** Cada jugador guarda ahora sus créditos en dos sitios: un monedero para gastar y un banco donde guardarlos. Los jugadores nuevos pueden recibir créditos la primera vez que entran, y la casa de cambio y la tienda ahora son muebles en vez de cubos.

## ⚠️ Notas Importantes

- Los saldos que ya tenían los jugadores se quedan donde estaban, que ahora se llama monedero, y el banco de todos empieza vacío. No hay que hacer nada al actualizar
- Volver a una versión anterior después de actualizar conserva todos los monederos, pero pierde lo que había en los bancos
- La casa de cambio y la tienda ahora son de madera: se rompen con un hacha en vez de con un pico
- **Para desarrolladores de mods:** `Credits.balance`, `Credits.spendable` y `Settlement.move` sin bolsillo ahora se refieren al monedero, así que un mod hecho para la 0.10 funciona sin cambios y gasta del monedero. El banco se lee con `Credits.savings` y se mueve nombrando `Pocket.SAVINGS` en un `Settlement`. `CreditMath` calcula partes de una cantidad en créditos enteros, con el redondeo que elija quien la usa, para comisiones

## ✨ Nuevas Características

- **Un monedero y un banco para cada jugador.** Del monedero sale todo lo que se gasta: la tienda, la casa de cambio, `/credits pay`, las mesas de mods como TableGames. Lo del banco está guardado y no se puede gastar hasta sacarlo. `/credits deposit <cantidad>` y `/credits withdraw <cantidad>` mueven créditos entre los dos, `all` para todo; los créditos apartados en una mesa de juego o una puja no se pueden guardar. `/credits` muestra los dos, y también `/credits balance <jugador>` para el staff
- **Banqueros.** Un tercer tipo de empleado, con el verde y dorado del banco y tres atuendos propios. Haz clic derecho en uno, o escribe `/bank`, para una ventana con tu monedero, tu banco, una cantidad y botones para depositarla o retirarla, o todo. `access.bankCommand` en la configuración apaga `/bank`, y con él `/credits deposit` y `/credits withdraw`, para que los jugadores tengan que ir a un banquero
- **Perder créditos al morir.** `/economycore deathloss <porcentaje>` hace que los jugadores pierdan esa parte de su monedero al morir, que es lo que hace que valga la pena usar el banco. Está desactivado por defecto, y sin valor muestra el actual. Solo se toca la parte libre del monedero, nunca el banco ni los créditos apartados en una mesa de juego. Lo que le queda al jugador se redondea al crédito más cercano, y las mitades a su favor: si pierde la mitad de 15, se queda con 8. Con `keepInventory` no se pierde nada, y lo perdido es ingreso del servidor, como las ventas de la tienda. Un crash que revierte una muerte devuelve también los créditos
- **Los dos están a salvo de los crashes.** Los depósitos y retiros quedan en el log como cualquier otro movimiento de créditos, y tras un crash se repone cada bolsillo por separado
- **Dinero de bienvenida.** `players.welcomeBalance` en `economycore-server.toml` le da esa cantidad de créditos a un jugador la primera vez que entra al mundo, con un mensaje que se lo dice. Por defecto es 0, que no da nada. A los jugadores que ya habían entrado no se les paga al ponerlo o subirlo, y a nadie se le paga dos veces. Aparece en el log de transacciones como `welcome`, y se recupera tras un crash como cualquier otro movimiento de créditos
- **La casa de cambio es un mostrador de caja.** Un mueble de caoba bajo un mostrador de mármol, con una ventanilla de cajero detrás y monedas y billetes encima. Su contorno sigue el modelo y gira según hacia dónde mira
- **La tienda es una vitrina.** Un mueble con dos puertas bajo una vitrina de cristal con marco dorado y mercancía en un estante. Las dos comparten los materiales de las mesas de TableGames, para que la sala de un casino parezca un solo lugar
- **Los datos guardados llevan versión.** Los dos archivos de guardado del mod dicen ahora en qué versión de su formato están, para que los cambios futuros puedan traer los mundos viejos. A partir de esta versión, el mod también avisa en el log cuando un mundo lo guardó una versión más nueva que él

## 🐛 Correcciones

- La casa de cambio y la tienda ya no ocultan las caras de los bloques de al lado

## 📦 Dependencias

- Sin cambios

</details>
