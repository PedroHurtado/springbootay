# @Bean en @Configuration vs escaneo de componentes

## Dos formas de declarar beans

Spring ofrece dos mecanismos principales para registrar beans en el contenedor:

1. **Escaneo de componentes**: anotar la clase con un estereotipo (`@Service`, `@Repository`, etc.) y dejar que `@ComponentScan` la descubra.
2. **Declaración explícita en `@Configuration`**: crear una clase de configuración con métodos `@Bean` que devuelven instancias.

Ambas producen el mismo resultado — un bean registrado en el contenedor — pero se aplican en contextos distintos.

## @Configuration y @Bean

Una clase `@Configuration` es una clase de configuración que contiene métodos productores de beans:

```java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
```

Cada método anotado con `@Bean` registra el objeto devuelto como bean del contenedor. El nombre del bean es, por defecto, el nombre del método (`objectMapper`).

## Cuándo usar cada forma

### Component scan: clases propias

Cuando la clase es **tuya** y puedes anotarla, usa estereotipos. Es más directo y reduce código:

```java
@Service
public class IngredientPricingService {
    // ...
}
```

### @Bean: clases que no controlas o configuración no trivial

Usa `@Bean` cuando:

- **La clase no es tuya** y no puedes anotarla (librerías de terceros: `ObjectMapper`, `RestTemplate`, `DataSource`...).
- **La construcción no es trivial**: requiere lectura de propiedades, decisiones condicionales, builders complejos.
- **Quieres registrar varias instancias del mismo tipo** con configuraciones distintas.
- **El bean es una interfaz funcional o lambda**.

```java
@Configuration
public class HttpConfig {

    @Bean
    public WebClient stripeClient(@Value("${stripe.api-url}") String url) {
        return WebClient.builder()
            .baseUrl(url)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .build();
    }

    @Bean
    public WebClient paylandsClient(@Value("${paylands.api-url}") String url) {
        return WebClient.builder()
            .baseUrl(url)
            .build();
    }
}
```

Aquí no podemos usar `@Component` sobre `WebClient` (no es nuestra clase) y además queremos **dos instancias distintas**.

## Por qué @Configuration y no solo @Component

Una pregunta razonable: si todos los estereotipos descubren la clase, ¿qué tiene de especial `@Configuration`?

Una clase `@Configuration` recibe un tratamiento extra: Spring genera una **subclase proxy CGLIB** en runtime que intercepta las llamadas entre métodos `@Bean` y se asegura de que devuelvan **siempre el mismo bean singleton**, no una nueva instancia cada vez.

```java
@Configuration
public class AppConfig {

    @Bean
    public IngredientRepository repository() {
        return new IngredientRepository();
    }

    @Bean
    public IngredientService service() {
        return new IngredientService(repository());  // ← misma instancia siempre
    }
}
```

La llamada `repository()` dentro de `service()` **no** crea un nuevo `IngredientRepository`. El proxy intercepta la llamada y devuelve el bean ya registrado en el contenedor.

Si la clase estuviera marcada con `@Component` en lugar de `@Configuration`, **sí** se crearía una nueva instancia cada vez, rompiendo el contrato de singleton. Por eso para declarar beans con `@Bean` se usa siempre `@Configuration`.

A este comportamiento se le llama modo **full** de `@Configuration`. Existe un modo **lite** (cuando `proxyBeanMethods = false` o cuando se usan `@Bean` en clases `@Component`), pero queda fuera del alcance de este documento.

## Dependencias entre @Bean

Los métodos `@Bean` pueden declarar dependencias como parámetros del método. Spring los inyecta automáticamente:

```java
@Configuration
public class ServiceConfig {

    @Bean
    public IngredientService ingredientService(
            IngredientRepository repository,
            PricingPolicy policy) {
        return new IngredientService(repository, policy);
    }
}
```

Spring busca beans del tipo declarado en cada parámetro y los pasa al método. Es la misma resolución que en la inyección por constructor de los estereotipos.

## Nombre del bean

El nombre por defecto de un bean declarado con `@Bean` es el nombre del método:

```java
@Bean
public WebClient stripeClient() { ... }   // bean name: "stripeClient"
```

Se puede sobreescribir explícitamente:

```java
@Bean(name = "stripe")
public WebClient stripeClient() { ... }   // bean name: "stripe"

@Bean({ "primary", "main" })              // varios nombres (alias)
public DataSource dataSource() { ... }
```

## Auto-configuración: @Bean como base

Toda la **auto-configuración de Spring Boot** está construida sobre `@Configuration` + `@Bean`. Cuando añadimos `spring-boot-starter-web`, Spring Boot carga clases como `WebMvcAutoConfiguration` que contienen métodos `@Bean` para `DispatcherServlet`, `RequestMappingHandlerMapping`, etc.

Estas auto-configuraciones usan anotaciones condicionales (`@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty`...) para activarse solo cuando tiene sentido. Por ejemplo:

```java
@Bean
@ConditionalOnMissingBean
public ObjectMapper jacksonObjectMapper(Jackson2ObjectMapperBuilder builder) {
    return builder.createXmlMapper(false).build();
}
```

Esto significa: "registra este `ObjectMapper` solo si **el usuario no ha declarado el suyo**". Es el patrón que permite que Spring Boot tenga valores por defecto razonables y al mismo tiempo te deje sobreescribirlos sin esfuerzo.

## Ámbito de aplicación de @Bean

Los métodos `@Bean` admiten todas las anotaciones de configuración aplicables a beans:

```java
@Bean
@Primary
@Scope("singleton")
public IngredientRepository repository() { ... }

@Bean
@Profile("dev")
public DataSource devDataSource() { ... }

@Bean(initMethod = "start", destroyMethod = "shutdown")
public ExpensiveResource resource() { ... }
```

`@Primary`, `@Qualifier`, `@Scope`, `@Profile`, `@Conditional`, `@DependsOn`, `@Lazy`, métodos de init y destrucción — todo aplicable como en componentes escaneados.

## Resumen

- Escaneo (`@Component` y derivados): para clases propias con construcción simple.
- `@Bean` en `@Configuration`: para clases de terceros, construcciones complejas o múltiples instancias del mismo tipo.
- `@Configuration` aplica proxy CGLIB para que las llamadas entre `@Bean` devuelvan siempre el bean del contenedor, no nuevas instancias.
- El nombre por defecto del bean es el nombre del método.
- Toda la auto-configuración de Spring Boot está construida sobre este mecanismo.
