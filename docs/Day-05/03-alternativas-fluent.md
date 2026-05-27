# Alternativas fluentes a Bean Validation

Bean Validation es el estándar, pero **no es la única opción**. Hay quien viene de FluentValidation (.NET) y echa de menos el estilo fluido y programático. Hay quien quiere validación sin reflexión, en compile-time. Hay quien necesita reglas cruzadas y conditionals que las anotaciones llevan mal.

En esta sesión vemos tres alternativas relevantes para Java puro:

1. **Java Fluent Validator** (mvallim) — el más parecido a .NET FluentValidation.
2. **Avaje Validator** — compile-time, sin reflexión, alternativa moderna a Hibernate Validator.
3. **Fluent Validator** (neoremind) — wrapper fluido sobre Bean Validation.

Descartamos **Valiktor** (Kotlin only, no Java) y **Konform** (Kotlin only) aunque a veces aparecen en búsquedas.

## Java Fluent Validator (mvallim/java-fluent-validator)

Inspirado directamente en FluentValidation de .NET. Define las reglas en una clase separada, no en el DTO.

```xml
<dependency>
    <groupId>br.com.fluent.validator</groupId>
    <artifactId>java-fluent-validator</artifactId>
    <version>1.13.1</version>
</dependency>
```

**El DTO queda limpio:**

```java
public record CreatePizzaCommand(
    String name,
    BigDecimal price,
    List<UUID> ingredientIds
) {}
```

**Las reglas se definen aparte:**

```java
public class CreatePizzaValidator extends AbstractValidator<CreatePizzaCommand> {

    @Override
    protected void rules() {
        ruleFor(CreatePizzaCommand::name)
            .must(not(stringEmptyOrNull()))
            .withMessage("El nombre es obligatorio")
            .withFieldName("name")
            .critical();

        ruleFor(CreatePizzaCommand::name)
            .must(stringSizeLessThanOrEqual(100))
            .withMessage("Máximo 100 caracteres")
            .withFieldName("name");

        ruleFor(CreatePizzaCommand::price)
            .must(not(nullValue()))
            .withMessage("El precio es obligatorio")
            .withFieldName("price")
            .critical();

        ruleFor(CreatePizzaCommand::price)
            .must(greaterThan(BigDecimal.ZERO))
            .withMessage("El precio debe ser positivo")
            .withFieldName("price");

        ruleFor(CreatePizzaCommand::ingredientIds)
            .must(not(collectionEmptyOrNull()))
            .withMessage("Al menos un ingrediente")
            .withFieldName("ingredientIds");
    }
}
```

**Uso:**

```java
ValidationResult result = new CreatePizzaValidator().validate(command);

if (!result.isValid()) {
    // result.getErrors() → List<Error>
}
```

**A favor:**

- Separación total entre DTO y reglas (los que vienen de .NET lo reconocen al instante).
- Lógica condicional con `.when(...)`.
- Combinación de validadores (`.andValidator(...)`).
- Mensajes y nombres de campo explícitos.

**En contra:**

- No hay integración nativa con Spring MVC. `@Valid` no lo dispara. Lo invocas tú a mano en el handler.
- Comunidad pequeña comparada con Hibernate Validator.
- No genera Problem Details automáticamente.

## Avaje Validator

Filosofía opuesta a Hibernate Validator: **cero reflexión, todo en compile-time**.

```xml
<dependency>
    <groupId>io.avaje</groupId>
    <artifactId>avaje-validator</artifactId>
    <version>2.16</version>
</dependency>
<dependency>
    <groupId>io.avaje</groupId>
    <artifactId>avaje-validator-constraints</artifactId>
    <version>2.16</version>
</dependency>

<!-- Annotation processor -->
<dependency>
    <groupId>io.avaje</groupId>
    <artifactId>avaje-validator-generator</artifactId>
    <version>2.16</version>
    <scope>provided</scope>
</dependency>
```

**Las anotaciones son casi idénticas a Jakarta:**

```java
@Valid
public record CreatePizzaCommand(
    @NotBlank
    @Size(max = 100)
    String name,

    @NotNull
    @Positive
    BigDecimal price,

    @NotEmpty
    List<UUID> ingredientIds
) {}
```

La diferencia: el annotation processor **genera en compile-time** una clase `CreatePizzaCommandValidationAdapter` con el código de validación. No hay reflexión en runtime.

**A favor:**

- Arranque rápido (sin escaneo reflexivo).
- Imágenes nativas (GraalVM) sin pelearse con configuraciones de reflexión.
- ~120KB vs los varios MB de Hibernate Validator.
- Soporta las anotaciones Jakarta estándar, así que el código del Command **no cambia** si decides migrar.

**En contra:**

- Comunidad mucho más pequeña.
- Integración con Spring MVC no es automática, hay que cablearla.
- Menos extensiones de terceros.

Es la opción interesante para microservicios que arrancan en frío (Cloud Run, Lambda) donde el tiempo de cold start cuenta.

## Fluent Validator (neoremind/fluent-validator)

Es un **wrapper fluido sobre Bean Validation**. No reemplaza Hibernate Validator, lo orquesta con una API más expresiva.

```java
Result result = FluentValidator.checkAll()
    .on(command.name(), new NameValidator())
    .on(command.price(), new PriceValidator())
    .on(command, new HibernateSupportedValidator<CreatePizzaCommand>()
        .setHibernateValidator(validator))
    .doValidate()
    .result(toSimple());
```

Útil cuando ya tienes Bean Validation y necesitas añadir validadores custom encadenados sin perder lo que ya hace Hibernate Validator. Pero el proyecto está bastante parado.

## Tabla comparativa

| Aspecto | Bean Validation (Hibernate) | Java Fluent Validator | Avaje Validator |
|---------|------------------------------|------------------------|------------------|
| Estilo | Anotaciones declarativas | Fluent DSL imperativo | Anotaciones declarativas |
| Reflexión | Sí (runtime) | No (programático) | No (compile-time) |
| Integración Spring `@Valid` | Automática | Manual | Manual |
| Reglas condicionales | Limitadas | Excelentes (`.when()`) | Limitadas (groups) |
| Mensajes/i18n | Sí | Sí | Sí (ResourceBundle) |
| Madurez | Estándar de facto | Nicho | Emergente |
| GraalVM nativo | Necesita config | Bien | Excelente |
| Curva de entrada | Baja | Media | Baja |

## Recomendación para Fudie / proyectos enterprise

1. **Por defecto, Bean Validation + Hibernate Validator**. Es el estándar, Spring lo trae, los nuevos en el equipo lo conocen, hay infinita documentación. No se gana nada eligiendo otra cosa "para presumir".

2. **Java Fluent Validator** si en algún slice concreto las reglas se vuelven barrocas (muchas condicionales cruzadas, reglas que dependen de otros campos del mismo command). Lo invocas dentro del Handler. No es ni-todo-ni-nada.

3. **Avaje Validator** si el proyecto va a GraalVM nativo y el cold start importa (microservicios en Cloud Run / Lambda). Las anotaciones son las mismas, así que migrar es viable.

4. **Para validación de dominio**: ninguna de estas. Validación de dominio es lógica imperativa en constructores y métodos. No usar una librería externa para algo que son tres `if` con excepciones de dominio bien nombradas.

## El punto incómodo: ¿esto es realmente "Fluent Validation"?

Quien viene de .NET con FluentValidation espera **esto**:

```csharp
public class PizzaValidator : AbstractValidator<CreatePizzaCommand>
{
    public PizzaValidator()
    {
        RuleFor(x => x.Name).NotEmpty().MaximumLength(100);
        RuleFor(x => x.Price).GreaterThan(0);
        RuleFor(x => x.IngredientIds).NotEmpty();
    }
}
```

De las opciones Java, la que más se parece es **Java Fluent Validator**. Las otras siguen siendo anotaciones, con otro motor por debajo. Si la pregunta es "¿hay Fluent Validation en Java?", la respuesta honesta es: **sí, pero no es el estándar, y el ecosistema mainstream sigue casado con anotaciones**.
