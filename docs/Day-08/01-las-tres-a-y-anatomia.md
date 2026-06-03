# Las tres A (AAA) y la anatomía de un test

Todo buen test, sin importar la tecnología, tiene la misma estructura interna. Si entiendes esta estructura, sabes leer y escribir cualquier test.

## El patrón AAA: Arrange, Act, Assert

Un test cuenta una pequeña historia en tres actos:

1. **Arrange** (preparar): montar el escenario. Crear los datos, configurar los dobles, instanciar lo que vamos a probar.
2. **Act** (actuar): ejecutar **la** acción que queremos verificar. Idealmente **una sola** línea.
3. **Assert** (comprobar): verificar que el resultado es el esperado.

En español a veces se traduce como **"Preparar – Actuar – Comprobar"**, o el patrón equivalente de BDD **"Given – When – Then"** (Dado – Cuando – Entonces). Es lo mismo.

## Visto en un test real del curso

Mira el test del handler de creación, [PizzaCreateHandlerTest.java](../../demo/src/test/java/com/example/demo/pizza/create/PizzaCreateHandlerTest.java). Lo he anotado con las tres fases:

```java
@Test
void crea_pizza_con_ingredientes_resueltos_y_calcula_precio() {
    // ───── ARRANGE ─────  preparamos el escenario
    UUID ingredientId = UUID.randomUUID();
    Ingredient ingredient = Ingredient.create(ingredientId, "Tomate", new BigDecimal("0.50"));

    when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
            .thenReturn(Set.of(ingredient));

    var command = new PizzaCreate.Command(
            "Margherita", "Clásica", "http://img.png", Set.of(ingredientId));

    // ───── ACT ─────  ejecutamos LA acción bajo prueba
    PizzaCreate.Response response = handler.handle(command);

    // ───── ASSERT ─────  comprobamos el resultado
    assertNotNull(response.id());
    assertEquals(new BigDecimal("0.60"), response.price()); // 0.50 * 1.20
    verify(repository).add(any(Pizza.class));
}
```

- **Arrange**: creamos un ingrediente, le decimos al `lookup` (un mock) qué devolver, y construimos el comando.
- **Act**: una sola línea, `handler.handle(command)`. Es lo que estamos probando.
- **Assert**: el response trae un id, el precio aplica el margen del 20 %, y se llamó a `repository.add(...)`.

## Por qué separar las fases importa

No es decoración. La estructura AAA hace el test **legible** y **diagnosticable**:

- Si un test falla, sabes mirar la fase **Assert** para ver *qué* expectativa se rompió, y la fase **Arrange** para entender *en qué contexto*.
- Un test con una sola línea de **Act** prueba **una sola cosa**. Si la fase de Act tiene cinco llamadas, probablemente estás testeando cinco comportamientos en un solo test (mala señal, ver más abajo).

> Regla práctica: si te cuesta separar tu test en estas tres fases, el test probablemente está haciendo demasiado.

## Un test = un comportamiento

El segundo test del mismo fichero prueba **otra cosa distinta**: el camino de error.

```java
@Test
void lanza_si_un_ingrediente_no_existe() {
    // Arrange: el lookup lanzará excepción
    UUID ingredientId = UUID.randomUUID();
    when(lookup.findAll(Ingredient.class, Set.of(ingredientId)))
            .thenThrow(new EntityNotFoundException(Ingredient.class, ingredientId));

    var command = new PizzaCreate.Command("X", "Y", "http://z.png", Set.of(ingredientId));

    // Act + Assert: comprobamos que la excepción se propaga
    assertThrows(EntityNotFoundException.class, () -> handler.handle(command));
}
```

Fíjate: **no metimos el caso feliz y el caso de error en el mismo test**. Cada uno es un test independiente, con su propio nombre. Si mañana falla `lanza_si_un_ingrediente_no_existe`, el nombre ya te dice qué se rompió sin abrir el código.

## El nombre del test es documentación

En el curso usamos nombres en español, descriptivos, con el formato `qué_hace_bajo_qué_condición`:

- `crea_pizza_con_ingredientes_resueltos_y_calcula_precio`
- `lanza_si_un_ingrediente_no_existe`
- `get_lanza_si_no_existe`
- `toDomain_no_genera_eventos_al_hidratar`

Otra convención muy común es `metodo_estadoInicial_resultadoEsperado` o el formato BDD `should...When...`. Da igual cuál elijas: lo importante es que **al leer la lista de nombres de test entiendas qué garantiza la clase** sin abrir el cuerpo.

## Las propiedades FIRST de un buen test

Un acrónimo útil para recordar qué hace bueno a un test unitario:

| Letra | Propiedad | Qué significa |
|-------|-----------|---------------|
| **F** | *Fast* (rápido) | Milisegundos. Si tarda, no lo ejecutarás a menudo. |
| **I** | *Independent* (independiente) | No depende del orden ni del resultado de otros tests. |
| **R** | *Repeatable* (repetible) | Mismo resultado siempre, en tu máquina y en CI. Nada de fechas reales, aleatorios, o red. |
| **S** | *Self-validating* (auto-verificable) | Pasa o falla solo. No hay que mirar un log a mano para saberlo. |
| **T** | *Timely* (oportuno) | Se escribe junto al código, no "algún día". |

Los tests del handler cumplen FIRST de manual: son rápidos (no arrancan Spring ni BD), independientes (cada uno monta su escenario), repetibles, y se auto-verifican con `assertEquals` / `assertThrows`.

## Resumen

- Todo test sigue **Arrange – Act – Assert**.
- **Una acción** por test, **un comportamiento** por test.
- El **nombre** describe la garantía.
- Un buen test es **FIRST**.

Con esta anatomía clara, en el siguiente documento clasificamos los tests por su *alcance*: unitario, integración, regresión — y los ordenamos en la **pirámide**.
