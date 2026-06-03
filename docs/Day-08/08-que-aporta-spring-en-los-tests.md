# Qué aporta Spring en los tests

Hasta ahora, los tests unitarios (handler, dominio) **no necesitan Spring para nada**: instancias con `new` o con Mockito y listo. Y eso está bien — es lo deseable. Entonces, ¿para qué sirve el soporte de testing de Spring?

> Spring no te ayuda a testear lógica. Te ayuda a testear **integración con el framework**: el contenedor de beans, JPA, la capa web, la configuración. Justo lo que un test unitario *no* puede cubrir.

## El concepto central: el `ApplicationContext` de test

Spring, en ejecución normal, arranca un **contexto** de aplicación: descubre los `@Component`/`@Service`/`@Repository`, los instancia, resuelve sus inyecciones y aplica la configuración. En un test, Spring puede arrancar **ese mismo mecanismo** (o un trozo de él) para que pruebes tu código **tal como vivirá en producción**: con inyección real, configuración real, JPA real.

El precio es la **lentitud**: arrancar un contexto cuesta. Por eso Spring ofrece **slices**: arrancar **solo la parte del contexto** que necesitas.

```
   @SpringBootTest         →  TODO el contexto (lento, realista)
        ▲
        │  cada vez menos contexto, más rápido
        │
   @DataJpaTest            →  solo JPA + repositorios + BD
   @WebMvcTest             →  solo la capa web (controllers, JSON, validación)
        │
        ▼
   (sin Spring)            →  Mockito puro: handler, dominio
```

## En este proyecto: slices modularizados (Spring Boot 4)

Una particularidad de **Spring Boot 4**, que es la que usa el curso: los slices de test están **modularizados** en artefactos separados. No vienen todos en `spring-boot-starter-test`; cada slice trae su dependencia:

| Slice | Artefacto Maven (Boot 4) | Import |
|-------|--------------------------|--------|
| `@DataJpaTest` | `spring-boot-data-jpa-test` | `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest` |
| `@WebMvcTest` | `spring-boot-starter-webmvc-test` | `...web.servlet.autoconfigure.WebMvcTest` |
| `@SpringBootTest` | `spring-boot-starter-test` (núcleo) | `org.springframework.boot.test.context.SpringBootTest` |

Por eso en el `pom.xml` del curso ves, además de `spring-boot-starter-test`, las dependencias `spring-boot-data-jpa-test` y `spring-boot-starter-webmvc-test`. Si quisieras un slice de test y su starter no estuviera, el import no compilaría.

## `@SpringBootTest`: el contexto completo

Arranca **toda** la aplicación. Es lo que ves en [DemoApplicationTests](../../demo/src/test/java/com/example/demo/DemoApplicationTests.java):

```java
@SpringBootTest
class DemoApplicationTests {
    @Test
    void contextLoads() { }
}
```

**Qué aporta**: verifica que el contexto entero se levanta — que todos los beans se descubren e inyectan, que no hay ciclos ni dependencias rotas, que la configuración (perfiles, `@ConfigurationProperties`...) es válida. Es el test que, si falla, te dice "la app ni siquiera arranca".

Combinado con `@AutoConfigureMockMvc`, sirve para tests **E2E** de la pila completa. Como arranca todo, es **lento** → pocos.

## `@DataJpaTest`: el slice de persistencia

Es **el** aporte de Spring más usado en el curso. Arranca **solo** lo necesario para JPA: el `EntityManager`, los repositorios Spring Data, una BD en memoria (**H2**), y configura transacciones. **No** arranca controllers, ni seguridad, ni el resto de servicios.

Mira [PizzaRepositoryTest](../../demo/src/test/java/com/example/demo/pizza/persistence/PizzaRepositoryTest.java):

```java
@DataJpaTest
@Import({ PizzaRepository.class, PizzaMapper.class, IngredientMapper.class })
class PizzaRepositoryTest {

    @Autowired private PizzaRepository repository;
    @Autowired private IngredientJpaRepository ingredientJpa;
    @PersistenceContext private EntityManager em;
}
```

Qué te da Spring aquí, gratis:

- **Una BD H2 en memoria** auto-configurada. No tocas la BD de producción ni necesitas instalar nada.
- **`@Transactional` por defecto**: cada test corre en una transacción que se hace **rollback al terminar**. Los tests no se ensucian entre sí ni dejan basura → independencia (la "I" de FIRST) sin esfuerzo.
- **`@Autowired`**: inyecta los beans reales del slice, igual que en producción.
- **`@Import(...)`**: como `@DataJpaTest` solo registra los repositorios Spring Data, le decimos explícitamente que **añada al contexto** nuestras piezas (`PizzaRepository`, los mappers). Esto mantiene el slice mínimo: solo entra lo que pides.
- Acceso al `EntityManager` para el patrón `flush()`/`clear()` que fuerza ir a la BD real (ver [04](04-test-de-integracion-donde-y-por-que.md)).

**Qué prueba que un mock no puede**: el mapeo JPA real, las FKs, las consultas (`findByIdWithIngredients` con su `JOIN FETCH`), el `orElseThrow`. La realidad de la persistencia.

## `@WebMvcTest`: el slice de la capa web

Arranca **solo** la capa web: los `@RestController`, Jackson (JSON), la Bean Validation y los `@ControllerAdvice`. **No** arranca BD ni servicios — esos se **mockean** con `@MockitoBean`.

```java
@WebMvcTest(PizzaCreate.Endpoint.class)
class PizzaCreateEndpointTest {

    @Autowired private MockMvc mvc;                 // cliente HTTP simulado
    @MockitoBean private CommandDispatcher dispatcher;  // mock dentro del contexto

    @Test
    void devuelve_400_si_el_nombre_esta_en_blanco() throws Exception {
        mvc.perform(post("/api/pizzas")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"description\":\"d\",\"url\":\"u\",\"ingredientIds\":[\"...\"]}"))
            .andExpect(status().isBadRequest());
    }
}
```

Qué te da Spring aquí:

- **`MockMvc`**: simula peticiones HTTP **sin levantar un servidor real** (sin puerto, sin red). Rápido y determinista.
- Ejercita de verdad la **deserialización JSON**, la **validación** (`@Valid` + `@NotBlank`...) y los **`@ControllerAdvice`** (`ValidationAdvice`, `DomainExceptionAdvice`) → puedes comprobar que un input inválido devuelve `400` con el `ProblemDetail` correcto.

## `@MockitoBean` y `@MockitoSpyBean`: mocks dentro del contexto

Cuando Spring **sí** arranca, no puedes usar `@Mock` de Mockito a secas para sustituir un bean: necesitas que el mock **entre en el contexto** ocupando el lugar del bean real. Para eso:

- `@MockitoBean` — reemplaza (o añade) un bean del contexto por un mock de Mockito.
- `@MockitoSpyBean` — envuelve el bean real en un spy (mantiene su comportamiento y registra llamadas).

```java
@WebMvcTest(PizzaCreate.Endpoint.class)
class ... {
    @MockitoBean private CommandDispatcher dispatcher;  // el endpoint usará este mock
}
```

> Nota de versión: `@MockitoBean`/`@MockitoSpyBean` (paquete `org.springframework.test.context.bean.override.mockito`) son los actuales. Sustituyen a los antiguos `@MockBean`/`@SpyBean`, **deprecados** desde Spring Boot 3.4. En el curso (Boot 4) usamos los nuevos.

## Otras utilidades que aporta Spring en tests

| Herramienta | Para qué |
|-------------|----------|
| `@ActiveProfiles("test")` | Activa un perfil de configuración específico para el test (recuerda Day-07). |
| `@TestPropertySource` / `@DynamicPropertySource` | Sobrescribe propiedades solo para el test. |
| `@Sql("...")` | Ejecuta un script SQL antes/después del test (datos de prueba). |
| `TestRestTemplate` / `WebTestClient` | Cliente HTTP **real** contra el servidor levantado por `@SpringBootTest(webEnvironment = RANDOM_PORT)`. |
| Caché de contextos | Spring **reutiliza** el mismo contexto entre clases de test con idéntica configuración. Por eso conviene no inventar configuraciones distintas en cada test: cada variación = un contexto nuevo que arrancar. |

## La regla de oro: el slice más pequeño que sirva

```
¿Pruebas lógica de negocio?           → sin Spring (Mockito / new)        ← lo más rápido
¿Pruebas persistencia / JPA?          → @DataJpaTest
¿Pruebas serialización / HTTP / valid?→ @WebMvcTest
¿Pruebas que TODO arranca y cablea?   → @SpringBootTest (uno o pocos)     ← lo más lento
```

Cuanto más pequeño el slice, más rápido el test y más fácil localizar el fallo. Subir a `@SpringBootTest` "por comodidad" cuando bastaba `@DataJpaTest` es el camino al cono de helado.

## Resumen

- Spring **no** ayuda a testear lógica (eso es Mockito/`new`); ayuda a testear **integración con el framework**.
- Lo hace arrancando un **contexto**, total (`@SpringBootTest`) o en **slices** (`@DataJpaTest`, `@WebMvcTest`).
- En Boot 4 los slices están **modularizados** (artefactos `spring-boot-data-jpa-test`, `spring-boot-starter-webmvc-test`).
- Regalos clave: **H2 + rollback transaccional** en `@DataJpaTest`, **`MockMvc`** en `@WebMvcTest`, **`@MockitoBean`** para sustituir beans.
- Elige siempre **el slice más pequeño** que cubra lo que pruebas.
