# Estereotipos y @ComponentScan

## Qué es un estereotipo

Un **estereotipo** es una anotación que marca una clase como candidata a bean del contenedor IoC. Cuando el `ClassPathBeanDefinitionScanner` recorre el classpath, busca clases anotadas con cualquier estereotipo y las registra automáticamente.

El estereotipo base es `@Component`. Todos los demás son **especializaciones** de `@Component` con un significado semántico añadido.

## La jerarquía de estereotipos

```
@Component                  ← genérico, sin semántica añadida
├── @Service               ← lógica de aplicación / dominio
├── @Repository            ← acceso a datos
├── @Controller            ← controlador web (MVC con vistas)
│   └── @RestController    ← controlador REST (@Controller + @ResponseBody)
└── @Configuration         ← clase de configuración con @Bean
```

A nivel de descubrimiento de beans, todos son equivalentes: cualquiera de ellos marca la clase como candidata. La diferencia está en la **intención** y en el comportamiento adicional que Spring aplica a algunos.

## Cada estereotipo y su propósito

### @Component

Estereotipo genérico. Se usa para cualquier bean que no encaje en las categorías más específicas: utilidades, validadores, mappers, componentes técnicos.

```java
@Component
public class IngredientMapper { }
```

### @Service

Indica que la clase contiene lógica de aplicación o dominio. Por sí misma no añade comportamiento técnico — es semánticamente equivalente a `@Component`. Sirve para que el equipo de desarrollo identifique a primera vista dónde vive la lógica de negocio.

```java
@Service
public class IngredientPricingService { }
```

**Implicación importante con `@Transactional`**: aunque `@Service` no implica transaccionalidad por sí solo, es la capa donde **debe colocarse `@Transactional`**.

```java
@Service
public class IngredientPricingService {

    private final IngredientRepository repository;

    public IngredientPricingService(IngredientRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void updatePrices(List<PriceChange> changes) {
        // toda la operación se ejecuta dentro de una transacción
    }
}
```

La regla convencional en Spring es:

- **`@Service`** define el límite transaccional. Es la capa que coordina el caso de uso completo.
- **`@Repository`** ejecuta operaciones individuales de persistencia, sin abrir transacciones propias.
- **`@Controller`/`@RestController`** nunca lleva `@Transactional` — su responsabilidad es traducir HTTP, no gestionar transacciones.

Por qué la transacción vive en `@Service` y no en `@Repository`: un caso de uso típicamente coordina varias operaciones de persistencia (leer un agregado, modificarlo, persistir cambios, publicar un evento). Todas deben confirmarse o revertirse juntas. Si la transacción vive en el repositorio, cada operación abriría su propia transacción independiente y perderíamos la atomicidad del caso de uso.

Cuando `@Transactional` se aplica a un método público, Spring envuelve el bean en un proxy que abre la transacción al entrar al método, hace commit al salir correctamente y rollback si se lanza una excepción runtime. Esto se cubre en detalle al hablar de transacciones, pero conviene saber desde ya que **el sitio correcto para `@Transactional` es la clase `@Service`**.

### @Repository

Marca una clase como componente de acceso a datos. **Sí añade comportamiento**: Spring aplica un post-procesador (`PersistenceExceptionTranslationPostProcessor`) que traduce las excepciones específicas de la tecnología de persistencia (JPA, JDBC, MongoDB...) a la jerarquía unificada `DataAccessException` de Spring.

```java
@Repository
public class IngredientRepository { }
```

### @Controller y @RestController

`@Controller` marca un componente web que maneja peticiones HTTP y devuelve normalmente nombres de vista (Thymeleaf, JSP...).

`@RestController` es la combinación de `@Controller` + `@ResponseBody`: cada método devuelve directamente el cuerpo de la respuesta, serializado a JSON.

```java
@RestController
@RequestMapping("/api/ingredients")
public class IngredientEndpoint { }
```

### @Configuration

Marca una clase como fuente de definiciones de beans declarados con `@Bean`. Se cubre en detalle en el documento 04.

```java
@Configuration
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

## Por qué usar el estereotipo correcto

Aunque a nivel funcional `@Component` bastaría para todo, usar el estereotipo correcto aporta:

- **Claridad de intención**: el lector entiende el rol del componente sin leer el código.
- **Comportamiento añadido**: `@Repository` traduce excepciones, `@RestController` serializa a JSON.
- **Herramientas**: IDEs, plugins de análisis estático y AOP pueden filtrar por estereotipo.
- **Coherencia de equipo**: convención compartida sobre dónde vive cada tipo de lógica.

## @ComponentScan

`@ComponentScan` le dice al contenedor **dónde buscar** clases con estereotipo.

```java
@Configuration
@ComponentScan(basePackages = "com.fudie")
public class AppConfig { }
```

En Spring Boot no solemos declararlo explícitamente porque `@SpringBootApplication` ya lo incluye:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(...)            // ← aquí
public @interface SpringBootApplication { }
```

Sin `basePackages`, Spring escanea **el paquete de la clase anotada y todos sus sub-paquetes**.

## Opciones de @ComponentScan

```java
@ComponentScan(
    basePackages = { "com.fudie.ingredients", "com.fudie.orders" },
    basePackageClasses = { IngredientCreate.class },
    includeFilters = @Filter(type = FilterType.ANNOTATION, classes = MyMarker.class),
    excludeFilters = @Filter(type = FilterType.REGEX, pattern = ".*Test.*")
)
```

- `basePackages`: paquetes a escanear (strings).
- `basePackageClasses`: alternativa type-safe — Spring usa el paquete de cada clase indicada.
- `includeFilters` / `excludeFilters`: reglas para incluir o excluir candidatos.

`basePackageClasses` es preferible cuando se quiere refactorizar sin riesgo: si cambias un paquete, el IDE actualiza la referencia automáticamente.

## El paquete raíz como convención

La convención de Spring Boot es poner la clase `@SpringBootApplication` en el paquete raíz del proyecto:

```
com.fudie/
├── FudieApplication.java        ← raíz
├── ingredients/
│   ├── IngredientCreate.java
│   └── IngredientUpdate.java
└── orders/
    └── OrderCreate.java
```

Así, todo lo que cuelgue de `com.fudie` se escanea automáticamente sin configuración adicional. Si una clase queda **fuera** del árbol (por ejemplo en `com.utils`), no se descubrirá. Es uno de los errores más típicos al añadir módulos externos al proyecto.

## Filtros: cuándo se usan

Los filtros son útiles en escenarios concretos:

- **Excluir clases de test** cuando se reutiliza configuración entre producción y test.
- **Activar componentes solo bajo ciertas condiciones** (combinado con `@Profile` o `@Conditional` esto se hace de forma más limpia).
- **Auto-configuraciones de Spring Boot** que filtran qué cargar según lo que haya en el classpath.

En código de aplicación normal **rara vez se usan filtros**. Si los necesitas, suele ser señal de que la estructura del proyecto puede mejorarse.

## Custom stereotypes

Java permite componer anotaciones. Podemos crear un estereotipo propio que combine `@Service` con otras anotaciones:

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Service
@Transactional
public @interface UseCase { }
```

Y usarlo:

```java
@UseCase
public class CreateIngredientHandler { }
```

Spring detecta `@UseCase` como estereotipo (porque está meta-anotado con `@Service`) y registra la clase como bean, aplicando además `@Transactional`. Es una técnica potente pero conviene usarla con moderación: añade una capa de indirección que puede dificultar la lectura del código.

## Resumen

- Los estereotipos son anotaciones que marcan clases como candidatas a bean.
- Todos derivan de `@Component`; la diferencia es semántica (`@Service`, `@Controller`) o de comportamiento (`@Repository`, `@RestController`).
- `@ComponentScan` define dónde busca el contenedor; por defecto, el paquete de la clase anotada hacia abajo.
- `@SpringBootApplication` incluye `@ComponentScan` sin configuración, escaneando desde su paquete.
- La convención es colocar la clase principal en el paquete raíz del proyecto para que todo el código de aplicación quede dentro del escaneo automático.
