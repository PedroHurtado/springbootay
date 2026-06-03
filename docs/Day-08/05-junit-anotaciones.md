# JUnit 5: las anotaciones

**JUnit** es el framework de tests estándar en Java. La versión que usa Spring Boot 3 / 4 es **JUnit 5** (también llamado *JUnit Jupiter*). Aquí están las anotaciones que vas a usar, qué hacen y cuándo.

> Importante: las anotaciones de JUnit 5 viven en el paquete `org.junit.jupiter.api.*`. Si ves imports de `org.junit.*` (sin `jupiter`), es JUnit 4 — antiguo, no lo usamos.

## El núcleo: `@Test`

Marca un método como caso de prueba. JUnit lo descubre y lo ejecuta. No lleva argumentos, no devuelve nada.

```java
import org.junit.jupiter.api.Test;

@Test
void el_precio_aplica_el_margen() {
    // arrange, act, assert
}
```

En JUnit 5 **no hace falta que los métodos ni la clase sean `public`** (a diferencia de JUnit 4). Por eso en el curso ves `class PizzaCreateHandlerTest` y `void crea_pizza...()` sin modificador.

## Ciclo de vida: preparar y limpiar

Cuando varios tests comparten preparación, se extrae a métodos de ciclo de vida:

| Anotación | Cuándo se ejecuta | Uso típico |
|-----------|-------------------|------------|
| `@BeforeEach` | **Antes de cada** `@Test` | Reiniciar el objeto bajo prueba, datos limpios por test. |
| `@AfterEach` | **Después de cada** `@Test` | Liberar recursos abiertos en el test. |
| `@BeforeAll` | **Una vez**, antes de todos | Setup caro y compartido (debe ser `static`). |
| `@AfterAll` | **Una vez**, al final | Cerrar lo abierto en `@BeforeAll` (debe ser `static`). |

```java
class PizzaTest {

    private Ingredient tomate;

    @BeforeEach
    void preparar() {
        // se ejecuta antes de CADA test → cada uno parte de un estado limpio
        tomate = Ingredient.create(UUID.randomUUID(), "Tomate", new BigDecimal("0.50"));
    }

    @Test
    void test_a() { /* usa 'tomate' recién creado */ }

    @Test
    void test_b() { /* usa SU PROPIO 'tomate', no el de test_a */ }
}
```

**Clave de la independencia (FIRST)**: JUnit crea **una instancia nueva de la clase de test por cada `@Test`** por defecto. Sumado a `@BeforeEach`, esto garantiza que los tests no se contaminan entre sí.

## Documentación y organización

| Anotación | Para qué |
|-----------|----------|
| `@DisplayName("...")` | Nombre legible en el informe ("Crea pizza y calcula precio") en vez del nombre del método. |
| `@Nested` | Agrupa tests en una clase interna, para organizar por escenario ("cuando el ingrediente existe" / "cuando no existe"). |
| `@Tag("lento")` | Etiqueta tests para ejecutarlos selectivamente (p. ej. excluir los lentos en local). |
| `@Disabled("motivo")` | Desactiva temporalmente un test. **Siempre con motivo**; un `@Disabled` sin explicación es deuda técnica. |

```java
@DisplayName("Creación de pizza")
class PizzaCreateTest {

    @Nested
    @DisplayName("cuando todos los ingredientes existen")
    class IngredientesValidos {
        @Test
        @DisplayName("calcula el precio con el margen del 20%")
        void calculaPrecio() { /* ... */ }
    }
}
```

## Las aserciones (`org.junit.jupiter.api.Assertions`)

El corazón de la fase **Assert**. Se suelen importar de forma estática (`import static ...assertEquals;`) para escribirlas sin prefijo, como en los tests del curso.

| Aserción | Comprueba |
|----------|-----------|
| `assertEquals(esperado, real)` | Igualdad (ojo: el **esperado va primero**). |
| `assertNotNull(x)` / `assertNull(x)` | Que algo no sea / sea `null`. |
| `assertTrue(cond)` / `assertFalse(cond)` | Una condición booleana. |
| `assertThrows(Ex.class, () -> ...)` | Que el código lance **esa** excepción. Devuelve la excepción para inspeccionarla. |
| `assertDoesNotThrow(() -> ...)` | Que **no** lance nada. |
| `assertAll(...)` | Agrupa varias aserciones; reporta **todas** las que fallan, no solo la primera. |
| `assertIterableEquals` / `assertArrayEquals` | Colecciones y arrays. |

Ejemplos sacados del estilo del curso:

```java
assertEquals(new BigDecimal("0.60"), response.price());   // 0.50 * 1.20
assertNotNull(response.id());
assertTrue(loaded.getEvents().isEmpty());
assertThrows(EntityNotFoundException.class, () -> handler.handle(command));
```

`assertThrows` devuelve la excepción, lo que permite afinar el assert:

```java
var ex = assertThrows(InvalidPizzaException.class,
        () -> Pizza.create(id, "X", "Y", "url", Set.of()));
assertEquals("La pizza necesita al menos un ingrediente", ex.getMessage());
```

### Cuidado con `assertEquals` y `BigDecimal`

`new BigDecimal("0.60")` y `new BigDecimal("0.6")` son **distintos** para `equals` (la escala importa). Por eso en el dominio se hace `setScale(2, RoundingMode.HALF_UP)` y en el test se espera exactamente `"0.60"`. Es un caso real donde el assert te obliga a ser preciso.

## Tests parametrizados

Cuando el mismo test debe correr con varios datos, evitas copiar-pegar con `@ParameterizedTest`:

```java
@ParameterizedTest
@ValueSource(strings = { "", "   " })
void rechaza_nombre_vacio_o_en_blanco(String nombreInvalido) {
    var ing = Ingredient.create(UUID.randomUUID(), "T", new BigDecimal("1"));
    assertThrows(InvalidPizzaException.class, () ->
        Pizza.create(UUID.randomUUID(), nombreInvalido, "desc", "url", Set.of(ing)));
}
```

Fuentes de datos habituales: `@ValueSource`, `@CsvSource`, `@EnumSource`, `@MethodSource`. Requiere `@ParameterizedTest` en vez de `@Test`.

## Integración con Mockito y con Spring

Dos anotaciones que ya has visto en los tests del curso y que conectan JUnit con otros frameworks:

- `@ExtendWith(MockitoExtension.class)` — engancha **Mockito** al ciclo de vida de JUnit (procesa `@Mock`, `@InjectMocks`). Lo vemos en [06](06-mockito-y-dobles-de-prueba.md).
- `@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest` — anotaciones **de Spring**, no de JUnit, que internamente usan `@ExtendWith(SpringExtension.class)` para arrancar un contexto. Lo vemos en [08](08-que-aporta-spring-en-los-tests.md).

`@ExtendWith` es el **mecanismo de extensión** de JUnit 5: así es como otros frameworks se enchufan al motor de tests.

## Tabla de referencia rápida

| Anotación | Origen | Función |
|-----------|--------|---------|
| `@Test` | JUnit | Marca un caso de prueba. |
| `@BeforeEach` / `@AfterEach` | JUnit | Setup/limpieza por test. |
| `@BeforeAll` / `@AfterAll` | JUnit | Setup/limpieza una vez (static). |
| `@DisplayName` | JUnit | Nombre legible. |
| `@Nested` | JUnit | Agrupa por escenario. |
| `@Disabled` | JUnit | Desactiva (con motivo). |
| `@ParameterizedTest` + `@ValueSource`... | JUnit | Mismo test, varios datos. |
| `@ExtendWith` | JUnit | Engancha extensiones (Mockito, Spring). |
| `@Mock` / `@InjectMocks` | Mockito | Dobles de prueba → doc [06](06-mockito-y-dobles-de-prueba.md). |
| `@DataJpaTest` / `@WebMvcTest` / `@SpringBootTest` | Spring | Slices de contexto → doc [08](08-que-aporta-spring-en-los-tests.md). |

## Resumen

- `@Test` marca el caso; las aserciones (`assertEquals`, `assertThrows`...) son la fase Assert.
- `@BeforeEach` prepara un estado limpio por test → independencia.
- `@DisplayName`/`@Nested` mejoran la legibilidad; `@ParameterizedTest` evita duplicar.
- `@ExtendWith` es la puerta por la que Mockito y Spring se integran con JUnit.
