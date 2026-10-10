# momentosHARDCORE (Fabric 1.21.1)

Item **Ruleta** (pestaña Herramientas, o `/give @s momentoshardcore:ruleta`).
Clic derecho abre el menú; pasa el cursor sobre un icono y crece. Clic de nuevo en el icono = desactivar.

Permisos: OP nivel 2, o cualquiera en mundo de un jugador.

## Descargar el .jar desde GitHub
1. Crea un repositorio y sube todo este contenido (incluida la carpeta `.github`).
2. En la pestaña **Actions** se compila solo (~3-5 min).
3. El .jar queda en **Releases** (a la derecha del repo) y también como artifact de la ejecución.

## Compilar local
Con Gradle 8.10+ y Java 21: `gradle build` -> `build/libs/momentoshardcore-1.0.0.jar`

Valores ajustables (daño, radio de teletransporte, cantidades): arriba de `EventManager.java`.
