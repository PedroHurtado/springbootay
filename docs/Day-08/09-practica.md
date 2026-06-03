# Práctica: escribe tus propios tests

Ahora os toca a vosotros. El objetivo no es la cantidad, sino aplicar bien lo que hemos visto: el patrón **AAA**, elegir el **nivel** adecuado (unitario vs. integración) y usar los **dobles** solo donde toca.

Tenéis dos tests ya hechos como referencia y plantilla:

- Unitario de handler con Mockito → [PizzaCreateHandlerTest](../../demo/src/test/java/com/example/demo/pizza/create/PizzaCreateHandlerTest.java)
- Integración de repositorio con `@DataJpaTest` → [PizzaRepositoryTest](../../demo/src/test/java/com/example/demo/pizza/persistence/PizzaRepositoryTest.java)

## Cómo ejecutar los tests

```bash
# todos
./mvnw test

# una sola clase
./mvnw test -Dtest=PizzaCreateHandlerTest
```

En el IDE: botón derecho sobre la clase o el método → *Run test*. El verde/rojo es vuestra brújula.

## Antes de escribir cada test, preguntaos

1. ¿Qué **comportamiento** concreto quiero blindar? (un test = un comportamiento)
2. ¿Es **lógica** (→ unitario) o **integración con infraestructura** (→ `@DataJpaTest`/`@WebMvcTest`)?
3. ¿Necesito **dobles**? Si la unidad tiene dependencias con efectos (BD, lookup), sí. Si es dominio puro, no.
4. ¿Sé escribir las tres fases **Arrange / Act / Assert** por separado?

---

## Nivel 1 — Dominio puro (unitario, sin mocks)

Sobre `Pizza` ([Pizza.java](../../demo/src/main/java/com/example/demo/pizza/domain/Pizza.java)). No necesitan ni Spring ni Mockito: `new` / fábricas y aserciones.

1. **El precio suma varios ingredientes y aplica el margen.** Crea una pizza con dos ingredientes (p. ej. 0,50 € y 1,50 €) y comprueba que `getPrice()` es `2.40` (`2.00 * 1.20`).
2. **Rechaza pizza sin ingredientes.** `assertThrows(InvalidPizzaException.class, ...)` al crear con `Set.of()`.
3. **Rechaza nombre en blanco** y **nombre de más de 100 caracteres** (pista: `@ParameterizedTest` con `@ValueSource`).
4. **`create` emite el evento `pizza.create`** pero **`update` emite `pizza.update`**: comprueba el contenido de `getEvents()` tras cada operación.
5. **`getIngredients()` es inmutable**: intentar modificar el `Set` devuelto debe fallar (`UnsupportedOperationException`).

## Nivel 2 — Handler (unitario, con Mockito)

Sobre algún handler que **no** tenga test todavía. Buen candidato: `PizzaUpdate.Handler` o `PizzaRemove.Handler`. Patrón: `@ExtendWith(MockitoExtension.class)`, `@Mock` las dependencias, `@InjectMocks` el handler.

6. **`PizzaUpdate.Handler` actualiza y persiste.** Stub: `repository.get(id)` devuelve una pizza existente y `lookup.findAll(...)` los ingredientes nuevos. Act: `handle(command)`. Assert: `verify(repository).update(...)` y que el nombre cambió.
7. **`PizzaUpdate.Handler` propaga si la pizza no existe.** Stub: `when(repository.get(id)).thenThrow(new EntityNotFoundException(...))`. Assert: `assertThrows`.
8. **`PizzaRemove.Handler` llama a `remove`.** Verifica la interacción con el repositorio.
9. (Reto) Comprueba con un **argument matcher** (`argThat(...)`) que la `Pizza` pasada a `update`/`add` tiene los datos esperados.

## Nivel 3 — Integración de repositorio (`@DataJpaTest`)

Sobre `IngredientRepository` (paralelo a `PizzaRepositoryTest`). Recuerda `@Import(...)` de repositorio + mapper, y el patrón `em.flush()` / `em.clear()`.

10. **`add` + `get` devuelven el mismo ingrediente** desde la BD real.
11. **`get` lanza `EntityNotFoundException`** con un id que no existe.
12. (Reto) **`getAll`** devuelve todos los ingredientes persistidos.

## Nivel 4 — Capa web (`@WebMvcTest`) — opcional / reto

Sobre `PizzaCreate.Endpoint`. `@MockitoBean` el `CommandDispatcher`, `MockMvc` para las peticiones.

13. **POST con body válido → 201** y el JSON de respuesta trae `id` y `price`.
14. **POST con `name` en blanco → 400** (lo rechaza Bean Validation antes de llegar al handler).

---

## Reto final — un test de regresión de verdad

1. Elige una regla sutil del dominio (por ejemplo: *"al hidratar una pizza desde BD no se emite el evento `pizza.create`"* — ya cubierta por `toDomain_no_genera_eventos_al_hidratar`, pero busca otra **no** cubierta).
2. Escribe el test que la blinda. Ejecútalo: debe estar **verde** (la regla ya funciona).
3. Ahora **rompe a propósito** el código de producción (cambia el margen a `1.30`, o quita una validación).
4. Ejecuta: tu test debe ponerse **rojo**. Eso demuestra que **de verdad** protege la regla.
5. Restaura el código. Verde otra vez. Ese test queda en la suite como **red de regresión**.

> Si al romper el código el test **sigue verde**, el test no estaba probando lo que creías. Es la lección más valiosa del día.

## Checklist de un buen test (revisión final)

- [ ] Tiene un **nombre** que describe qué garantiza.
- [ ] Se distinguen las fases **Arrange / Act / Assert**.
- [ ] Prueba **un solo comportamiento**.
- [ ] Está en el **nivel correcto** (no `@SpringBootTest` para probar un cálculo).
- [ ] Usa **dobles solo donde hay dependencias con efectos**; no mockea datos ni el dominio.
- [ ] Es **repetible**: sin fechas reales, sin aleatorios, sin orden entre tests.
- [ ] Lo has visto **fallar** al menos una vez (rompiendo el código) para confiar en él.
