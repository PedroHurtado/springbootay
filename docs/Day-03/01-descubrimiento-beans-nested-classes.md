# Descubrimiento de beans con clases anidadas

## El problema

Tres declaraciones aparentemente equivalentes, comportamientos distintos:

```java
// (1) NO registra el controlador
public class IngredientCreate {
    @RestController
    @RequestMapping("/api/ingredients")
    public class Controller { }
}

// (2) SÍ registra el controlador
@Configuration
public class IngredientCreate {
    @RestController
    @RequestMapping("/api/ingredients")
    public class Controller { }
}

// (3) SÍ registra el controlador
public class IngredientCreate {
    @RestController
    @RequestMapping("/api/ingredients")
    public static class Controller { }
}
```

La diferencia entre que Spring descubra el bean o lo ignore está en dos detalles: si la clase anidada es `static` y si la clase externa es una `@Configuration`.

## Inner class vs nested static class

En Java, una clase declarada dentro de otra puede ser de dos formas:

- **Inner class** (no `static`): mantiene una referencia implícita a la instancia de la clase externa (`this$0`). No se puede instanciar sin tener antes una instancia del enclosing class.
- **Nested static class**: es independiente. A efectos de instanciación equivale a una clase top-level. No tiene `this$0`.

```java
// Inner class — requiere instancia externa
IngredientCreate outer = new IngredientCreate();
IngredientCreate.Controller c = outer.new Controller();

// Nested static — independiente
IngredientCreate.Controller c = new IngredientCreate.Controller();
```

Spring trabaja con un contenedor de beans que crea instancias por sí mismo. Si una clase no puede instanciarse sin algo más, Spring no puede registrarla como bean autónomo.

## El mecanismo de descubrimiento

Cuando arranca, Spring lanza el `ClassPathBeanDefinitionScanner`, que recorre el classpath buscando clases con estereotipo (`@Component` y derivados: `@Service`, `@Repository`, `@Controller`, `@RestController`, `@Configuration`).

Antes de registrar un candidato, el scanner evalúa si es **instanciable de forma independiente**. Para inner classes no estáticas, el filtro las descarta: Spring no sabe cómo construir la instancia externa que necesitan.

Por eso el caso (1) no registra nada: `Controller` es inner no estática, requiere una instancia de `IngredientCreate`, y Spring no tiene forma de crearla porque `IngredientCreate` no es ningún bean.

## Por qué (2) sí funciona

Aquí cambia el camino de procesado. Cuando una clase está marcada con `@Configuration`, no la procesa el scanner puro, sino el `ConfigurationClassParser`.

Ese parser hace algo distinto: procesa **recursivamente las clases miembro** de la configuración. Si una nested class tiene estereotipo, la registra como configuración anidada adicional, resolviendo la dependencia del enclosing instance a través del bean ya creado para `IngredientCreate`.

Es decir, Spring primero crea el bean `IngredientCreate` (porque es `@Configuration`), y a partir de esa instancia construye la inner `Controller`. Funciona, pero es un efecto secundario poco intuitivo del procesado de configuraciones.

## Por qué (3) sí funciona

Una nested **static** class es instanciable sin nada externo. El scanner la trata como si fuera top-level: la encuentra, comprueba que tiene estereotipo, comprueba que es instanciable, y la registra. La clase externa `IngredientCreate` ni siquiera necesita ser un bean — actúa como un simple *namespace* a nivel de código fuente.

## Regla práctica

> Si una clase con estereotipo está anidada dentro de otra, hazla `static` o Spring no la registrará como bean (salvo el caso especial de estar dentro de una `@Configuration`).

## Aplicación a Vertical Slice

El patrón Vertical Slice agrupa en una sola unidad de código todo lo necesario para una operación: el endpoint HTTP, el comando o request, la lógica de aplicación y la respuesta. En Spring, una forma idiomática de expresar esto es usar la clase externa como *namespace del slice* y declarar todo lo de dentro como `static`.

```java
public class IngredientCreate {

    @RestController
    @RequestMapping("/api/ingredients")
    public static class Endpoint {

        private final Handler handler;

        public Endpoint(Handler handler) {
            this.handler = handler;
        }

        @PostMapping
        public Response create(@RequestBody Command command) {
            return handler.handle(command);
        }
    }

    public interface Handler {
        Response handle(Command command);
    }

    @Service
    public static class DefaultHandler implements Handler {

        @Override
        public Response handle(Command command) {
            // lógica de aplicación
            return new Response();
        }
    }

    public record Command() { }

    public record Response() { }
}
```

Puntos clave del esqueleto:

- `IngredientCreate` **no es un bean**, es solo el contenedor del slice. Sirve para localizar todo lo del caso de uso con un único `Ctrl+P` sobre `IngredientCreate`.
- `Endpoint` y `DefaultHandler` son `static`, por lo que el scanner los descubre sin problema.
- `Handler` es una **interfaz** que define el contrato de la lógica de aplicación. El `Endpoint` depende de la abstracción, no de la implementación. Spring inyecta automáticamente `DefaultHandler` porque es el único bean del contenedor que implementa `Handler`.
- Las interfaces anidadas son implícitamente `static` en Java — no hace falta el modificador.
- `Command` y `Response` son `record` anidados — no son beans, son tipos de datos del slice.
- Cada slice vive en su propio fichero. Si tenemos `IngredientUpdate`, `IngredientDelete`, etc., todos siguen la misma estructura interna.

## Nombres y colisiones de beans

Surge una duda razonable: si todos los slices llaman `Endpoint` (o `Controller`) a su clase interna, ¿no chocan los nombres de bean entre slices?

No, porque `AnnotationBeanNameGenerator` compone el nombre del bean para clases anidadas usando el simple name del enclosing más el de la nested:

```
IngredientCreate.Endpoint  →  bean name "ingredientCreate.Endpoint"
IngredientUpdate.Endpoint  →  bean name "ingredientUpdate.Endpoint"
IngredientDelete.Endpoint  →  bean name "ingredientDelete.Endpoint"
```

El generador detecta la anidación y compone el nombre con un punto separador, precisamente para evitar la colisión entre nested classes con el mismo simple name en distintos enclosings. Cada slice queda identificado de forma única sin que tengamos que pensar en nombres.
