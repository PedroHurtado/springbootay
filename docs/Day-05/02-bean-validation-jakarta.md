# Bean Validation (Jakarta Validation) en el Command

Bean Validation es el **estándar** Java para validación declarativa. Se llamaba `javax.validation` hasta Jakarta EE 9, ahora es `jakarta.validation`. La implementación de referencia es **Hibernate Validator** (no confundir con Hibernate ORM, comparten organización, no producto).

Spring Boot la trae de serie con el starter:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>
```

## Cómo se engancha con Spring MVC

Dos piezas:

1. **Anotaciones en el Command** (`@NotBlank`, `@Size`, etc.).
2. **`@Valid` en el parámetro del Controller**, que es lo que dispara la validación.

```java
@RestController
@RequestMapping("/pizzas")
public class PizzaCreate {

    @PostMapping
    public ResponseEntity<Response> create(@Valid @RequestBody Command command) {
        // si Command no es válido, esta línea no se ejecuta
        // Spring lanza MethodArgumentNotValidException
        return ResponseEntity.ok(handler.handle(command));
    }

    public record Command(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede superar 100 caracteres")
        String name,

        @NotNull
        @Positive(message = "El precio debe ser positivo")
        BigDecimal price,

        @NotEmpty(message = "La pizza necesita al menos un ingrediente")
        List<@NotNull UUID> ingredientIds
    ) {}

    public record Response(UUID id) {}

    @Service
    public static class Handler { /* ... */ }
}
```

Tres detalles:

- **`@Valid`** dispara la validación. Sin él, las anotaciones del record son decorativas.
- **`@RequestBody`** indica que viene en el body. Para `@PathVariable` o `@RequestParam` se usan anotaciones en los propios parámetros del método (y se necesita `@Validated` en la clase del Controller).
- **`List<@NotNull UUID>`**: las anotaciones se pueden poner sobre el tipo genérico, no solo sobre la propiedad. Esto valida cada elemento de la lista.

## Catálogo de anotaciones esenciales

### Nulidad y presencia

| Anotación | Aplica a | Significado |
|-----------|----------|-------------|
| `@NotNull` | Cualquiera | No puede ser `null` |
| `@Null` | Cualquiera | Debe ser `null` |
| `@NotEmpty` | String, Collection, Map, Array | No null y `size() > 0` |
| `@NotBlank` | String | No null, no vacío, no solo espacios |

`@NotEmpty` sobre un String permite `"   "` (solo espacios). `@NotBlank` no. Para entradas de usuario, casi siempre quieres `@NotBlank`.

### Tamaños y rangos

| Anotación | Aplica a | Significado |
|-----------|----------|-------------|
| `@Size(min, max)` | String, Collection, Map, Array | Tamaño dentro del rango |
| `@Min(value)` | Numéricos | Valor mínimo |
| `@Max(value)` | Numéricos | Valor máximo |
| `@DecimalMin` / `@DecimalMax` | BigDecimal y numéricos | Mismos límites pero con precisión decimal |
| `@Positive` / `@PositiveOrZero` | Numéricos | > 0 / ≥ 0 |
| `@Negative` / `@NegativeOrZero` | Numéricos | < 0 / ≤ 0 |
| `@Digits(integer, fraction)` | Numéricos | Cifras enteras y decimales |

### Texto y formato

| Anotación | Aplica a | Significado |
|-----------|----------|-------------|
| `@Email` | String | Formato de email |
| `@Pattern(regexp)` | String | Cumple expresión regular |

### Fechas y tiempo

| Anotación | Aplica a | Significado |
|-----------|----------|-------------|
| `@Past` | Date, temporal | En el pasado |
| `@PastOrPresent` | Date, temporal | Pasado o ahora |
| `@Future` | Date, temporal | En el futuro |
| `@FutureOrPresent` | Date, temporal | Ahora o futuro |

### Otros útiles

| Anotación | Significado |
|-----------|-------------|
| `@AssertTrue` / `@AssertFalse` | Boolean obligatorio a true/false |
| `@Valid` | Validación recursiva en objetos anidados |

## Validación de objetos anidados

`@Valid` no solo se pone en el Controller. También dentro del Command, en propiedades complejas:

```java
public record Command(
    @NotBlank String name,

    @NotNull
    @Valid                          // ← propaga la validación a Address
    Address shippingAddress,

    @NotEmpty
    @Valid                          // ← cada Line se valida
    List<Line> lines
) {
    public record Address(
        @NotBlank String street,
        @NotBlank String city,
        @Pattern(regexp = "\\d{5}") String zipCode
    ) {}

    public record Line(
        @NotNull UUID productId,
        @Min(1) int quantity
    ) {}
}
```

Sin `@Valid` en `Address`, sus campos no se validan aunque tengan anotaciones.

## Constraint personalizada

Cuando ninguna anotación cubre el caso, se crea una. Dos piezas:

**1. La anotación**

```java
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = SlugValidator.class)
public @interface Slug {
    String message() default "El slug solo admite minúsculas, números y guiones";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

**2. El validador**

```java
public class SlugValidator implements ConstraintValidator<Slug, String> {

    private static final Pattern PATTERN = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;  // delega null en @NotNull
        return PATTERN.matcher(value).matches();
    }
}
```

Y se usa como cualquier otra:

```java
public record Command(
    @NotBlank @Slug String slug
) {}
```

**Convención importante**: cuando el valor es `null`, devolvemos `true`. La responsabilidad de exigir presencia es de `@NotNull`. Cada constraint hace una sola cosa.

## Manejo de errores: el @RestControllerAdvice

Cuando `@Valid` falla, Spring lanza `MethodArgumentNotValidException`. Por defecto eso es un 400 con un payload poco amable. Lo interceptamos para devolver **Problem Details (RFC 9457)**:

```java
@RestControllerAdvice
public class ValidationAdvice {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handle(MethodArgumentNotValidException ex) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setType(URI.create("https://fudie.eu/problems/validation"));

        Map<String, List<String>> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.groupingBy(
                FieldError::getField,
                Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())
            ));

        problem.setProperty("errors", errors);
        return problem;
    }
}
```

Respuesta:

```json
{
    "type": "https://fudie.eu/problems/validation",
    "title": "Validation failed",
    "status": 400,
    "errors": {
        "name": ["El nombre es obligatorio"],
        "price": ["El precio debe ser positivo"]
    }
}
```

## Lo que Bean Validation no hace bien

- **Reglas que cruzan campos**: "si el campo A es X, entonces B no puede ser null". Se puede hacer con `@AssertTrue` en un método del record, pero queda feo. Aquí entran las alternativas fluentes (siguiente doc).
- **Reglas que necesitan consultar la base de datos**: "este email no puede existir ya". Eso **no** es validación de Command, eso es regla de negocio del handler/dominio.
- **Mensajes muy personalizados con contexto**: se puede hacer con `ConstraintValidatorContext`, pero es verboso.

Para todo lo demás, Bean Validation cubre el 90% de los casos con cero líneas de código imperativo.
