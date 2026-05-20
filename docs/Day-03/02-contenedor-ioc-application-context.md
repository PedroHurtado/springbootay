# Contenedor IoC y ApplicationContext

## Qué es la Inversión de Control

En programación tradicional, el código de aplicación controla el flujo: crea sus objetos, los conecta entre sí, gestiona su ciclo de vida. La **Inversión de Control** (IoC) invierte esa responsabilidad: el código de aplicación declara *qué* necesita, y un contenedor externo se encarga de crear, conectar y gestionar los objetos.

La **Inyección de Dependencias** (DI) es la forma concreta en que Spring implementa IoC: el contenedor inyecta las dependencias declaradas por cada componente en lugar de que estos las construyan o las busquen.

```java
// Sin IoC — el código controla la construcción
public class IngredientService {
    private final IngredientRepository repository = new IngredientRepository();
}

// Con IoC — el contenedor inyecta la dependencia
public class IngredientService {
    private final IngredientRepository repository;

    public IngredientService(IngredientRepository repository) {
        this.repository = repository;
    }
}
```

## El contenedor

El **contenedor IoC** es el objeto central de Spring. Sus responsabilidades:

- **Descubrir** las clases candidatas a bean (escaneando el classpath).
- **Registrar** sus definiciones (`BeanDefinition`) en un registro interno.
- **Instanciar** los beans cuando hacen falta, resolviendo sus dependencias.
- **Gestionar el ciclo de vida** (creación, inicialización, destrucción).
- **Resolver dependencias** entre beans por tipo, por nombre o por cualificador.

En Spring hay dos interfaces principales que representan el contenedor: `BeanFactory` y `ApplicationContext`. La primera es la API mínima; la segunda extiende la primera con funcionalidades de framework (eventos, internacionalización, recursos, integración con AOP, etc.). En Spring Boot siempre trabajamos con `ApplicationContext`.

## ApplicationContext

`ApplicationContext` es el contenedor IoC completo de Spring. Cuando arrancamos una aplicación Spring Boot:

```java
@SpringBootApplication
public class FudieApplication {
    public static void main(String[] args) {
        SpringApplication.run(FudieApplication.class, args);
    }
}
```

`SpringApplication.run(...)` construye internamente un `ApplicationContext` (concretamente un `AnnotationConfigServletWebServerApplicationContext` para una aplicación web), escanea el classpath, registra los beans, los instancia y los conecta.

## El ciclo de arranque

El proceso de arranque del contenedor sigue estas fases:

1. **Carga de configuración**: Spring procesa la clase principal (`@SpringBootApplication`) y descubre las clases `@Configuration`, incluyendo las auto-configuraciones (`META-INF/spring/...AutoConfiguration.imports`).
2. **Component scan**: el `ClassPathBeanDefinitionScanner` recorre los paquetes configurados buscando clases con estereotipo.
3. **Registro de definiciones**: cada candidato se convierte en un `BeanDefinition` y se guarda en el `BeanDefinitionRegistry`. Aquí todavía **no se instancia nada**.
4. **Procesamiento de `BeanFactoryPostProcessor`**: se ejecutan procesadores que pueden modificar las definiciones (por ejemplo, resolver placeholders `${...}`).
5. **Instanciación de beans singleton**: el contenedor crea las instancias, inyecta sus dependencias y ejecuta los callbacks de inicialización.
6. **Aplicación lista**: el contenedor publica el evento `ContextRefreshedEvent` y la aplicación está operativa.

Cuando la aplicación se para, el contenedor ejecuta los callbacks de destrucción y cierra todos los beans en orden inverso.

## BeanDefinition vs bean

Un punto que conviene tener claro: **`BeanDefinition` no es lo mismo que un bean**.

- Una **`BeanDefinition`** es la *descripción* de cómo construir un bean: la clase, el scope, los argumentos del constructor, los métodos de inicialización, etc.
- Un **bean** es la *instancia* viva que el contenedor crea a partir de la definición.

Spring registra todas las definiciones primero y solo después instancia los beans. Esto le permite resolver dependencias circulares, gestionar el orden de creación y aplicar post-procesadores antes de que ninguna instancia exista.

## Cómo el contenedor encuentra los beans

`@SpringBootApplication` es una anotación compuesta que incluye `@ComponentScan` **sin paquete base explícito**. Por defecto, Spring escanea **el paquete de la clase anotada y todos sus sub-paquetes**.

```
com.fudie/
├── FudieApplication.java        ← @SpringBootApplication
├── ingredients/
│   ├── IngredientCreate.java    ← se descubre
│   └── IngredientUpdate.java    ← se descubre
└── orders/
    └── OrderCreate.java         ← se descubre
```

Si una clase con estereotipo está **fuera** del paquete raíz (por ejemplo en `com.other.something`), Spring **no la descubrirá** salvo que extendamos el escaneo explícitamente con `@ComponentScan(basePackages = {...})`.

Esta es una de las causas más habituales de "mi bean no se inyecta": está fuera del árbol del paquete raíz.

## Acceso programático al contenedor

Aunque lo normal es que el contenedor sea invisible para el código de aplicación, a veces necesitamos acceder a él directamente. Se inyecta como cualquier otro bean:

```java
@Service
public class BeanInspector {

    private final ApplicationContext context;

    public BeanInspector(ApplicationContext context) {
        this.context = context;
    }

    public void inspect() {
        String[] names = context.getBeanDefinitionNames();
        // ...
    }
}
```

Usos legítimos: diagnóstico, herramientas de administración, búsqueda dinámica de beans por tipo cuando no se conocen en tiempo de compilación. **No debe usarse para resolver dependencias normales** — para eso está la inyección por constructor.

## Resumen

- El contenedor IoC de Spring se materializa en el `ApplicationContext`.
- El contenedor descubre clases con estereotipo, registra `BeanDefinition`s, instancia beans, inyecta sus dependencias y gestiona su ciclo de vida.
- `@SpringBootApplication` activa el escaneo del paquete raíz hacia abajo.
- Una `BeanDefinition` describe cómo construir un bean; el bean es la instancia ya creada.
- El código de aplicación normalmente no toca el contenedor: declara dependencias en el constructor y Spring las inyecta.
