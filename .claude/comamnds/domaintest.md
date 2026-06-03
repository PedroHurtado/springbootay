---
description: Genera tests unitarios con 100% cobertura de líneas y ramas para una clase de dominio
argument-hint: [ruta/a/ClaseDominio.java]
---

Lee la clase de dominio en `$ARGUMENTS`.

Vas a crear tests unitarios JUnit que cubran el 100% de cobertura de líneas y el 100% de cobertura de ramas de esa clase.

Reglas:
- Respeta en la carpeta de test la MISMA estructura de paquetes que tiene el código fuente (src/main/java/... → src/test/java/... con idéntico package).
- No pierdas tiempo explorando la estructura del proyecto: deriva la ruta de test directamente desde la ruta del fuente.
- Antes de escribir una sola línea, presenta la lista de tests que vas a hacer y qué prueba cada uno (un test por cada rama/condición/excepción/camino). Para cada test indica: nombre, qué ejercita y qué rama o línea cubre.
- ESPÉRATE a mi aprobación explícita. No escribas ningún test hasta que apruebe.
- Una vez aprobados, escribe los tests siguiendo la guía de estilos del proyecto.
- En los commits no añadas líneas de co-autoría ni "Generated with Claude Code" ni "Co-Authored-By".