# Configuración tipada y validada: `@ConfigurationProperties`

Inyectar `@Value("${...}")` suelto por todas partes funciona, pero ensucia y no valida nada. El enfoque limpio es agrupar la configuración de cada microdominio en un POJO inmutable y validado. Encaja con el vertical slice: **cada slice tiene su propio bloque de configuración tipado**.

## El POJO de configuración

Usamos un `record` para que la configuración sea inmutable y autodocumentada:

```java
package com.example.pizzeria.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "pizzeria.catalog")
public record CatalogProperties(

        @Min(1) @Max(20)
        int maxIngredientsPerPizza,

        @NotBlank
        String defaultCategory
) {}
```

El `prefix` enlaza con el bloque del YAML:

```yaml
pizzeria:
  catalog:
    max-ingredients-per-pizza: 12
    default-category: clasicas
```

## Activación

Con `@ConfigurationPropertiesScan` en la clase principal, Spring detecta todos los `@ConfigurationProperties` del proyecto automáticamente:

```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class PizzeriaApplication {
    public static void main(String[] args) {
        SpringApplication.run(PizzeriaApplication.class, args);
    }
}
```

## Uso en un handler

El handler del slice recibe el objeto tipado por inyección, no strings sueltos:

```java
@Service
public class PizzaCreateHandler implements CommandHandler<PizzaCreate, UUID> {

    private final IAddPizza repository;
    private final CatalogProperties properties;

    public PizzaCreateHandler(IAddPizza repository, CatalogProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public UUID handle(PizzaCreate command) {
        if (command.ingredientIds().size() > properties.maxIngredientsPerPizza()) {
            throw new TooManyIngredientsException(properties.maxIngredientsPerPizza());
        }
        // ...
    }
}
```

## Las dos ventajas que importan

### Fail-fast

Con `@Validated`, si en producción falta `defaultCategory` o `maxIngredientsPerPizza` viene a 0, la aplicación **no arranca** y te lo dice en el log de inicio. Es infinitamente mejor que descubrir el fallo en runtime con un `NullPointerException` tres horas después del despliegue.

### Tipado e inmutable

Usar `record` da configuración inmutable y autodocumentada. El handler recibe un objeto tipado con métodos accesores, no un mapa de strings. Si renombras una propiedad, el compilador te avisa de todos los usos.

## Resumen

- Agrupa la config de cada slice en un `record` anotado con `@ConfigurationProperties(prefix = ...)`.
- Añade `@Validated` + anotaciones de Bean Validation para validar al arrancar.
- Actívalo con `@ConfigurationPropertiesScan` en la clase principal.
- Inyecta el objeto tipado en los handlers, no `@Value` sueltos.
- Ganas fail-fast en el arranque y configuración inmutable autodocumentada.
