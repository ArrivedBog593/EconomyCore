# Economy Core

Un mod de [NeoForge](https://neoforged.net/) 1.21.1 que le da a un servidor una economía en
créditos, y una base sobre la que otros mods pueden construir: casinos, casas de subastas,
bazares.

[English](README.md)

> **Beta.** Todo lo de abajo funciona y se ha jugado en un servidor dedicado, pero puede
> cambiar antes de la 1.0. Haz una copia de tu mundo antes de actualizar.

---

## Qué añade

- **Cuentas personales en créditos** para cada jugador, guardadas con el mundo, en dos
  bolsillos: un monedero para gastar, del que el servidor puede hacer perder una parte al
  morir, y un banco donde guardar a salvo. Cada movimiento queda en un registro, y lo que un
  crash dejó a medias se repone al arrancar.
- **La casa de cambio**: entrega cualquier objeto a cambio de créditos y recómpralo
  después, a los precios que el servidor fija dentro del juego. Los objetos de cualquier mod
  pueden ser moneda, o simples lingotes de vanilla. Un recargo de recompra opcional evita
  que se use como almacén gratis.
- **La tienda**: vende objetos por créditos, incluso encantados o renombrados, tal cual se
  pusieron a la venta. Nunca compra nada, y por eso es por donde los créditos salen de la
  economía.
- **Revisión de precios**: antes de aceptar un precio se compara con las recetas de
  crafteo reales. Un conjunto de precios que permitiría fabricar créditos crafteando se
  rechaza, y se sugiere la cifra correcta.
- **Una lista de staff** que maneja la economía sin ser operadores. Los moderadores
  manejan la tienda y la casa de cambio; los administradores, además, el banco del servidor
  y los saldos de los jugadores. Cada uno tiene su propia llave de staff.

## Tres formas de entrar

La casa de cambio y la tienda abren la misma pantalla sin importar por dónde llegue el
jugador. Cada servidor elige cuáles usa.

- **Un bloque** para poner detrás de un mostrador.
- **Un empleado**: un NPC de la tienda o de la casa de cambio, que se pone con su huevo
  generador o con `/summon`, como cualquier mob. Se queda donde lo pones, mira hacia donde
  lo pusiste, no recibe daño, no desaparece y gira la cabeza hacia quien tenga cerca. Cada
  tipo tiene tres atuendos propios. En creativo, agáchate y haz clic derecho con la mano
  vacía para cambiarle el atuendo; golpéalo en creativo para quitarlo. Ponle nombre con una
  etiqueta.
- **Un comando**: `/exchange` y `/shop`, desde cualquier sitio. Los dos vienen encendidos
  y se pueden apagar en la configuración del servidor para que los jugadores tengan que ir
  a un mostrador.

Una llave de staff abre la configuración de cualquiera de ellos: precios, lo que vende la
tienda, el recargo.

El banco de los jugadores tiene dos de las tres: un **banquero**, un tercer tipo de
empleado con el verde y dorado del banco, y **`/bank`**. Los dos abren una ventana parecida
al banco de SkyBlock: el monedero y el banco arriba, y una fila de objetos en los que hacer
clic — un cofre para depositar todo, la mitad o una cantidad; un dispensador para retirar
todo, la mitad, el 20 % o una cantidad; un mapa con los últimos diez movimientos; una
antorcha con cómo funciona el banco en este servidor —, cada uno con un tooltip que dice qué
va a hacer. Apagar `/bank` en la configuración también cierra `/credits deposit` y
`/credits withdraw`, para que los jugadores tengan que ir a un banquero.

Cada jugador ve su monedero y su banco a la derecha de la pantalla, encima de la barra
lateral del marcador si la hay. Se puede ocultar en `config/economycore-client.toml`.

El banco puede pagar **interés**, desactivado por defecto: cada tantas horas reales, a todos
los que tengan algo guardado, por tramos como en SkyBlock (un 2 % por los primeros
10.000.000 y un 1 % hasta 15.000.000, salvo que la configuración diga otra cosa). Cada
crédito que paga es dinero nuevo.

## Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/exchange`, `/shop` | todos, salvo que estén apagados | Abre la casa de cambio o la tienda |
| `/bank` | todos, salvo que esté apagado | Abre tu banco |
| `/credits` | todos | Tu monedero y tu banco |
| `/credits pay <jugador> <cantidad>` | todos | Envía créditos a otro jugador |
| `/credits deposit <cantidad>` | todos, salvo que esté apagado | Guarda créditos del monedero en el banco; `all` para todo |
| `/credits withdraw <cantidad>` | todos, salvo que esté apagado | Saca créditos del banco al monedero; `all` para todo |
| `/credits balance <jugador>` | administradores del staff, operadores | El saldo de cualquiera, conectado o no |
| `/credits give / take / set` | administradores del staff, operadores | Cambia un saldo |
| `/economycore help` | todos | Todos los comandos que puedes usar, con una línea sobre cada uno |

`/economycore` también cubre los precios (`values`), el recargo de recompra (`spread`), lo que
pierden los jugadores del monedero al morir (`deathloss`, desactivado por defecto, y
`deathloss combat true` para que solo cuesten las muertes a manos de un mob o un jugador), la tienda
(`shop`), el banco del servidor (`bank`) y la lista de staff (`staff`).
`/economycore help` los muestra a quien pueda usarlos.

## Configuración

`config/economycore-server.toml`. Una copia en la carpeta `serverconfig` de un mundo la
reemplaza para ese mundo.

| Opción | Por defecto | Qué hace |
|---|---|---|
| `access.exchangeCommand` | `true` | Deja que los jugadores abran la casa de cambio con `/exchange` |
| `access.shopCommand` | `true` | Deja que los jugadores abran la tienda con `/shop` |
| `access.bankCommand` | `true` | Deja que los jugadores usen su banco desde cualquier sitio, con `/bank` y con `/credits deposit` y `withdraw` |
| `bank.alwaysOn` | `false` | Mantiene el banco del servidor encendido aunque ningún mod lo pida |
| `bank.incomeToBank` | `true` | Mete al banco lo que cobra el servidor, en vez de retirarlo |
| `players.welcomeBalance` | `0` | Créditos con los que empieza un jugador la primera vez que entra al mundo |
| `interest.enabled` | `false` | Paga interés por lo que los jugadores guardan en el banco |
| `interest.periodHours` | `31` | Cada cuántas horas reales se paga |
| `interest.tranches` | `["10000000:2", "15000000:1"]` | Cada tramo del saldo y lo que rinde, como `"hasta:porcentaje"`; por encima del último, nada |

## El banco del servidor y lo que cobra el servidor

Las ventas de la tienda, el recargo de recompra de la casa de cambio y cualquier comisión
que cobre otro mod siempre se les quitan a los jugadores: así salen créditos de
circulación. Si hay instalado un mod que paga desde un banco, como un casino, ese dinero
entra al banco del servidor. Si no, simplemente desaparece.

---

## Para desarrolladores de mods

Economy Core no sabe nada de los mods que lo usan: ellos dependen de él, nunca al revés.
Todo lo que un mod necesita está en el paquete `com.github.arrivedbog593.economycore.api`,
y solo ese paquete conserva su forma entre versiones.

- **`Credits`**: el saldo de un jugador, y lo que todavía puede gastar de él.
- **`PlayerCommitments`** (de `Credits.commitments()`): apunta créditos que un jugador
  prometió pero aún no pagó, como una apuesta o una puja, para que la tienda y la casa de
  cambio no los gasten dos veces.
- **`Settlement`**: mueve créditos entre cualquier número de cuentas a la vez. Primero se
  revisan todas, nada se mueve si una no puede pagar, y todo el movimiento queda registrado
  y se recupera tras un crash.
- **`Bank`**: la cuenta del propio servidor. Un mod que paga desde ella la enciende con
  `Bank.enable(modId)` al cargar, y declara lo que debe con `Bank.promise(...)` para que los
  retiros del staff nunca lo toquen.
- **`TransactionType`**: registra tus propios tipos de movimiento para el registro con
  `TransactionType.register(code, recoverable)` desde el constructor de tu mod.
- **`Staff`** y **`StaffRank`**: si un jugador es del staff, y si un objeto es una llave de
  staff.
- **`CreditFormat`**: los créditos escritos como los escribe el resto del mod.

Todo se usa solo desde el hilo del servidor.

### Cómo depender de él

```gradle
repositories {
    maven { url = "https://cursemaven.com" }
}

dependencies {
    implementation "curse.maven:economy-core-1775541:<fileId>"
}
```

Cambia `<fileId>` por el archivo de la versión que quieras: es el número al final de la
página de ese archivo en CurseForge.

## Compilar

```
./gradlew build
./gradlew publishToMavenLocal   # para mods que dependen de él, mientras se desarrollan
```

## Licencia

[MIT](LICENSE).
