# Test de integración: dónde hacerlos y por qué

Un test unitario te dice "mi lógica es correcta **bajo las suposiciones que hice en los mocks**". Pero esas suposiciones pueden ser falsas. El test de integración existe para responder a la pregunta que el unitario no puede: **"¿las piezas encajan de verdad contra la infraestructura real?"**

## La grieta que cubre la integración

En el test del handler mockeamos `IAddPizza`:

```java
when(lookup.findAll(Ingredient.class, Set.of(ingredientId))).thenReturn(Set.of(ingredient));
// repository.add(...) es un mock: no persiste nada
```

Eso prueba que el handler **llama** a `add`. Pero **no** prueba que:

- El mapper traduzca bien `Pizza` → `PizzaJpa` (y al revés).
- Las relaciones JPA (`@ManyToMany` con ingredientes) se persistan correctamente.
- El `JOIN FETCH` de `findByIdWithIngredients` cargue los ingredientes sin `LazyInitializationException`.
- El esquema de la BD admita los datos.

Todo eso vive **entre** capas, en los contratos reales. Un mock, por definición, no lo verifica. Por eso necesitamos un test que use componentes reales.

## Dónde poner tests de integración en nuestra base de código

```
        ┌─────────────────────────────────────┐
HTTP →  │  Endpoint  ──►  @WebMvcTest           │  capa web real (JSON, HTTP, validación)
        ├─────────────────────────────────────┤
        │  Handler   ──►  (unitario, ya cubierto)│
        ├─────────────────────────────────────┤
        │  Repositorio + Mapper ──► @DataJpaTest │  persistencia real (H2/JPA) ★
        ├─────────────────────────────────────┤
        │  TODA la app ──►  @SpringBootTest      │  contexto completo / E2E
        └─────────────────────────────────────┘
```

### 1. El repositorio: el test de integración estrella ★

Es **el** punto donde la integración es imprescindible, porque es la frontera con la base de datos. En el curso, [PizzaRepositoryTest](../../demo/src/test/java/com/example/demo/pizza/persistence/PizzaRepositoryTest.java):

```java
@DataJpaTest
@Import({ PizzaRepository.class, PizzaMapper.class, IngredientMapper.class })
class PizzaRepositoryTest {

    @Autowired private PizzaRepository repository;
    @Autowired private IngredientJpaRepository ingredientJpa;
    @PersistenceContext private EntityManager em;

    @Test
    void add_y_get_devuelven_la_pizza_con_sus_ingredientes() {
        UUID ingredientId = UUID.randomUUID();
        ingredientJpa.saveAndFlush(new IngredientJpa(ingredientId, "Mozzarella", new BigDecimal("1.50")));

        Ingredient ingredient = Ingredient.create(ingredientId, "Mozzarella", new BigDecimal("1.50"));
        Pizza pizza = Pizza.create(UUID.randomUUID(), "Margherita", "Clásica",
                "http://img/margherita.png", Set.of(ingredient));

        repository.add(pizza);
        em.flush();   // fuerza el INSERT real
        em.clear();   // vacía la caché de 1er nivel → la siguiente lectura va a BD

        Pizza loaded = repository.get(pizza.getId());

        assertEquals("Margherita", loaded.getName());
        assertEquals(1, loaded.getIngredients().size());
        assertEquals(new BigDecimal("1.80"), loaded.getPrice()); // 1.50 * 1.20
    }
}
```

Tres detalles **que solo un test de integración revela**:

- **`em.flush()` + `em.clear()`**: sin esto, la "lectura" devolvería el objeto de la caché de primer nivel de Hibernate, no de la BD, y el test mentiría. El patrón flush+clear **obliga a ir a la base de datos de verdad**. Esto no tiene sentido en un test unitario porque ahí no hay `EntityManager`.
- **El ingrediente se persiste antes** (`saveAndFlush`) porque es el *target* de una FK: la integración te obliga a respetar las restricciones reales del esquema, cosa que un mock se saltaría alegremente.
- **`get_lanza_si_no_existe`** comprueba que `findByIdWithIngredients(...).orElseThrow(...)` realmente lanza `EntityNotFoundException` contra una BD vacía.

**Por qué aquí y no mockeado**: el repositorio **es** infraestructura. Su valor es precisamente integrar dominio + mapper + JPA + SQL. Mockear cualquiera de esas piezas vaciaría el test de sentido.

### 2. La capa web: `@WebMvcTest`

El endpoint hace cosas que solo existen "en integración con Spring MVC":

- Deserializar el JSON del body a `Command`.
- Aplicar `@Valid` y la Bean Validation (`@NotBlank`, `@NotEmpty`...).
- Devolver el código HTTP correcto (`201`, `400`, `404`...).
- Aplicar los `@ControllerAdvice` (`DomainExceptionAdvice`, `ValidationAdvice`).

Un test con `@WebMvcTest` arranca **solo la capa web** (no la BD), mockea el handler/dispatcher con `@MockitoBean`, y dispara peticiones con `MockMvc`:

```java
@WebMvcTest(PizzaCreate.Endpoint.class)
class PizzaCreateEndpointTest {

    @Autowired private MockMvc mvc;
    @MockitoBean private CommandDispatcher dispatcher;   // mock del bean

    @Test
    void devuelve_400_si_falta_el_nombre() throws Exception {
        mvc.perform(post("/api/pizzas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"description\": \"x\", \"url\": \"y\", \"ingredientIds\": [] }"))
            .andExpect(status().isBadRequest());
    }
}
```

**Por qué es integración**: aunque mockeamos el dispatcher, estamos integrando con el motor real de Spring MVC, Jackson y Bean Validation. Ese cableado es justo lo que probamos.

### 3. La aplicación completa: `@SpringBootTest`

Es el test de integración de **máximo alcance** (la cima de la pirámide). Arranca el contexto entero. En el curso ya existe [DemoApplicationTests](../../demo/src/test/java/com/example/demo/DemoApplicationTests.java):

```java
@SpringBootTest
class DemoApplicationTests {
    @Test
    void contextLoads() { }
}
```

Verifica que **todos los beans se resuelven y la app arranca**. Es la primera línea de defensa: si una inyección está rota o una configuración es inválida, falla aquí.

Si añades `@AutoConfigureMockMvc`, puedes hacer un E2E real: `POST /api/pizzas` → 201 → comprobar en BD que la pizza existe. Útil para confirmar el flujo completo, pero **pocos** (son lentos: arrancan todo).

## Cuándo SÍ y cuándo NO un test de integración

| Situación | ¿Integración? |
|-----------|---------------|
| Persistencia, mapeo JPA, consultas (`JOIN FETCH`) | **Sí** — `@DataJpaTest` |
| Serialización JSON, códigos HTTP, validación, `@ControllerAdvice` | **Sí** — `@WebMvcTest` |
| Que la app entera arranque y cablee bien | **Sí** — `@SpringBootTest` (uno o pocos) |
| Una regla de negocio del dominio | **No** — unitario, es más barato |
| La orquestación de un handler | **No** — unitario con mocks |

## El compromiso: realismo vs. velocidad

```
        realismo ▲                         ● @SpringBootTest (E2E)
                 │                    ●  @WebMvcTest
                 │              ● @DataJpaTest
                 │        ● unitario con mocks
                 │   ● dominio puro
                 └────────────────────────────────► coste / lentitud
```

El test de integración compra **realismo** pagando **velocidad y fragilidad**. Por eso la estrategia es: *toda la lógica que puedas, en unitarios baratos; reserva la integración para lo que de verdad solo se puede comprobar con infraestructura real* — principalmente la frontera con la BD y con la web.

## Resumen

- El test de integración cubre la **grieta que el mock deja**: los contratos reales entre piezas.
- En el curso, el sitio crítico es el **repositorio** (`@DataJpaTest`): mapper + JPA + SQL reales, con el patrón `flush()`/`clear()` para forzar ir a BD.
- La **capa web** se integra con `@WebMvcTest` (JSON, HTTP, validación).
- La **app completa** con `@SpringBootTest` (uno o pocos; arranca todo).
- Realismo y lentitud van de la mano: pocos arriba, muchos abajo.
