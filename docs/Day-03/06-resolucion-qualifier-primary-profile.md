# Resolución de dependencias: @Qualifier, @Primary, @Profile

## El problema base

Spring resuelve las dependencias **por tipo**. Cuando un constructor declara:

```java
public IngredientPricingService(TaxCalculator taxCalculator) { ... }
```

el contenedor busca un bean de tipo `TaxCalculator` y lo inyecta. Si hay **exactamente uno**, todo funciona. Si hay **cero**, el arranque falla con `NoSuchBeanDefinitionException`. Si hay **más de uno**, el arranque falla con `NoUniqueBeanDefinitionException`.

Las anotaciones `@Qualifier`, `@Primary` y `@Profile` son los mecanismos para resolver el caso de "más de uno".

## El escenario

Imaginemos que tenemos dos implementaciones de la misma interfaz:

```java
public interface PaymentProvider {
    PaymentResult charge(PaymentRequest request);
}

@Service
public class StripeProvider implements PaymentProvider {
    public PaymentResult charge(PaymentRequest request) { ... }
}

@Service
public class PaylandsProvider implements PaymentProvider {
    public PaymentResult charge(PaymentRequest request) { ... }
}
```

Y un servicio que necesita uno:

```java
@Service
public class CheckoutService {
    private final PaymentProvider paymentProvider;

    public CheckoutService(PaymentProvider paymentProvider) {
        this.paymentProvider = paymentProvider;
    }
}
```

Spring no sabe cuál inyectar. Falla en el arranque.

## @Primary

`@Primary` marca **el bean preferido** cuando hay varios candidatos del mismo tipo. Si no se especifica nada, Spring elige el primario.

```java
@Service
@Primary
public class StripeProvider implements PaymentProvider { ... }

@Service
public class PaylandsProvider implements PaymentProvider { ... }
```

Ahora `CheckoutService` recibirá `StripeProvider` automáticamente. `PaylandsProvider` sigue existiendo en el contenedor y se puede inyectar explícitamente.

`@Primary` también se puede declarar en métodos `@Bean`:

```java
@Bean
@Primary
public PaymentProvider stripeProvider() { ... }
```

**Cuándo usar `@Primary`**: cuando hay un candidato "por defecto" claro y los demás son alternativas que solo se usan explícitamente.

## @Qualifier

`@Qualifier` selecciona un bean **por nombre** entre varios candidatos. Se usa en el punto de inyección.

```java
@Service
public class CheckoutService {

    private final PaymentProvider stripeProvider;
    private final PaymentProvider paylandsProvider;

    public CheckoutService(
            @Qualifier("stripeProvider") PaymentProvider stripeProvider,
            @Qualifier("paylandsProvider") PaymentProvider paylandsProvider) {
        this.stripeProvider = stripeProvider;
        this.paylandsProvider = paylandsProvider;
    }
}
```

El nombre del qualifier coincide por defecto con el nombre del bean (el nombre simple de la clase decapitalizado, o el nombre del método `@Bean`).

### Qualifier explícito en la declaración

Podemos asignar un qualifier propio al bean independientemente del nombre de la clase:

```java
@Service
@Qualifier("stripe")
public class StripeProvider implements PaymentProvider { ... }

@Service
@Qualifier("paylands")
public class PaylandsProvider implements PaymentProvider { ... }
```

Y usarlo:

```java
public CheckoutService(@Qualifier("stripe") PaymentProvider provider) { ... }
```

### Qualifiers personalizados (type-safe)

Para evitar strings mágicos, se puede crear una anotación qualifier propia:

```java
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Stripe { }

@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Qualifier
public @interface Paylands { }
```

Uso:

```java
@Service
@Stripe
public class StripeProvider implements PaymentProvider { ... }

@Service
@Paylands
public class PaylandsProvider implements PaymentProvider { ... }

@Service
public class CheckoutService {
    public CheckoutService(@Stripe PaymentProvider provider) { ... }
}
```

Más verboso, pero refactorizable y comprobable en tiempo de compilación.

## @Primary vs @Qualifier

| | `@Primary` | `@Qualifier` |
|---|---|---|
| Decide quién | El productor | El consumidor |
| Cuántos | Uno solo es primary | Cualquier número |
| Selección | Por defecto si no se especifica | Explícita en cada punto |
| Uso típico | Implementación "por defecto" | Elegir entre varias por nombre |

Se pueden combinar: `@Primary` para el caso por defecto, `@Qualifier` para los puntos donde se necesita uno específico.

## @Profile

`@Profile` activa o desactiva beans según el **perfil de ejecución**. Es el mecanismo para tener configuraciones distintas en desarrollo, test y producción.

```java
@Service
@Profile("dev")
public class FakeEmailSender implements EmailSender { ... }

@Service
@Profile("prod")
public class SendGridEmailSender implements EmailSender { ... }
```

Spring registra **solo** el bean cuyo perfil está activo. Si arrancamos con `spring.profiles.active=dev`, el contenedor solo verá `FakeEmailSender`; no hay ambigüedad.

### Activar perfiles

Vía `application.properties`:

```properties
spring.profiles.active=dev
```

Vía variable de entorno:

```
SPRING_PROFILES_ACTIVE=dev
```

Vía línea de comandos:

```
java -jar app.jar --spring.profiles.active=dev
```

### Expresiones de perfil

`@Profile` admite expresiones lógicas:

```java
@Profile("dev | test")           // dev O test
@Profile("!prod")                // cualquiera menos prod
@Profile({ "dev", "qa" })        // dev O qa
```

### @Profile en métodos @Bean

Funciona igual sobre métodos `@Bean`:

```java
@Configuration
public class EmailConfig {

    @Bean
    @Profile("dev")
    public EmailSender devSender() { return new FakeEmailSender(); }

    @Bean
    @Profile("prod")
    public EmailSender prodSender() { return new SendGridEmailSender(); }
}
```

## @Conditional: el mecanismo general

`@Profile` está implementado por debajo con `@Conditional`, que es el mecanismo general para registrar beans según condiciones arbitrarias.

Spring Boot añade un catálogo amplio de condicionales:

```java
@ConditionalOnProperty(name = "fudie.payments.enabled", havingValue = "true")
@ConditionalOnClass(WebClient.class)
@ConditionalOnMissingBean(EmailSender.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnExpression("${fudie.feature.x:false}")
```

Estas anotaciones son la base de la auto-configuración de Spring Boot, y también sirven en código de aplicación cuando se necesita lógica condicional más fina que `@Profile`.

Ejemplo típico: registrar un bean solo si una propiedad está activa.

```java
@Service
@ConditionalOnProperty(name = "fudie.metrics.enabled", havingValue = "true")
public class MetricsCollector { ... }
```

## Aplicado al patrón dual PSP

Para un sistema con dos proveedores de pago donde el código de aplicación trabaja contra una abstracción y la elección del proveedor se hace en runtime, el patrón habitual es:

```java
public interface IPaymentProvider {
    PaymentResult charge(PaymentRequest request);
    String getId();   // "stripe", "paylands", ...
}

@Service
public class StripeProvider implements IPaymentProvider {
    public String getId() { return "stripe"; }
    // ...
}

@Service
public class PaylandsProvider implements IPaymentProvider {
    public String getId() { return "paylands"; }
    // ...
}

@Service
public class PaymentProviderResolver {

    private final Map<String, IPaymentProvider> providersById;

    public PaymentProviderResolver(List<IPaymentProvider> providers) {
        this.providersById = providers.stream()
            .collect(toMap(IPaymentProvider::getId, identity()));
    }

    public IPaymentProvider resolve(String providerId) {
        IPaymentProvider provider = providersById.get(providerId);
        if (provider == null) {
            throw new UnknownPaymentProviderException(providerId);
        }
        return provider;
    }
}
```

Aquí no usamos `@Qualifier` ni `@Primary` para distinguir: usamos **inyección de colección** y un selector de runtime. Es el patrón natural cuando la decisión depende de datos (no de configuración estática). Se detalla en el documento sobre inyección de colecciones.

## Resumen

- Spring resuelve por tipo; varios candidatos sin desambiguación → error.
- `@Primary`: marca el candidato por defecto. Lo decide el productor.
- `@Qualifier`: selecciona por nombre en el punto de inyección. Lo decide el consumidor.
- Qualifiers personalizados (anotaciones propias) evitan strings mágicos.
- `@Profile`: activa beans según el perfil de ejecución. Útil para dev/test/prod.
- `@Conditional` y derivados (`@ConditionalOnProperty`, `@ConditionalOnClass`...): mecanismo general de activación condicional.
- Cuando la elección entre implementaciones depende de datos en runtime, mejor inyectar la colección y resolver dinámicamente.
