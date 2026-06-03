# Tipos de test y la pirámide

Los tests se clasifican por su **alcance**: cuánto código ejercitan a la vez. A más alcance, más realismo... pero también más lentitud y más fragilidad. Entender este compromiso es lo que nos lleva a la **pirámide de tests**.

## Test unitario

Prueba **una unidad de lógica en aislamiento**: una clase, un método, una regla de negocio. Todo lo que esa unidad necesita del exterior se sustituye por **dobles de prueba** (mocks, stubs...).

- **No** arranca Spring.
- **No** toca base de datos, red, ni ficheros.
- Se ejecuta en **milisegundos**.

Ejemplo en el curso: [PizzaCreateHandlerTest](../../demo/src/test/java/com/example/demo/pizza/create/PizzaCreateHandlerTest.java). Prueba el handler con `IAddPizza` y `LookupResolver` mockeados. El handler nunca habla con una BD real.

También es unitario probar el dominio puro:

```java
@Test
void el_precio_aplica_el_margen_del_20_por_ciento() {
    var ingrediente = Ingredient.create(UUID.randomUUID(), "Tomate", new BigDecimal("0.50"));
    var pizza = Pizza.create(UUID.randomUUID(), "Marinara", "Sin queso", "url",
                             Set.of(ingrediente));

    assertEquals(new BigDecimal("0.60"), pizza.getPrice());
}
```

`Pizza` no depende de nada externo, así que no necesita ni siquiera un mock. Es el test más barato que existe.

## Test de integración

Prueba que **varias piezas funcionan juntas** correctamente: que el contrato entre ellas es real, no solo el que asumimos en los mocks.

- Arranca **parte** del framework (un *slice* de Spring) o el contexto completo.
- Suele tocar **infraestructura real**: una base de datos (aunque sea en memoria como H2), el `EntityManager`, el mapeo JPA, el serializador JSON...
- Se ejecuta en **cientos de milisegundos o segundos**.

Ejemplo en el curso: [PizzaRepositoryTest](../../demo/src/test/java/com/example/demo/pizza/persistence/PizzaRepositoryTest.java). Usa `@DataJpaTest`, arranca H2 + JPA, persiste de verdad y vuelve a leer:

```java
@DataJpaTest
@Import({ PizzaRepository.class, PizzaMapper.class, IngredientMapper.class })
class PizzaRepositoryTest {

    @Test
    void add_y_get_devuelven_la_pizza_con_sus_ingredientes() {
        // ... persiste, em.flush(), em.clear(), y vuelve a leer de BD
    }
}
```

Aquí **no se mockea la base de datos**: el objetivo es justo comprobar que el mapper, el `JOIN FETCH` y el contrato del repositorio funcionan contra una BD real.

### ¿Por qué un mock no basta aquí?

En el test del handler, `IAddPizza` es un mock: le decimos "cuando te llamen a `add`, no hagas nada". Eso prueba la **lógica del handler**, pero **no** prueba que el `add` real persista bien. Esa garantía solo la da un test de integración contra la BD. Cada nivel responde una pregunta distinta:

- Unitario del handler → *¿el handler orquesta bien las llamadas?*
- Integración del repositorio → *¿la persistencia real funciona?*

## Test end-to-end (E2E)

Es un test de integración del **sistema completo**: entra por la puerta de verdad (una petición HTTP) y comprueba el resultado al final de toda la cadena.

- Arranca el contexto Spring **completo** (`@SpringBootTest`).
- Ejercita endpoint → dispatcher → handler → repositorio → BD.
- Es el más **lento** y el más **frágil** (muchas piezas pueden fallar), pero el más **realista**.

En el curso, el embrión de esto es [DemoApplicationTests](../../demo/src/test/java/com/example/demo/DemoApplicationTests.java):

```java
@SpringBootTest
class DemoApplicationTests {
    @Test
    void contextLoads() { }
}
```

Parece tonto —"no hace nada"— pero verifica algo muy valioso: **que toda la aplicación arranca**, que todos los beans se resuelven, que no hay dependencias rotas ni configuración inválida. Si este test falla, ninguno de los demás importa.

## Test de regresión

Atención: el test de regresión **no es un cuarto tipo por alcance**. Es un test definido por su **propósito** y su **origen**.

> Un test de regresión es el que escribes para que **un bug que ya ocurrió no vuelva a ocurrir**.

El flujo es:

1. Aparece un bug en producción (o en desarrollo). Por ejemplo: "el precio de las pizzas sin ingredientes se calcula como `0.00` en vez de lanzar error".
2. **Antes de arreglarlo**, escribes un test que reproduce el bug. Ese test **falla** (rojo): confirma que has entendido el problema.
3. Arreglas el código. El test pasa (verde).
4. **Dejas el test en la suite para siempre.**

Ese test es ahora de regresión: cada vez que alguien toque el cálculo de precio, comprobará que el bug no ha "regresado". Un test de regresión puede ser unitario, de integración o E2E — lo que lo define es **por qué existe**.

En el curso, `toDomain_no_genera_eventos_al_hidratar` tiene este sabor: protege una decisión sutil (que reconstruir una `Pizza` desde BD **no** debe emitir el evento `pizza.create`). Es exactamente el tipo de garantía que, sin un test, se rompe en cuanto alguien refactoriza el mapper sin recordar ese detalle.

```java
@Test
void toDomain_no_genera_eventos_al_hidratar() {
    // ... persiste y vuelve a cargar
    Pizza loaded = repository.get(pizza.getId());
    assertTrue(loaded.getEvents().isEmpty()); // hidratar ≠ crear
}
```

## La pirámide de tests

Si juntamos los tres niveles por alcance y los ordenamos por **cantidad recomendada**, sale la pirámide:

```
                    ╱╲
                   ╱  ╲        E2E / @SpringBootTest
                  ╱ E2E╲       · POCOS
                 ╱──────╲      · lentos, frágiles, realistas
                ╱        ╲
               ╱ Integ.   ╲    Integración / @DataJpaTest, @WebMvcTest
              ╱ DataJpaTest ╲   · ALGUNOS
             ╱──────────────╲   · medios; prueban contratos reales
            ╱                ╲
           ╱  Unitarios       ╲  Unitarios / Mockito
          ╱  (handler, dominio)╲  · MUCHOS
         ╱──────────────────────╲ · rapidísimos, baratos, estables
```

La idea, popularizada por Mike Cohn:

| Nivel | Cantidad | Velocidad | Coste de mantener | Qué responde |
|-------|----------|-----------|-------------------|--------------|
| Unitario | Muchos | ms | Bajo | ¿La lógica es correcta? |
| Integración | Algunos | cientos de ms | Medio | ¿Las piezas encajan de verdad? |
| E2E | Pocos | segundos | Alto | ¿El sistema entero funciona? |

### Por qué esa forma y no otra

- **La base ancha (unitarios)** es donde está el grueso de tus tests porque son rápidos y estables. Te dan feedback instantáneo y casi nunca fallan "por casualidad".
- **La cima estrecha (E2E)** son caros y frágiles: un E2E puede fallar por la red, por timing, por datos sucios... Tener cientos los hace insoportables de mantener. Unos pocos, bien elegidos, confirman el cableado.

### El antipatrón: el "cono de helado"

Cuando un equipo invierte la pirámide —pocos unitarios y muchos E2E— obtiene una suite lenta, frágil y que nadie quiere ejecutar:

```
   ╲                          ╱╲
    ╲   muchos E2E  ╱        ╱  ╲   pocos E2E
     ╲────────────╱        ╱────╲
      ╲          ╱        ╱      ╲  algunos integración
       ╲ pocos  ╱        ╱        ╲
        ╲unit. ╱        ╱  muchos  ╲ muchos unitarios
         ╲────╱        ╱ unitarios  ╲
          ╲╱          ╱──────────────╲
   CONO DE HELADO        PIRÁMIDE
     (mal)                 (bien)
```

## Por qué nuestra arquitectura empuja hacia la pirámide correcta

No es casualidad que en el curso los tests unitarios sean tan fáciles:

- Los **handlers** dependen de **interfaces segregadas** (`IAddPizza`, `IUpdatePizza`...), no de implementaciones. Mockearlas es trivial → muchos tests unitarios baratos.
- El **dominio** (`Pizza`, `Ingredient`) no conoce nada externo → se testea con `new`, sin mocks siquiera.
- La **persistencia** está concentrada en el repositorio → un único punto que necesita test de integración.

> El diseño determina la forma de tu pirámide. Una arquitectura acoplada te empuja al cono de helado; una desacoplada te deja construir la pirámide.

## Resumen

- **Unitario**: una unidad aislada, sin Spring ni BD, rapidísimo. → muchos.
- **Integración**: varias piezas juntas con infraestructura real (BD, JPA, JSON). → algunos.
- **E2E**: el sistema completo por HTTP. → pocos.
- **Regresión**: no es un nivel; es el test que escribes para que un bug no vuelva. Puede vivir en cualquier nivel.
- La **pirámide** ordena cantidad vs. coste; invertirla (cono de helado) es un antipatrón.

En los dos documentos siguientes bajamos al detalle de **dónde** poner cada uno en nuestra base de código y **por qué** ahí.
