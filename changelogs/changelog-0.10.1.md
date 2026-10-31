# Economy Core 0.10.1

*NeoForge 1.21.1 · 2026-10-31*

> **No more test prices, and the exchange has one way in per door.** The mod no longer gives any item a value of its own, and the item commands that predate the exchange screen are gone.

## ⚠️ Important Notes

- Iron, gold, diamonds and emeralds no longer have a value unless your server gives them one. Prices set in game are kept
- `/credits convert` and `/credits redeem` are removed. Use the exchange instead: its block, its clerk, or `/exchange`

## 🐛 Bug Fixes

- **0.10.0 shipped with test prices.** A file used during development gave iron, gold, diamonds and emeralds, and their nuggets and blocks, a value at the exchange. Because it came from inside the mod, those prices could not be removed in game. It is gone: the exchange now starts empty, and every price is the server's
- **Turning `/exchange` off did not keep players at the counter.** `/credits convert` and `/credits redeem` traded items for credits from anywhere whatever the config said. They were written to try the economy out before the exchange had a screen, and now that it can be opened from anywhere they only got in the way of a server that wants it opened in person

## 📦 Dependencies

- No changes

---

<details>
<summary><b>🇪🇸 Leer en español</b></summary>

> **Adiós a los precios de prueba, y una sola entrada a la casa de cambio por cada puerta.** El mod ya no le da valor propio a ningún objeto, y los comandos de objetos anteriores a la pantalla de la casa de cambio ya no están.

## ⚠️ Notas Importantes

- El hierro, el oro, los diamantes y las esmeraldas ya no tienen valor salvo que tu servidor se lo dé. Los precios fijados dentro del juego se conservan
- Se quitan `/credits convert` y `/credits redeem`. Usa la casa de cambio: su bloque, su empleado o `/exchange`

## 🐛 Correcciones

- **La 0.10.0 traía precios de prueba.** Un archivo usado durante el desarrollo les daba valor en la casa de cambio al hierro, el oro, los diamantes y las esmeraldas, con sus pepitas y bloques. Como venía de dentro del mod, esos precios no se podían quitar en el juego. Ya no está: la casa de cambio empieza vacía y todos los precios son del servidor
- **Apagar `/exchange` no obligaba a ir al mostrador.** `/credits convert` y `/credits redeem` cambiaban objetos por créditos desde cualquier sitio, dijera lo que dijera la configuración. Se escribieron para probar la economía antes de que la casa de cambio tuviera pantalla, y ahora que se puede abrir desde cualquier sitio solo estorbaban a un servidor que quiere que se use en persona

## 📦 Dependencias

- Sin cambios

</details>
