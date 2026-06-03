# TDD: Test-Driven Development

**TDD** (Desarrollo Guiado por Tests) es una forma de trabajar en la que **el test se escribe *antes* que el código de producción**. No es una técnica de testing: es una técnica de **diseño**. Los tests son un efecto secundario; el objetivo real es dejarte guiar por ellos para escribir mejor código.

## El ciclo Red – Green – Refactor

```
        ┌─────────────────────────────────────────┐
        │                                          │
        ▼                                          │
   ╔═════════╗      ╔═════════╗      ╔═══════════╗ │
   ║  RED    ║ ───► ║  GREEN  ║ ───► ║ REFACTOR  ║─┘
   ║ (rojo)  ║      ║ (verde) ║      ║           ║
   ╚═════════╝      ╚═════════╝      ╚═══════════╝
 escribe un test  haz que pase con  limpia el código
 que falla        lo MÍNIMO         sin romper el test
```

1. **RED** — Escribe un test para un comportamiento que **aún no existe**. Ejecuta: **falla** (rojo). Esto demuestra que el test *de verdad* prueba algo (un test que pasa antes de escribir el código no prueba nada).
2. **GREEN** — Escribe el **mínimo** código necesario para que el test pase. No el código "bonito": el mínimo. Aunque sea feo. Lo importante es llegar a verde rápido.
3. **REFACTOR** — Ahora que tienes la red de seguridad en verde, **mejora el diseño** del código (y del test) sin cambiar el comportamiento. El test garantiza que no rompes nada al limpiar.

Y vuelta a empezar con el siguiente comportamiento. Ciclos cortos, de minutos.

## TDD aplicado a una regla del curso

Imagina que la regla "el precio de la pizza lleva un 20 % de margen" **aún no estuviera implementada**. Con TDD:

### 🔴 RED — escribo el test primero

```java
@Test
void el_precio_aplica_el_margen_del_20_por_ciento() {
    var ing = Ingredient.create(UUID.randomUUID(), "Tomate", new BigDecimal("0.50"));
    var pizza = Pizza.create(UUID.randomUUID(), "Marinara", "Sin queso", "url", Set.of(ing));

    assertEquals(new BigDecimal("0.60"), pizza.getPrice());  // 0.50 * 1.20
}
```

Ejecuto. **Falla**: `getPrice()` todavía no existe o devuelve otra cosa. Perfecto: el test es válido.

### 🟢 GREEN — el mínimo para pasar

```java
public BigDecimal getPrice() {
    BigDecimal cost = ingredients.stream()
            .map(Ingredient::getCost)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return cost.multiply(new BigDecimal("1.20")).setScale(2, RoundingMode.HALF_UP);
}
```

Ejecuto. **Verde.** El comportamiento está probado.

### 🔵 REFACTOR — limpio sin miedo

Extraigo el literal mágico a una constante con nombre, como está hoy en el código real:

```java
private static final BigDecimal PROFIT_MARGIN = new BigDecimal("1.20");
// ...
return cost.multiply(PROFIT_MARGIN).setScale(2, RoundingMode.HALF_UP);
```

Vuelvo a ejecutar el test: sigue **verde**. La refactorización fue segura porque el test me cubría.

Luego añadiría el siguiente test (`rechaza_pizza_sin_ingredientes`) y repetiría el ciclo.

## Por qué TDD (las razones de fondo)

No se trata de dogma. TDD aporta cosas concretas:

| Razón | Explicación |
|-------|-------------|
| **Diseño guiado por el uso** | Escribir el test primero te obliga a usar tu propia API *antes* de implementarla. Si es incómoda de testear, es incómoda de usar → la rediseñas antes de escribir nada. |
| **Solo el código necesario** | Como solo escribes lo mínimo para pasar el test, no añades funcionalidad especulativa ("por si acaso") que nadie pidió (YAGNI). |
| **Cobertura por construcción** | Cada línea de producción existe porque un test la exigió. La cobertura alta no es un objetivo: es una consecuencia. |
| **Feedback inmediato** | Ciclos de minutos. Si algo se rompe, fue en los últimos 3 minutos de trabajo, no hace 2 días. |
| **Refactor sin miedo** | El paso Refactor es parte del ciclo, no un lujo "para cuando haya tiempo". La red de seguridad ya está. |
| **Documentación viva** | Acabas con una suite de tests que describe exactamente qué hace el código y por qué. |

## El valor del paso "RED" (que la gente se salta)

Ver el test **fallar primero** es importante y se subestima. Confirma dos cosas:

1. Que el test **realmente prueba** el comportamiento (no es un test que pasa siempre por error).
2. Que **falla por la razón correcta** (el comportamiento no existe), no por un fallo de compilación tonto o un typo en el assert.

Un test que escribes y pasa a la primera, sin haberlo visto fallar nunca, es sospechoso: ¿está probando lo que crees?

## TDD no es la única forma válida

Seamos honestos: **no todo el mundo hace TDD estricto, y está bien.** Hay enfoques intermedios perfectamente razonables:

- **Test-first** en la lógica crítica (dominio, cálculos), **test-after** en el cableado.
- Escribir el código y los tests "casi a la vez", en la misma sesión, antes del commit.

Lo que **no** es negociable es que la lógica importante **acabe cubierta por tests** antes de darla por terminada. TDD es una disciplina excelente para conseguirlo; si te funciona otra, perfecto, mientras el resultado sea el mismo: código probado.

## Errores típicos al empezar con TDD

- **Escribir tests gigantes**: el test del primer ciclo debe ser diminuto. Un comportamiento.
- **Saltarse el refactor**: quedarse en verde con código feo acumula deuda. El refactor es parte del ciclo.
- **Hacer TDD del cableado**: no tiene sentido TDD-ear un getter o el endpoint. TDD brilla donde hay **lógica y decisiones**.
- **No ver el rojo**: escribir test + código a la vez y ejecutar solo al final. Pierdes la garantía del paso RED.

## Relación con el resto del día

TDD usa exactamente las herramientas de los documentos anteriores: el patrón **AAA** ([01](01-las-tres-a-y-anatomia.md)) para cada test, las anotaciones de **JUnit** ([05](05-junit-anotaciones.md)), y **Mockito** ([06](06-mockito-y-dobles-de-prueba.md)) cuando la unidad bajo prueba tiene dependencias. La diferencia es solo el **orden**: primero el test, luego el código.

## Resumen

- TDD = ciclo **Red → Green → Refactor**, en pasos de minutos.
- Es una técnica de **diseño**, no solo de testing: el test guía la API y limita el código al necesario.
- El paso **RED** valida que el test prueba de verdad; el **REFACTOR** es obligatorio, no opcional.
- No es la única vía, pero el resultado innegociable es: **la lógica importante acaba con tests**.
