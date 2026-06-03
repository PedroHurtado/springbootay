# Mockito y los dobles de prueba

Para testear una unidad **en aislamiento** necesitamos sustituir sus dependencias por versiones controladas. Esas sustituciones se llaman, genéricamente, **dobles de prueba** (*test doubles*, como un doble de cine). **Mockito** es la librería que usamos en Java para crearlos.

## El problema que resuelven

`PizzaCreate.Handler` depende de `IAddPizza` y `LookupResolver`. Si en el test usáramos las implementaciones reales, arrastraríamos la base de datos y todo el contexto. El test dejaría de ser unitario: sería lento, frágil y, si fallara, no sabríamos en qué capa está el problema.

La solución es darle al handler **dobles**: objetos falsos que imitan la interfaz pero cuyo comportamiento controlamos nosotros.

```
        PRODUCCIÓN                         TEST
   ┌──────────────────┐          ┌──────────────────┐
   │ PizzaCreate.Handler│        │ PizzaCreate.Handler│  ← lo real, bajo prueba
   └────────┬─────────┘          └────────┬─────────┘
            │ usa                          │ usa
   ┌────────▼─────────┐          ┌────────▼─────────┐
   │ PizzaRepository  │          │  mock de IAddPizza│  ← doble controlado
   │ (BD real)        │          │  (no toca BD)     │
   └──────────────────┘          └──────────────────┘
```

## La taxonomía de dobles (Gerard Meszaros / Martin Fowler)

No todos los dobles son iguales. Se diferencian por **cuánto comportamiento tienen** y **para qué se usan**. De menos a más sofisticado:

| Doble | Qué es | Para qué sirve |
|-------|--------|----------------|
| **Dummy** | Objeto que se pasa pero **nunca se usa**. Solo rellena un parámetro obligatorio. | Cumplir una firma. Su contenido da igual. |
| **Stub** | Devuelve **respuestas predefinidas** a las llamadas. No tiene lógica. | Controlar lo que el colaborador *devuelve* (estado de entrada). |
| **Spy** | Un objeto real (o parcial) que además **registra cómo fue llamado**. | Verificar interacciones sin sustituir todo el comportamiento. |
| **Mock** | Objeto con **expectativas programadas** sobre cómo debe ser llamado; la verificación de esas llamadas es parte del test. | Comprobar el *comportamiento* (que se llamó a X con Y). |
| **Fake** | Implementación **funcional pero simplificada**, no apta para producción. | Sustituir infraestructura pesada por una ligera. |

### Los tres  en detalle

**Stub** — *responde, no juzga.* Su trabajo es entregar datos para que el test avance por el camino que quieres. En el curso, esto es un stub aunque se cree con Mockito:

```java
// "cuando te pregunten por estos ingredientes, devuelve este conjunto"
when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
        .thenReturn(Set.of(ingredient));
```

No comprobamos *que* se llamó a `findAll`; solo lo usamos para alimentar al handler. Es un stub.

**Mock** — *verifica la interacción.* El foco está en comprobar que **se produjo la llamada esperada**:

```java
// tras ejecutar el handler, EXIGIMOS que se llamara a add con una Pizza
verify(repository).add(any(Pizza.class));
```

Aquí no nos importa qué devuelve `add` (es `void`): nos importa que **se llamó**. Eso es comportamiento de mock.

**Fake** — *implementación ligera de verdad.* El ejemplo clásico es un repositorio en memoria:

```java
class FakeAddPizza implements IAddPizza {
    private final Map<UUID, Pizza> store = new HashMap<>();
    @Override public void add(Pizza p) { store.put(p.getId(), p); }   // funciona de verdad
}
```

A diferencia del stub/mock, el fake **tiene lógica real** (guarda y recupera), solo que en memoria en vez de en BD. La base de datos **H2** que usa `@DataJpaTest` es, conceptualmente, un fake de la BD de producción: una BD real pero ligera y efímera.

> Confusión habitual: en la práctica, casi todo el mundo llama "mock" a cualquier objeto creado con Mockito. Técnicamente, si solo configuras `when(...).thenReturn(...)` y no verificas llamadas, lo estás usando como **stub**. La distinción importa para *pensar* qué estás probando: ¿el **estado** resultante (stub) o la **interacción** (mock)?

## Mockito en la práctica

### Las anotaciones

En [PizzaCreateHandlerTest](../../demo/src/test/java/com/example/demo/pizza/create/PizzaCreateHandlerTest.java):

```java
@ExtendWith(MockitoExtension.class)   // engancha Mockito a JUnit 5
class PizzaCreateHandlerTest {

    @Mock private IAddPizza repository;        // crea un doble de IAddPizza
    @Mock private LookupResolver lookup;       // crea un doble de LookupResolver
    @InjectMocks private PizzaCreate.Handler handler;  // instancia el Handler real
                                                       // e inyecta los @Mock de arriba
}
```

- `@ExtendWith(MockitoExtension.class)` — sin esto, los `@Mock` serían `null`. La extensión los inicializa antes de cada test.
- `@Mock` — crea el doble. Por defecto, todos sus métodos devuelven valores "vacíos" (`null`, `0`, `false`, colección vacía) hasta que los configures.
- `@InjectMocks` — crea el objeto **real** bajo prueba y le inyecta los mocks por su constructor. Como `Handler` recibe `(IAddPizza, LookupResolver)`, Mockito los encaja por tipo. (Aquí se ve por qué la **inyección por constructor** del curso facilita el testing.)

### Configurar comportamiento: `when(...).thenReturn(...)` / `thenThrow(...)`

```java
when(lookup.findAll(Ingredient.class, Set.of(ingredientId))).thenReturn(Set.of(ingredient));
when(lookup.findAll(Ingredient.class, Set.of(otroId))).thenThrow(new EntityNotFoundException(...));
```

`thenReturn` para el camino feliz, `thenThrow` para forzar errores difíciles de provocar de verdad. Esto último es una ventaja enorme: probar "¿qué hace el handler si el lookup falla?" sin tener que romper nada real.

### Verificar interacciones: `verify(...)`

```java
verify(repository).add(any(Pizza.class));        // se llamó exactamente 1 vez (por defecto)
verify(repository, times(1)).add(any());          // explícito
verify(repository, never()).remove(any());        // NO se llamó
verify(lookup).findAll(eq(Ingredient.class), anySet());
```

### Argument matchers

Cuando no quieres fijar el argumento exacto:

| Matcher | Significado |
|---------|-------------|
| `any(Pizza.class)` | Cualquier `Pizza`. |
| `eq(valor)` | Exactamente ese valor (necesario al mezclar matchers con literales). |
| `anyString()`, `anySet()`, `anyList()` | Cualquier valor de ese tipo. |
| `argThat(p -> ...)` | Un predicado a medida. |

> Regla: dentro de una llamada a `verify`/`when`, **o todos los argumentos son matchers o ninguno**. No puedes mezclar `verify(repo).add(realPizza, any())`. Si necesitas un literal junto a un matcher, envuélvelo en `eq(...)`.

## ¿Cuándo NO mockear?

- **No mockees lo que estás probando.** El dominio `Pizza` se testea con objetos reales, no con un mock de `Pizza`.
- **No mockees tipos que no posees** sin cuidado (APIs de terceros): mejor envolverlos en una interfaz propia y mockear esa.
- **No mockees value objects ni datos** (`BigDecimal`, `UUID`, records). Crea instancias reales.
- **No sobre-verifiques.** Verificar cada llamada interna acopla el test a la implementación: cualquier refactor lo rompe aunque el comportamiento sea correcto. Verifica solo las interacciones que **importan** (que se persistió, que no se borró...).

## Mockito vs. Spring: ¿`@Mock` o `@MockitoBean`?

Dos formas de mockear, para dos tipos de test distintos:

| | `@Mock` (Mockito puro) | `@MockitoBean` (Spring) |
|--|------------------------|----------------------|
| Contexto Spring | **No** arranca | Arranca (slice o completo) |
| Velocidad | Rapidísimo | Más lento |
| Uso | Test **unitario** del handler/dominio | Test de **integración** donde quieres sustituir un bean del contexto |

En un `@WebMvcTest`, por ejemplo, usarías `@MockitoBean private CommandDispatcher dispatcher;` para que Spring ponga un mock en el contexto. En el test unitario del handler usamos `@Mock` y nos ahorramos arrancar Spring. (Nota: `@MockitoBean` sustituye al antiguo `@MockBean`, deprecado en Spring Boot 3.4+.)

## Resumen

- **Dobles de prueba**: dummy (relleno), **stub** (responde datos), spy (registra), **mock** (verifica interacciones), **fake** (implementación ligera real, como H2).
- **Mockito** crea esos dobles: `@Mock` + `@InjectMocks`, configurados con `when().thenReturn()/thenThrow()` y verificados con `verify()`.
- Pregúntate qué pruebas: ¿el **estado** resultante (stub) o **que ocurrió una llamada** (mock)?
- No mockees lo que pruebas, ni los datos, ni de más.
