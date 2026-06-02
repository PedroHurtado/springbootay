# Configuración por entorno: perfiles y estructura de ficheros

Toda aplicación que vaya a desplegarse necesita comportarse distinto según dónde corra: en tu máquina, en preproducción y en producción. Spring Boot resuelve esto con **profiles**: un fichero base común y un fichero de overrides por entorno.

## Estructura de ficheros

```
src/main/resources/
├── application.yml              # común a todos los entornos
├── application-dev.yml          # desarrollo (local)
├── application-stage.yml        # preproducción
└── application-prod.yml         # producción
```

Spring carga primero `application.yml` y, encima, el fichero del perfil activo. Lo del perfil **sobreescribe** lo común.

## El fichero base

`application.yml` contiene lo que es igual en todos los entornos y el perfil por defecto:

```yaml
spring:
  application:
    name: pizzeria
  profiles:
    default: dev          # si nadie activa nada, arranca en dev
  jpa:
    open-in-view: false   # buena práctica, igual en todos los entornos

pizzeria:
  catalog:
    max-ingredients-per-pizza: 12
```

## Desarrollo

`application-dev.yml`: cómodo, verboso, secretos en claro (son locales y desechables).

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/pizzeria_dev
    username: dev
    password: dev          # en local no pasa nada
  jpa:
    hibernate:
      ddl-auto: update     # cómodo en dev
    show-sql: true

logging:
  level:
    com.example.pizzeria: DEBUG
    org.hibernate.SQL: DEBUG
```

## Preproducción

`application-stage.yml`: parecido a producción, pero con logging algo más generoso. Los secretos ya **no** van en el fichero.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: validate   # el esquema lo gestiona Flyway/Liquibase
    show-sql: false

logging:
  level:
    com.example.pizzeria: DEBUG
```

## Producción

`application-prod.yml`: logging mínimo, pool dimensionado, secretos externalizados.

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

logging:
  level:
    root: WARN
    com.example.pizzeria: INFO
```

## La regla que lo resume todo

**En dev los secretos pueden ir en claro porque son locales y desechables; en stage y prod nunca aparecen en el fichero, solo el placeholder `${...}` que resuelve contra el entorno.**

Esto es lo que separa un ejemplo de juguete de algo desplegable. El fichero del perfil aporta los *defaults* del entorno; de dónde sale el valor real de cada placeholder lo veremos en el documento de precedencia y en el de secretos.
