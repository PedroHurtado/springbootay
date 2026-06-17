# Gateway — Spring Cloud Gateway

API Gateway que actúa como **puerta de entrada** delante del microservicio `demo`.

```
   Cliente
     │
     ▼
  Gateway  (localhost:8081)
     │   route demo-api:  Path=/api/**  ->  http://localhost:8080
     ▼
  Microservicio demo  (localhost:8080)
```

## Versiones

- Spring Boot **4.0.6**
- Spring Cloud **2025.1.1** (Oakwood), Gateway **5.0.x** sobre la pila reactiva (WebFlux + Netty)
- Starter: `spring-cloud-starter-gateway-server-webflux`

> Nota: en Gateway 5.0 el prefijo de configuración pasó a
> `spring.cloud.gateway.server.webflux.routes` (antes era `spring.cloud.gateway.routes`).

## Cómo funciona la ruta

En [`application.yml`](src/main/resources/application.yml):

```yaml
spring:
  cloud:
    gateway:
      server:
        webflux:
          routes:
            - id: demo-api          # nombre de la ruta
              uri: http://localhost:8080   # a DÓNDE se reenvía
              predicates:
                - Path=/api/**      # CUÁNDO se aplica
```

Cada *route* combina un **predicate** (la condición que debe cumplir la petición)
con una **uri** (el destino al que se reenvía).

## Probar el enlace

Con ambos servicios arrancados (ver `start.ps1` en la carpeta superior):

```bash
# Mismo endpoint, mismo resultado: directo vs. a través del gateway
curl http://localhost:8080/api/ingredients   # micro directo
curl http://localhost:8081/api/ingredients   # a través del gateway

# Escribir a través del gateway y leer directo del micro
curl -X POST http://localhost:8081/api/ingredients \
     -H "Content-Type: application/json" \
     -d "{\"name\":\"Mozzarella\",\"cost\":2.5}"
```

## Arrancar todo (microservicio + gateway)

Los scripts de arranque están en la **carpeta superior** (junto a `demo/` y `gateway/`)
y levantan los **dos** servicios a la vez. Elige según tu terminal:

### Git Bash (recomendado en este equipo)

```bash
cd ..        # situarse en la carpeta que contiene demo/ y gateway/
./start.sh
```

- Levanta micro (8080) y gateway (8081) en segundo plano.
- Muestra los dos logs en vivo (`.logs/demo.log` y `.logs/gateway.log`).
- **`Ctrl+C` para ambos** de forma limpia (libera los puertos 8080 y 8081).

### PowerShell

```powershell
cd ..        # situarse en la carpeta que contiene demo/ y gateway/
.\start.ps1
```

- Abre **una ventana por servicio** para ver cada log por separado.
- Para parar: cierra cada ventana (o `Ctrl+C` dentro de ella).

> La **primera** ejecución descarga dependencias con Maven y tarda un poco.
> Cuando ambos servicios muestren `Started`, ya puedes hacer las pruebas de arriba.

## Arrancar solo el gateway

```bash
./mvnw spring-boot:run
```
