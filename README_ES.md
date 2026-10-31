# Economy Core

Un mod de [NeoForge](https://neoforged.net/) 1.21.1 que le da a un servidor una economía en
créditos, y una base sobre la que otros mods pueden construir: casinos, casas de subastas,
bazares.

[English](README.md)

> **Beta.** Todo lo de abajo funciona y se ha jugado en un servidor dedicado, pero puede
> cambiar antes de la 1.0. Haz una copia de tu mundo antes de actualizar.

---

## Qué añade

- **Cuentas personales en créditos** para cada jugador, guardadas con el mundo. Cada
  movimiento queda en un registro, y lo que un crash dejó a medias se repone al arrancar.
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

## Comandos

| Comando | Quién | Qué hace |
|---|---|---|
| `/exchange`, `/shop` | todos, salvo que estén apagados | Abre la casa de cambio o la tienda |
| `/credits` | todos | Tu saldo |
| `/credits pay <jugador> <cantidad>` | todos | Envía créditos a otro jugador |
| `/credits convert` | todos | Convierte en créditos el objeto que tienes en la mano |
| `/credits redeem <objeto> [cantidad]` | todos | Cambia créditos por objetos |
| `/credits balance <jugador>` | administradores del staff, operadores | El saldo de cualquiera, conectado o no |
| `/credits give / take / set` | administradores del staff, operadores | Cambia un saldo |
| `/economycore help` | todos | Todos los comandos que puedes usar, con una línea sobre cada uno |

`/economycore` también cubre los precios (`values`), el recargo de recompra (`spread`), la
tienda (`shop`), el banco del servidor (`bank`) y la lista de staff (`staff`).
`/economycore help` los muestra a quien pueda usarlos.

## Configuración

`config/economycore-server.toml`. Una copia en la carpeta `serverconfig` de un mundo la
reemplaza para ese mundo.

| Opción | Por defecto | Qué hace |
|---|---|---|
| `access.exchangeCommand` | `true` | Deja que los jugadores abran la casa de cambio con `/exchange` |
| `access.shopCommand` | `true` | Deja que los jugadores abran la tienda con `/shop` |
| `bank.alwaysOn` | `false` | Mantiene el banco del servidor encendido aunque ningún mod lo pida |
| `bank.incomeToBank` | `true` | Mete al banco lo que cobra el servidor, en vez de retirarlo |

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
    implementation "curse.maven:economy-core-<projectId>:<fileId>"
}
```

## Compilar

```
./gradlew build
./gradlew publishToMavenLocal   # para mods que dependen de él, mientras se desarrollan
```

## Licencia

[MIT](LICENSE).
