# Configuración centralizada: Spring Cloud Config Server

Hasta ahora cada aplicación lleva sus propios `application-{profile}.yml` empaquetados en el jar. Cuando tienes **varias aplicaciones** (o varias instancias), esto se vuelve incómodo: cambiar un valor de producción obliga a recompilar y redesplegar.

Spring Cloud Config Server resuelve esto: la configuración **vive fuera**, en un repositorio Git centralizado, y un servidor la sirve por HTTP a las aplicaciones cuando arrancan.

## El esquema

```
                    ┌─────────────────────┐
                    │   Git repo           │
                    │   (config files)     │
                    │   pizzeria-dev.yml   │
                    │   pizzeria-prod.yml  │
                    └──────────┬───────────┘
                               │ lee
                    ┌──────────▼───────────┐
                    │  Config Server       │
                    │  (Spring Boot app)   │
                    └──────────┬───────────┘
                               │ sirve por HTTP
              ┌────────────────┼────────────────┐
        ┌─────▼─────┐   ┌──────▼──────┐   ┌──────▼──────┐
        │ pizzeria  │   │  otra app   │   │  otra app   │
        │ (cliente) │   │             │   │             │
        └───────────┘   └─────────────┘   └─────────────┘
```

## Por qué existe

Con `application-prod.yml` dentro de cada jar, cambiar un valor de prod significa recompilar y redesplegar. Con Config Server cambias el fichero en Git, las apps recogen el cambio (incluso en caliente con `@RefreshScope`) y no recompilas nada.

El Git de por medio es lo valioso: **versionas la configuración como código**. Tienes historial, pull requests y rollback de un cambio de configuración igual que harías con el código.

## El servidor

Es una app Spring Boot mínima:

```java
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
```

```yaml
server:
  port: 8888

spring:
  cloud:
    config:
      server:
        git:
          uri: https://github.com/tu-org/pizzeria-config
          default-label: main
```

## El cliente

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-config</artifactId>
</dependency>
```

```yaml
spring:
  config:
    import: optional:configserver:http://localhost:8888
  application:
    name: pizzeria          # el server busca pizzeria-{profile}.yml en Git
```

Igual que con Vault, **el código no cambia**: pizzeria sigue leyendo `${DB_URL}`, solo cambia de dónde viene el valor.

## Config Server no es gestión de secretos

Se confunden, pero son cosas distintas:

| | Config Server | Vault / Secret Manager |
|---|---|---|
| **Para qué** | Configuración general (URLs, timeouts, feature flags) | Secretos (passwords, API keys) |
| **Almacén** | Git (texto plano, versionado) | Almacén cifrado con control de acceso |
| **Auditoría** | Historial de Git | Audit log de quién leyó cada secreto |

Un secreto en Git plano sigue siendo un secreto en texto plano. Por eso en producción seria **se combinan**: Config Server para los valores normales y Vault (o valores cifrados) para los secretos. No es uno *o* el otro; es Config Server *y* Vault.

GitHub, en este contexto, es el **backend** del Config Server: el servidor lee la configuración de ahí.

## Resumen

- Config Server centraliza la configuración de varias apps en un repositorio Git.
- El servidor es una app Spring Boot con `@EnableConfigServer`; el cliente apunta a él con `spring.config.import`.
- Versionas la config como código: historial, PRs, rollback.
- No sustituye a un gestor de secretos: para producción seria, se combinan.
- Tiene sentido cuando hay varias aplicaciones; para una sola, los perfiles bastan.
