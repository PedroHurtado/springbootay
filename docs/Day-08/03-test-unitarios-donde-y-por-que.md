# Test unitarios: dónde hacerlos y por qué

La pregunta clave de un test unitario no es "¿cómo lo escribo?" sino **"¿qué unidad merece la pena testear en aislamiento?"**. La respuesta depende de la arquitectura. Vamos a recorrer nuestras capas y decidir, en cada una, si lleva test unitario y por qué.

## Regla general

> Pon un test unitario donde haya **lógica de decisión propia**: un cálculo, una validación, una orquestación, una regla de negocio. No pongas test unitario donde solo hay **delegación o cableado** (eso lo cubre la integración).

## Capa por capa

```
        ┌─────────────────────────┐
HTTP →  │  Endpoint (@RestController)   → cableado, NO unitario aislado
        ├─────────────────────────┤
        │  Handler (@Service)            → ORQUESTACIÓN → SÍ, unitario ★
        ├─────────────────────────┤
        │  Dominio (Pizza, Ingredient)   → REGLAS DE NEGOCIO → SÍ, unitario ★★
        ├─────────────────────────┤
        │  Repositorio + Mapper          → infraestructura → NO unitario (va integración)
        └─────────────────────────┘
```

### 1. El dominio: el mejor candidato a test unitario ★★

`Pizza` ([Pizza.java](../../demo/src/main/java/com/example/demo/pizza/domain/Pizza.java)) contiene **reglas de negocio puras**:

- El precio = coste de ingredientes × 1,20, redondeado a 2 decimales.
- Una pizza necesita **al menos un ingrediente** (si no, `InvalidPizzaException`).
- El nombre es obligatorio y ≤ 100 caracteres.
- `create` emite el evento `pizza.create`; el constructor protegido (hidratación) **no**.

Cada una de esas reglas es una afirmación testeable sin tocar nada externo:

```java
@Test
void rechaza_pizza_sin_ingredientes() {
    assertThrows(InvalidPizzaException.class, () ->
        Pizza.create(UUID.randomUUID(), "X", "Y", "url", Set.of()));
}

@Test
void create_emite_el_evento_pizza_create() {
    var ing = Ingredient.create(UUID.randomUUID(), "Tomate", new BigDecimal("0.50"));
    var pizza = Pizza.create(UUID.randomUUID(), "Marinara", "Sin queso", "url", Set.of(ing));

    assertEquals(1, pizza.getEvents().size());
}
```

**Por qué aquí**: el dominio es el corazón de la aplicación. Si la regla del 20 % de margen está mal, da igual que el endpoint, la BD y el JSON sean perfectos: el negocio está roto. Y como el dominio no depende de nada, el test es **trivial y rapidísimo** (no necesita ni mocks). Es el mejor retorno de inversión de toda la suite.

### 2. El handler: orquestación → test unitario ★

El handler ([PizzaCreate.Handler](../../demo/src/main/java/com/example/demo/pizza/create/PizzaCreate.java)) **no contiene reglas de negocio**, pero sí una **lógica de orquestación**:

1. Resolver los ingredientes vía `lookup.findAll(...)`.
2. Crear la `Pizza` con esos ingredientes.
3. Llamar a `repository.add(pizza)`.
4. Devolver el `Response` con id y precio.

Eso es una secuencia de decisiones que **puede romperse**: ¿y si olvidamos llamar a `add`? ¿Y si no propagamos la excepción cuando un ingrediente no existe? El test unitario lo blinda, **mockeando** las dependencias:

```java
@ExtendWith(MockitoExtension.class)
class PizzaCreateHandlerTest {
    @Mock private IAddPizza repository;
    @Mock private LookupResolver lookup;
    @InjectMocks private PizzaCreate.Handler handler;
    // ...
}
```

**Por qué se mockea aquí**: queremos probar **solo la lógica del handler**, no la persistencia ni el lookup reales. Sustituyendo `IAddPizza` y `LookupResolver` por mocks:

- El test es rápido (sin BD).
- Aislamos la causa: si falla, el problema está **en el handler**, no en otra capa.
- Podemos forzar escenarios difíciles de provocar de verdad, como "el ingrediente no existe" (`thenThrow(...)`).

Aquí es donde la **inyección por interfaz segregada** del curso paga: como el handler depende de `IAddPizza` (no de `PizzaRepository`), el mock es de una interfaz pequeñísima.

### 3. El endpoint: normalmente NO lleva unitario aislado

`PizzaCreate.Endpoint` solo hace una cosa: recibir el `Command`, validarlo (`@Valid`), pasárselo al `dispatcher` y envolver el resultado en un `201 Created`. Es **cableado**, casi sin lógica propia.

Testear eso aislado (mockeando el dispatcher) aporta poco: estarías comprobando que Spring MVC mapea bien, lo cual ya hace Spring. Lo que sí merece la pena es un test de **integración web** (`@WebMvcTest`) que verifique la serialización, los códigos HTTP y la validación — eso va en el documento de integración ([04](04-test-de-integracion-donde-y-por-que.md)).

### 4. El repositorio y el mapper: NO unitario

`PizzaRepository` delega en Spring Data y en el mapper; el mapper traduce dominio ↔ JPA. Mockear JPA para "testear" esto sería absurdo: estarías probando tus propios mocks, no la realidad. **Su única prueba útil es de integración contra una BD real.**

## Tabla resumen: ¿unitario sí o no?

| Capa | ¿Test unitario? | Por qué |
|------|-----------------|---------|
| **Dominio** (`Pizza`, `Ingredient`) | **Sí, prioritario** | Reglas de negocio puras, sin dependencias. Máximo valor, mínimo coste. |
| **Handler** (`@Service`) | **Sí** | Orquestación con decisiones; se mockean las interfaces que recibe. |
| **Endpoint** (`@RestController`) | No (aislado) | Solo cablea; mejor `@WebMvcTest` de integración. |
| **Repositorio / Mapper** | No | Infraestructura; mockear la BD no prueba nada. Va integración. |

## El error típico: testear lo trivial y dejar lo importante

Un antipatrón frecuente es escribir tests de getters/setters o de código sin lógica, mientras la regla de negocio crítica se queda sin cubrir. Pregúntate siempre:

> "Si este código tuviera un bug, ¿el negocio se vería afectado?" Si la respuesta es no, probablemente no necesita test unitario.

Testear `getName()` no aporta. Testear `getPrice()` —que aplica el margen y redondea— **sí**, porque ahí hay una decisión que puede estar mal.

## Resumen

- Test unitario donde hay **lógica de decisión**: **dominio** (prioritario) y **handlers** (orquestación).
- **No** donde solo hay **cableado** (endpoint) o **infraestructura** (repositorio, mapper).
- En el handler se **mockean** las dependencias para aislar su lógica; el dominio ni eso necesita.
- La arquitectura del curso (interfaces segregadas, dominio puro) hace que estos tests sean baratos → puedes tener muchos.
