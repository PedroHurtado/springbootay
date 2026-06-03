# Day-08 — Testing

Sesión dedicada a **por qué** y **dónde** testear, no solo a cómo. Toda la teoría se apoya en el código que ya hemos construido en el curso y en los dos tests existentes:

- [PizzaCreateHandlerTest](../../demo/src/test/java/com/example/demo/pizza/create/PizzaCreateHandlerTest.java) — unitario de handler con Mockito.
- [PizzaRepositoryTest](../../demo/src/test/java/com/example/demo/pizza/persistence/PizzaRepositoryTest.java) — integración con `@DataJpaTest`.

## Recorrido

### Bloque 1 — Fundamentos: por qué y qué

1. [00 · Por qué testear](00-por-que-testear.md) — el miedo, la confianza, el coste de no testear; por qué esta arquitectura es fácil de testear.
2. [01 · Las tres A (AAA) y la anatomía de un test](01-las-tres-a-y-anatomia.md) — Arrange/Act/Assert, un test = un comportamiento, propiedades FIRST.
3. [02 · Tipos de test y la pirámide](02-piramide-y-tipos-de-test.md) — unitario, integración, E2E, **regresión**; la pirámide y el antipatrón del cono de helado.

### Bloque 2 — Dónde poner cada test

4. [03 · Test unitarios: dónde y por qué](03-test-unitarios-donde-y-por-que.md) — dominio y handlers; qué capas no llevan unitario.
5. [04 · Test de integración: dónde y por qué](04-test-de-integracion-donde-y-por-que.md) — repositorio (`@DataJpaTest`), web (`@WebMvcTest`), app completa.

> ☕ **Descanso (20 min)**

### Bloque 3 — Herramientas

6. [05 · JUnit 5: las anotaciones](05-junit-anotaciones.md) — `@Test`, ciclo de vida, aserciones, parametrizados, `@ExtendWith`.
7. [06 · Mockito y los dobles de prueba](06-mockito-y-dobles-de-prueba.md) — mock, stub, fake (y dummy/spy); `@Mock`, `@InjectMocks`, `verify`.
8. [07 · TDD](07-tdd.md) — Red/Green/Refactor y por qué.
9. [08 · Qué aporta Spring en los tests](08-que-aporta-spring-en-los-tests.md) — contexto, slices, H2, `MockMvc`, `@MockitoBean`.

### Bloque 4 — Práctica

10. [09 · Práctica: escribe tus propios tests](09-practica.md) — ejercicios por niveles + reto de test de regresión.

## Conceptos que cubre la sesión

- Las tres **A**: Arrange · Act · Assert.
- Tipos de test: **unitario**, **integración**, **regresión** (+ E2E).
- La **pirámide** de tests.
- Anotaciones de **JUnit 5**.
- **Mockito** y los conceptos de **mock**, **stub** y **fake**.
- **TDD** y por qué.
- Qué aporta **Spring** y para qué sirve en los tests.
