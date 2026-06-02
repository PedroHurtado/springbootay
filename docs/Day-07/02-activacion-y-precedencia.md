# Activación de perfiles y precedencia de propiedades

Tener los ficheros separados no sirve de nada si no sabemos **cómo se activa cada entorno** y **qué gana cuando un valor está definido en varios sitios**. Esta es la parte que más confunde en la práctica, así que conviene tenerla muy clara.

## Cómo se activa un perfil

De menor a mayor prioridad:

```yaml
# 1. Por defecto, en application.yml
spring:
  profiles:
    default: dev
```

```bash
# 2. Variable de entorno (lo habitual en contenedores)
SPRING_PROFILES_ACTIVE=prod java -jar pizzeria.jar

# 3. Argumento de línea de comandos (gana sobre la env var)
java -jar pizzeria.jar --spring.profiles.active=stage
```

En la práctica, en un despliegue con contenedores se usa la variable de entorno `SPRING_PROFILES_ACTIVE`. El argumento de línea de comandos se reserva para sobreescribir puntualmente.

## Precedencia de property sources

Cuando una propiedad está definida en varios sitios, Spring Boot aplica un orden de prioridad. Simplificado para lo que importa en el día a día (de mayor a menor):

1. **Argumentos de línea de comandos** — `--server.port=9000`
2. **Variables de entorno** — `SERVER_PORT=9000`
3. **`application-{profile}.yml`** — el fichero del perfil activo
4. **`application.yml`** — el fichero base

Esto es lo que hace que el modelo funcione:

- El fichero del perfil aporta los **defaults** del entorno.
- Una variable de entorno puntual puede **sobreescribir** cualquier valor sin tocar el fichero.

Es justo el enfoque que buscamos: **profiles para defaults, env vars para secretos y overrides.**

## Binding relajado

Spring hace *relaxed binding*: no exige que el nombre coincida literalmente. Una variable de entorno `DB_URL` (mayúsculas con guion bajo) resuelve `spring.datasource.url` o cualquier `${db.url}`.

Esto importa porque las variables de entorno, por convención, van en `MAYUSCULAS_CON_GUION_BAJO`, mientras que las propiedades de Spring van en `kebab-case.con.puntos`. El binding relajado hace de puente entre los dos mundos sin que tengas que preocuparte.

## Resumen

- El perfil se activa con `spring.profiles.active` (env var en contenedores, argumento para overrides puntuales).
- Si nadie lo activa, manda `spring.profiles.default`.
- Cuanto más cerca del arranque concreto está definida una propiedad, más prioridad tiene: línea de comandos > env vars > fichero de perfil > fichero base.
- El binding relajado conecta `MAYUSCULAS_GUION_BAJO` con `kebab-case.con.puntos` automáticamente.
