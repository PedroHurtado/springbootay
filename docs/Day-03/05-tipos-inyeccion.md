# Tipos de inyección de dependencias

## Las tres formas

Spring soporta tres mecanismos de inyección de dependencias:

1. **Por constructor**: las dependencias se reciben como parámetros del constructor.
2. **Por setter**: las dependencias se asignan mediante métodos setter.
3. **Por campo** (field injection): las dependencias se inyectan directamente en los atributos.

Las tres funcionan, pero **no son equivalentes**. La diferencia tiene implicaciones de diseño, testing y robustez del código.

## Inyección por constructor

Las dependencias se declaran como parámetros del constructor. El campo se marca `final`.

```java
@Service
public class IngredientPricingService {

    private final IngredientRepository repository;
    private final TaxCalculator taxCalculator;

    public IngredientPricingService(
            IngredientRepository repository,
            TaxCalculator taxCalculator) {
        this.repository = repository;
        this.taxCalculator = taxCalculator;
    }
}
```

Desde Spring 4.3, si la clase tiene **un único constructor**, no hace falta anotarlo con `@Autowired`. Spring lo detecta automáticamente.

## Inyección por setter

Las dependencias se asignan llamando a métodos setter después de construir el objeto.

```java
@Service
public class IngredientPricingService {

    private IngredientRepository repository;

    @Autowired
    public void setRepository(IngredientRepository repository) {
        this.repository = repository;
    }
}
```

## Inyección por campo

La anotación `@Autowired` (o `@Inject`, o `@Resource`) se coloca directamente sobre el atributo. Spring lo asigna por reflexión.

```java
@Service
public class IngredientPricingService {

    @Autowired
    private IngredientRepository repository;
}
```

Es la forma más corta de escribir, y la **menos recomendable**.

## Por qué constructor

La inyección por constructor es la opción correcta para código de aplicación. Razones:

### 1. Inmutabilidad

Los campos pueden declararse `final`. Una vez construido el objeto, sus dependencias no cambian. Esto elimina una clase entera de bugs (reasignación accidental) y mejora el razonamiento sobre el código.

### 2. Dependencias obligatorias explícitas

Si una clase necesita un `IngredientRepository`, el constructor lo declara. No se puede instanciar la clase sin proporcionarlo. El compilador lo garantiza.

Con inyección por campo o setter, el objeto puede existir con dependencias `null`, y el fallo aparece en runtime, lejos del punto del problema.

### 3. Testabilidad sin contenedor

Una clase con inyección por constructor se puede instanciar en un test unitario con `new`, pasando mocks o stubs directamente:

```java
@Test
void calculatesPriceCorrectly() {
    IngredientRepository repo = mock(IngredientRepository.class);
    TaxCalculator tax = mock(TaxCalculator.class);

    IngredientPricingService service = new IngredientPricingService(repo, tax);

    // ...
}
```

Con inyección por campo, hace falta reflexión (`ReflectionTestUtils.setField`) o arrancar el contenedor de Spring, lo que ralentiza los tests y los acopla al framework.

### 4. Detección de dependencias excesivas

Un constructor con seis parámetros se ve mal. Eso es una señal útil: la clase probablemente tiene demasiadas responsabilidades. Con inyección por campo, el problema queda oculto porque cada `@Autowired` es una línea más, indolora.

### 5. Detección de ciclos en arranque

Spring detecta dependencias circulares por constructor en el momento de arranque y falla rápido con un mensaje claro. Las dependencias circulares por setter o campo se resuelven silenciosamente con beans parcialmente construidos, lo que esconde el problema de diseño.

## Por qué no por campo

La inyección por campo es la más cómoda de escribir y la peor por casi todos los criterios anteriores. En concreto:

- **No permite `final`**: las dependencias son mutables.
- **Obliga a reflexión para tests** o a usar Spring en cada test.
- **Oculta el contrato** de la clase: no se ve qué necesita sin abrir el código.
- **Permite instanciar la clase con dependencias `null`**.
- **Acopla la clase a Spring**: sin contenedor no es usable.

La única razón por la que se usa es brevedad. No compensa.

## Por qué no por setter (en general)

La inyección por setter tiene un caso de uso legítimo: **dependencias verdaderamente opcionales** o **reconfiguración en runtime**.

```java
@Autowired(required = false)
public void setMetricsClient(MetricsClient client) {
    this.metricsClient = client;
}
```

Si `MetricsClient` está disponible en el contenedor, se inyecta; si no, el bean queda con `null` y el código debe manejarlo. Esto es legítimo en algunas integraciones, pero raro.

Para el 99% del código de aplicación, constructor es la respuesta.

## Lombok y Kotlin: constructor sin boilerplate

El argumento "el constructor con muchos campos es verboso" se resuelve con herramientas:

### Lombok

```java
@Service
@RequiredArgsConstructor
public class IngredientPricingService {

    private final IngredientRepository repository;
    private final TaxCalculator taxCalculator;
}
```

`@RequiredArgsConstructor` genera el constructor con todos los campos `final` no inicializados. Cero boilerplate, todas las ventajas.

### Java records

Los `record` ya tienen constructor canónico. Cuando se usan como portadores de configuración o como DTO no es necesario nada más:

```java
public record Command(String name, BigDecimal price) { }
```

(Los records no se suelen usar como beans gestionados con dependencias, pero sí como datos del slice.)

## @Autowired hoy

`@Autowired` sigue funcionando en cualquiera de las tres formas, pero en código moderno:

- **En constructor único**: se omite. Spring lo detecta solo.
- **En constructor múltiple**: se anota el constructor que Spring debe usar.
- **En setter o campo**: técnicamente válido, evitable en código nuevo.

```java
@Service
public class IngredientPricingService {

    private final IngredientRepository repository;

    // Sin @Autowired — único constructor
    public IngredientPricingService(IngredientRepository repository) {
        this.repository = repository;
    }
}
```

## Resumen

- Tres formas: constructor, setter, campo.
- **Constructor es la opción por defecto** para código de aplicación.
- Ventajas: inmutabilidad (`final`), dependencias obligatorias explícitas, tests sin Spring, detección de clases con demasiadas responsabilidades, detección temprana de ciclos.
- Field injection: cómoda pero perjudicial. Evitar.
- Setter injection: solo para dependencias opcionales o reconfigurables.
- Con constructor único no hace falta `@Autowired`.
- Lombok (`@RequiredArgsConstructor`) elimina el boilerplate del constructor.
