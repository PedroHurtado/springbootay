# Por qué testear (y por qué nos importa en *esta* arquitectura)

Antes de escribir un solo `@Test`, hay que entender qué problema resuelven los tests. No testeamos "porque toca" ni para subir un número de cobertura. Testeamos para **poder cambiar el código sin miedo**.

## El miedo es el síntoma

Un proyecto sin tests envejece igual: llega un punto en el que nadie se atreve a tocar una clase porque "no se sabe qué se rompe". El código se congela. Cada cambio es una apuesta. Las funcionalidades nuevas se construyen *encima* del código viejo en lugar de *dentro*, porque modificar lo existente da pánico.

Los tests son lo contrario de ese miedo. Un test es una afirmación ejecutable:

> "Cuando se crea una pizza con un ingrediente de 0,50 €, el precio resultante es 0,60 €."

Mientras ese test pase, esa afirmación es cierta. Si mañana alguien rompe el cálculo del margen, el test falla **en segundos**, antes de llegar a producción, antes incluso de hacer commit. El test no evita el error: lo **detecta a tiempo**.

## Qué nos da realmente

| Beneficio | Qué significa en la práctica |
|-----------|------------------------------|
| **Confianza para refactorizar** | Puedes reescribir `PizzaRepository.get()` con un `JOIN FETCH` distinto y saber al instante si rompiste algo. |
| **Documentación viva** | El test `crea_pizza_con_ingredientes_resueltos_y_calcula_precio` explica *cómo* se usa el handler mejor que cualquier comentario. Y nunca miente, porque si mintiera, fallaría. |
| **Diseño más limpio** | Código difícil de testear suele ser código mal diseñado (acoplado, con dependencias ocultas). El test es un primer cliente que te obliga a separar responsabilidades. |
| **Detección temprana** | Un bug encontrado en un test unitario cuesta minutos. El mismo bug en producción cuesta horas, reputación y, a veces, dinero. |
| **Red de seguridad colectiva** | El equipo entero puede tocar el código porque la suite avisa. No dependes de que "el que lo escribió" se acuerde. |

## El coste de NO testear no es cero

Es tentador pensar "voy más rápido si no escribo tests". A corto plazo, sí. Pero el coste no desaparece: se traslada al futuro y crece con intereses.

```
Sin tests:                   Con tests:

  velocidad                    velocidad
     │                            │
     │\                           │
     │ \___                       │‾‾‾‾‾‾‾‾‾‾‾‾‾
     │     \____                  │
     │          \____             │
     └───────────────► tiempo     └───────────────► tiempo
```

Sin tests empiezas rápido y te frenas: cada cambio arrastra regresiones que hay que cazar a mano. Con tests pagas un coste inicial constante y mantienes la velocidad porque la red de seguridad absorbe el riesgo.

## Por qué *esta* arquitectura es especialmente fácil de testear

Durante el curso hemos construido una arquitectura que no es accidental. Cada decisión de diseño tiene una consecuencia directa en la testabilidad:

- **El dominio no conoce nada externo.** `Pizza` no sabe de Spring, ni de JPA, ni de HTTP. Se puede instanciar con `new` en un test y comprobar su comportamiento sin levantar nada.

- **Los handlers reciben interfaces segregadas.** `PizzaCreate.Handler` depende de `IAddPizza` y `LookupResolver`, no de implementaciones concretas. Eso permite sustituirlas por dobles de prueba (mocks) trivialmente.

- **La persistencia está aislada.** El repositorio es la única pieza que toca la base de datos, así que solo *ahí* necesitamos un test que arranque una BD.

Esta separación es la que hace que la **pirámide de tests** (que veremos en [02-piramide-y-tipos-de-test.md](02-piramide-y-tipos-de-test.md)) funcione: muchos tests rápidos abajo, pocos tests lentos arriba.

> Si testear tu código es difícil, normalmente el problema no es el test: es el diseño.

## Lo que veremos hoy

1. La anatomía de un test y el patrón **AAA** — [01](01-las-tres-a-y-anatomia.md)
2. Los **tipos de test** (unitario, integración, regresión) y la **pirámide** — [02](02-piramide-y-tipos-de-test.md)
3. **Dónde y por qué** hacer tests unitarios — [03](03-test-unitarios-donde-y-por-que.md)
4. **Dónde y por qué** hacer tests de integración — [04](04-test-de-integracion-donde-y-por-que.md)
5. Las **anotaciones de JUnit 5** — [05](05-junit-anotaciones.md)
6. **Mockito** y los **dobles de prueba** (mock, stub, fake, spy, dummy) — [06](06-mockito-y-dobles-de-prueba.md)
7. **TDD** y por qué — [07](07-tdd.md)
8. Qué **aporta Spring** en los tests — [08](08-que-aporta-spring-en-los-tests.md)
9. **Práctica**: escribiréis vosotros algunos tests — [09](09-practica.md)
