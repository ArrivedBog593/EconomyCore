# Economy Core 0.11.0

*NeoForge 1.21.1 · 2026-11-04*

> **A purse and a bank.** Every player now keeps credits in two places: a purse to spend from, and a bank to put them away in. New players can be given credits the first time they join, and the exchange and the shop are furniture now rather than plain cubes.

## ⚠️ Important Notes

- Every balance players already had stays where it was, now called the purse, and everyone's bank starts empty. Nothing to do when updating
- Going back to an older version after updating keeps every purse, but loses what was in the banks
- The exchange and the shop are wooden now: they are broken with an axe rather than a pickaxe
- **For mod developers:** `Credits.balance`, `Credits.spendable` and `Settlement.move` without a pocket now mean the purse, so a mod built on 0.10 works unchanged and spends from the purse. The bank is read with `Credits.savings` and moved by naming `Pocket.SAVINGS` in a `Settlement`. `CreditMath` works out shares of an amount in whole credits, with the rounding the caller chooses, for fees and commissions

## ✨ New Features

- **A purse and a bank for every player.** The purse is what everything spends from: the shop, the exchange, `/credits pay`, the tables of mods like TableGames. The bank is put away and cannot be spent until it is taken back out. `/credits deposit <amount>` and `/credits withdraw <amount>` move credits between them, `all` for everything; credits set aside at a game table or in a bid cannot be put away. `/credits` shows both, and so does `/credits balance <player>` for staff. Staff change the bank as they do the purse, with `bank` on the end: `/credits give <player> <amount> bank`
- **ATM.** A cash machine block in the bank's green and gold, with a screen, a keypad and a card slot, that opens a player's bank where it stands: the bank's own block, beside the banker and `/bank`, as the exchange and the shop have theirs. Broken with a pickaxe
- **Bankers.** A third kind of clerk, in the bank's green and gold, with three outfits of their own. Right-click one, or type `/bank`, for a window laid out like SkyBlock's bank: your purse and your bank at the top, and a row of items with tooltips. The chest deposits all, half or an amount; the dispenser withdraws all, half, 20% or an amount; the map shows your last ten movements; the torch says how the bank works on this server. `access.bankCommand` in the config switches `/bank` off, and `/credits deposit` and `/credits withdraw` with it, so players have to go to a banker
- **Losing credits on death.** `/economycore deathloss <percent>` makes players lose that share of their purse when they die, which is what makes the bank worth using. It is off by default, and typing it without a value shows the current one. Only the free part of the purse is touched, never savings nor credits set aside at a game table. What the player keeps is rounded to the nearest credit, halves in their favor: with half of 15 to lose, they keep 8. Nothing is lost under `keepInventory`, and what is lost is the server's income, like the shop's sales. A crash that rolls a death back gives the credits back with it. `/economycore deathloss combat true` charges only deaths at the hands of a mob or a player, as SkyBlock does, so a fall or the lava costs nothing
- **Interest on savings.** Off by default. With `interest.enabled`, every `interest.periodHours` real hours (31 by default) the bank pays everyone with savings, online or not, by tranches: 2% on the first 10,000,000 and 1% up to 15,000,000 unless `interest.tranches` says otherwise, and nothing above the last. Rates may have decimals. Every credit it pays is new money
- **Bank account levels.** As in Hypixel SkyBlock: every bank holds up to its level's limit, and the next level is bought at the banker's new Upgrades tab. Staff set them up from a staff key on a banker or an ATM
- **Your purse on screen.** Your purse and your bank at the right of the screen, where SkyBlock keeps its sidebar, and above a scoreboard sidebar if the server shows one. `hud.showPurse` in `config/economycore-client.toml` hides it, or Mods → EconomyCore → Config in game
- **Light or dark windows.** `ui.theme` in `config/economycore-client.toml`: `LIGHT`, `DARK`, or `AUTO`, the default, which goes dark when your resource packs make Minecraft's own windows dark. The shop, the exchange, the bank and the staff screens all follow it
- **Merchant's pass.** Like SkyBlock's booster cookie: used (twice, to confirm) for `pass.durationHours` real hours of privileges, 96 by default, counting while away; another adds to what is left. While it lasts: `/bank`, `/exchange` and `/shop` from anywhere even when switched off, and no purse lost on dying. The time left shows under the purse on screen. It is put in the shop the first time a world starts, switched off, for the owner to price and switch on. Other mods can ask whether a player has one through `MerchantPass` in the API. Operators give one without the item, as `/effect` gives a potion's, with `/economycore pass give <players> [seconds]`, and end it with `/economycore pass clear <players>`
- **Shop stock and limits.** `/economycore shop disable <entry>` keeps an entry in the window, dimmed, but not for sale, and `enable` puts it back. `stock <entry> <amount|unlimited>` gives it a stock that runs out; `restock <entry> <hours>` fills it back up every so many real hours; `limit <entry> <perDay>` holds each player to so many purchases a day, from midnight UTC; `info <entry>` shows all of it. Players see what is left, their own limit and when it restocks in the tooltip, counting down to the second. `restock <entry> now` fills one up at once
- **Both are safe from crashes.** Deposits and withdrawals are logged like any other movement of credits, and each pocket is put back on its own after a crash
- **Welcome balance.** `players.welcomeBalance` in `economycore-server.toml` pays a player that many credits the first time they join the world, with a message telling them. It is 0 by default, which gives nothing. Players who had already joined are not paid when it is set or raised, and nobody is paid twice. It shows in the transaction log as `welcome`, and comes back after a crash like any other movement of credits
- **The exchange is a cashier's counter.** A mahogany cabinet under a marble counter, with a cashier's cage behind it and coins and notes on top. Its outline follows the model and turns with the way it faces
- **The shop is a display case.** A cabinet with two doors under a glass vitrine framed in gold, with goods on a shelf. Both share the materials of TableGames' tables, so a casino floor looks like one place
- **Saved data carries a version.** Both of the mod's save files now say which version of their format they are in, so later changes can bring old worlds forward. From this version on, the mod also warns in the log when a world was saved by a newer version than itself

## 🐛 Bug Fixes

- The exchange and the shop no longer hide the faces of the blocks next to them
- **The exchange and the shop dropped nothing when broken.** Their loot tables were in the wrong folder, so Minecraft never found them; they now drop themselves

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
- **Cajero automático.** Un bloque con el verde y dorado del banco, con pantalla, teclado y ranura de tarjetas, que abre el banco del jugador donde esté: el bloque propio del banco, junto al banquero y `/bank`, como la casa de cambio y la tienda tienen el suyo. Se rompe con pico
- **Banqueros.** Un tercer tipo de empleado, con el verde y dorado del banco y tres atuendos propios. Haz clic derecho en uno, o escribe `/bank`, para una ventana parecida al banco de SkyBlock: tu monedero y tu banco arriba, y una fila de objetos con tooltips. El cofre deposita todo, la mitad o una cantidad; el dispensador retira todo, la mitad, el 20 % o una cantidad; el mapa muestra tus últimos diez movimientos; la antorcha explica cómo funciona el banco en este servidor. `access.bankCommand` en la configuración apaga `/bank`, y con él `/credits deposit` y `/credits withdraw`, para que los jugadores tengan que ir a un banquero
- **Perder créditos al morir.** `/economycore deathloss <porcentaje>` hace que los jugadores pierdan esa parte de su monedero al morir, que es lo que hace que valga la pena usar el banco. Está desactivado por defecto, y sin valor muestra el actual. Solo se toca la parte libre del monedero, nunca el banco ni los créditos apartados en una mesa de juego. Lo que le queda al jugador se redondea al crédito más cercano, y las mitades a su favor: si pierde la mitad de 15, se queda con 8. Con `keepInventory` no se pierde nada, y lo perdido es ingreso del servidor, como las ventas de la tienda. Un crash que revierte una muerte devuelve también los créditos. `/economycore deathloss combat true` cobra solo las muertes a manos de un mob o un jugador, como en SkyBlock, así que una caída o la lava no cuestan nada
- **Interés del banco.** Desactivado por defecto. Con `interest.enabled`, cada `interest.periodHours` horas reales (31 por defecto) el banco paga a todos los que tengan algo guardado, conectados o no, por tramos: un 2 % por los primeros 10.000.000 y un 1 % hasta 15.000.000, salvo que `interest.tranches` diga otra cosa, y nada por encima del último. Los porcentajes pueden tener decimales. Cada crédito que paga es dinero nuevo
- **Niveles de la cuenta del banco.** Como en Hypixel SkyBlock: cada banco guarda hasta el tope de su nivel, y el siguiente se compra en la nueva pestaña Mejoras del banquero. El staff los configura con la llave de staff en un banquero o un cajero
- **Tu monedero en pantalla.** Tu monedero y tu banco a la derecha de la pantalla, donde SkyBlock tiene su barra lateral, y encima de la barra del marcador si el servidor la muestra. `hud.showPurse` en `config/economycore-client.toml` lo oculta, o Mods → EconomyCore → Configuración en el juego
- **Ventanas claras u oscuras.** `ui.theme` en `config/economycore-client.toml`: `LIGHT`, `DARK` o `AUTO`, por defecto, que se pone oscuro cuando tus paquetes de recursos oscurecen las ventanas del propio Minecraft. La tienda, la casa de cambio, el banco y las pantallas del staff lo siguen
- **Pase de mercader.** Como la booster cookie de SkyBlock: se usa (dos veces, para confirmar) a cambio de `pass.durationHours` horas reales de privilegios, 96 por defecto, que cuentan aunque no estés; otro suma a lo que queda. Mientras dura: `/bank`, `/exchange` y `/shop` desde cualquier sitio aunque estén apagados, y no se pierde el monedero al morir. Lo que queda se ve bajo el monedero en pantalla. Se pone en la tienda la primera vez que arranca un mundo, desactivado, para que el dueño le ponga precio y lo active. Otros mods pueden consultar si un jugador lo tiene con `MerchantPass` en la API. Los operadores lo dan sin el objeto, como `/effect` da el de una poción, con `/economycore pass give <jugadores> [segundos]`, y lo terminan con `/economycore pass clear <jugadores>`
- **Existencias y límites en la tienda.** `/economycore shop disable <artículo>` deja un artículo en la ventana, atenuado, pero sin venderse, y `enable` lo vuelve a poner. `stock <artículo> <cantidad|unlimited>` le da existencias que se agotan; `restock <artículo> <horas>` lo repone cada tantas horas reales; `limit <artículo> <porDía>` limita cuántas veces al día puede comprarlo cada jugador, desde la medianoche UTC; `info <artículo>` lo muestra todo. Los jugadores ven en el tooltip lo que queda, su propio límite y cuándo se repone, con la cuenta atrás al segundo. `restock <artículo> now` lo llena al momento
- **Los dos están a salvo de los crashes.** Los depósitos y retiros quedan en el log como cualquier otro movimiento de créditos, y tras un crash se repone cada bolsillo por separado
- **Dinero de bienvenida.** `players.welcomeBalance` en `economycore-server.toml` le da esa cantidad de créditos a un jugador la primera vez que entra al mundo, con un mensaje que se lo dice. Por defecto es 0, que no da nada. A los jugadores que ya habían entrado no se les paga al ponerlo o subirlo, y a nadie se le paga dos veces. Aparece en el log de transacciones como `welcome`, y se recupera tras un crash como cualquier otro movimiento de créditos
- **La casa de cambio es un mostrador de caja.** Un mueble de caoba bajo un mostrador de mármol, con una ventanilla de cajero detrás y monedas y billetes encima. Su contorno sigue el modelo y gira según hacia dónde mira
- **La tienda es una vitrina.** Un mueble con dos puertas bajo una vitrina de cristal con marco dorado y mercancía en un estante. Las dos comparten los materiales de las mesas de TableGames, para que la sala de un casino parezca un solo lugar
- **Los datos guardados llevan versión.** Los dos archivos de guardado del mod dicen ahora en qué versión de su formato están, para que los cambios futuros puedan traer los mundos viejos. A partir de esta versión, el mod también avisa en el log cuando un mundo lo guardó una versión más nueva que él

## 🐛 Correcciones

- La casa de cambio y la tienda ya no ocultan las caras de los bloques de al lado
- **La casa de cambio y la tienda no soltaban nada al romperlas.** Sus tablas de botín estaban en la carpeta equivocada y Minecraft no las encontraba; ahora se sueltan a sí mismas

## 📦 Dependencias

- Sin cambios

</details>
