# Gestión de secretos

Esto es lo que separa un ejemplo de juguete de algo desplegable. Guardar credenciales de base de datos o API keys directamente en `application.yml` es una vulnerabilidad de seguridad de primer orden. El riesgo real no es solo que el fichero exista, sino que acabe commiteado en el control de versiones.

Lo presentamos como una **escalera de madurez**, de menos a más robusto. No todo proyecto necesita el nivel máximo desde el día uno.

## Nivel 0 — Lo que NUNCA se hace

```yaml
spring:
  datasource:
    password: supersecret123   # commiteado al repo = incidente de seguridad
```

Una vez que un secreto entra en el historial de Git, **está comprometido para siempre**, aunque lo borres en el commit siguiente: sigue en el historial. La única respuesta correcta ante eso es rotar el secreto.

## Nivel 1 — Variables de entorno (mínimo viable)

El fichero solo tiene el placeholder; el valor vive fuera.

```yaml
spring:
  datasource:
    password: ${DB_PASSWORD}
```

```bash
export DB_PASSWORD='...'
java -jar pizzeria.jar --spring.profiles.active=prod
```

Suficiente para muchísimos proyectos: el secreto no está en el código. **Límites:** las env vars son visibles en el listado de procesos, no hay rotación y no hay auditoría (quién accedió a qué y cuándo, no lo sabes).

## Nivel 2 — Ficheros de secretos montados (contenedores / Kubernetes)

En orquestadores, el patrón habitual es montar el secreto como fichero y que Spring lo lea:

```yaml
spring:
  config:
    import: optional:file:/etc/secrets/
```

Kubernetes monta sus `Secret` como ficheros en esa ruta y Spring los incorpora como property source. Mejor que la env var (no aparece en el entorno del proceso), pero sigue sin rotación ni auditoría centralizada.

## Nivel 3 — Gestor de secretos dedicado

El salto cualitativo. Un gestor proporciona almacenamiento centralizado y cifrado, con control de acceso, registro de auditoría y rotación automática de credenciales.

Dos opciones a conocer:

- **HashiCorp Vault** — agnóstico de cloud, open source, estándar de facto. Spring Cloud Vault lo integra de forma transparente (inyecta los secretos como property source, con health checks y varios métodos de autenticación: AppRole, Kubernetes, AWS IAM).
- **AWS Secrets Manager / GCP Secret Manager / Azure Key Vault** — los nativos de cada nube. Si ya estás en un cloud concreto, suelen ser la opción de menor fricción.

### Integración de Vault con Spring

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-vault-config</artifactId>
</dependency>
```

Desde Spring Boot 2.4+ el bootstrap context está deshabilitado por defecto; se usa `spring.config.import` en el `application.yml` normal (antes iba en `bootstrap.yml`):

```yaml
spring:
  config:
    import: vault://
  cloud:
    vault:
      uri: https://vault.example.com:8200
      authentication: KUBERNETES   # o APPROLE, AWS_IAM...
      kv:
        enabled: true
        backend: secret
        default-context: pizzeria
```

Lo elegante: **el resto del código no cambia nada**. El `${DB_PASSWORD}` del datasource sigue igual; lo único que cambia es de dónde sale el valor. Vault se inyecta como property source de alta prioridad y resuelve los placeholders de forma transparente. Enlaza directo con la precedencia de property sources.

### Secretos dinámicos

El concepto que diferencia a Vault de todo lo anterior: en vez de una contraseña estática, se generan credenciales bajo demanda con tiempo de vida limitado, rotadas automáticamente. Vault le pide a la base de datos un usuario temporal que caduca en X minutos; si se filtra, el daño es mínimo porque ya expiró. Es "el siguiente nivel", no hace falta montarlo para entenderlo.

## Resumen por entorno

| Entorno | Solución |
|---|---|
| **dev** | Valores en claro en `application-dev.yml` (locales, desechables) |
| **stage** | Variables de entorno o ficheros montados |
| **prod** | Gestor de secretos (Vault / cloud) con rotación y auditoría |

## La regla de oro

**El código y el repositorio nunca conocen un secreto de producción; solo conocen el nombre del placeholder donde buscarlo.** Quién resuelve ese placeholder (env var, fichero montado, Vault) es una decisión de despliegue, no de código. Por eso `${DB_PASSWORD}` es lo único que aparece en el fichero en los tres casos, y la aplicación funciona idéntica sin recompilar.

Nota de cumplimiento: la auditoría de accesos (quién leyó qué secreto y cuándo) no es un lujo, es requisito en sectores como banca o seguros. Eso solo lo da un gestor dedicado, no las env vars.
