# Problem Details (RFC 9457) en Spring Boot

Guía de referencia para el ejercicio de slices.

---

## 1. Qué es Problem Details

**RFC 9457 — "Problem Details for HTTP APIs"** (julio 2023, obsoleta el RFC 7807) define un formato estándar para devolver errores en APIs HTTP/REST de forma legible por máquinas.

El content-type es **`application/problem+json`** (también existe `application/problem+xml`).

Campos canónicos:

| Campo | Significado |
|---|---|
| `type` | URI que identifica el **tipo** de problema |
| `title` | Descripción corta y legible del tipo de problema |
| `status` | Código HTTP, duplicado dentro del body |
| `detail` | Explicación específica de **esta ocurrencia concreta** |
| `instance` | URI que identifica la ocurrencia específica del problema |

Además, se pueden añadir libremente **extensiones** (members propios): `restaurantId`, `timestamp`, `errors`, etc.

Ejemplo de respuesta:

```json
{
  "type": "https://fudie.eu/problems/slot-unavailable",
  "title": "Slot no disponible",
  "status": 409,
  "detail": "El restaurante no acepta reservas en esa franja",
  "instance": "/reservas/abc-123",
  "requestedSlot": "2026-05-20T21:00:00"
}
```

---

## 2. Soporte en Spring Boot

Disponible de forma **nativa desde Spring Framework 6 / Spring Boot 3**. Está basado en RFC 7807, totalmente compatible con el formato de 9457. No hace falta ninguna librería externa: viene en `spring-web`, dentro de `spring-boot-starter-web` (o `spring-boot-starter-webflux`).

### Activación global

En `application.yml`:

```yaml
spring:
  mvc:
    problemdetails:
      enabled: true
  # Para WebFlux en lugar de MVC:
  # webflux:
  #   problemdetails:
  #     enabled: true
```

Con esto, las excepciones estándar de Spring (validación, 404, 405, etc.) se serializan automáticamente como `application/problem+json`.

---

## 3. La clase central: `ProblemDetail`

**Package: `org.springframework.http.ProblemDetail`**

> ⚠️ Atención al nombre: la clase es `ProblemDetail` **en singular**. No existe `ProblemDetails` en Spring (eso es el nombre en .NET). Si lo importáis en plural, el IDE no lo encontrará.

```java
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import java.net.URI;

ProblemDetail pd = ProblemDetail.forStatusAndDetail(
        HttpStatus.CONFLICT, "El restaurante no acepta reservas en esa franja");

pd.setType(URI.create("https://fudie.eu/problems/slot-unavailable"));
pd.setTitle("Slot no disponible");
pd.setInstance(URI.create("/reservas/abc-123"));   // ilustrativo: en el handler se saca de la request (ver sección 5)

// Extensiones (members propios)
pd.setProperty("restaurantId", restaurantId);
pd.setProperty("requestedSlot", slot);
```

Métodos de fábrica útiles:

- `ProblemDetail.forStatus(HttpStatusCode status)`
- `ProblemDetail.forStatusAndDetail(HttpStatusCode status, String detail)`

---

## 4. Excepciones de negocio: `ErrorResponseException`

**Package: `org.springframework.web.ErrorResponseException`**

Es la forma más directa de lanzar un error tipado que Spring traduce a `problem+json` automáticamente.

```java
import org.springframework.web.ErrorResponseException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import java.net.URI;

public class SlotUnavailableException extends ErrorResponseException {

    public SlotUnavailableException(String reservaId, String slot) {
        super(HttpStatus.CONFLICT,
              ProblemDetail.forStatusAndDetail(
                  HttpStatus.CONFLICT, "Franja no disponible"),
              null);

        getBody().setType(URI.create("https://fudie.eu/problems/slot-unavailable"));
        getBody().setInstance(URI.create("/reservas/" + reservaId));
        getBody().setProperty("requestedSlot", slot);
    }
}
```

`getBody()` devuelve el `ProblemDetail` interno, sobre el que se siguen configurando campos y extensiones.

Interfaces/clases relacionadas (todas en `spring-web`):

- `org.springframework.web.ErrorResponse` — interfaz base
- `org.springframework.web.ErrorResponseException` — excepción tipada

---

## 5. Handler centralizado: `@RestControllerAdvice`

### Por qué `@RestControllerAdvice` y NO `@ControllerAdvice`

La diferencia es la misma que entre `@Controller` y `@RestController`:

- **`@ControllerAdvice`** — el objeto devuelto por un `@ExceptionHandler` se interpreta por defecto como **nombre de vista**, salvo que se anote cada método con `@ResponseBody`.
- **`@RestControllerAdvice`** = `@ControllerAdvice` + `@ResponseBody` en todos los métodos. El valor devuelto se serializa directamente al body vía `HttpMessageConverter` → que es justo lo que queremos para `application/problem+json`.

```java
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    ProblemDetail handle(DomainException ex, HttpServletRequest request) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        pd.setType(URI.create("https://fudie.eu/problems/" + ex.code()));
        pd.setInstance(URI.create(request.getRequestURI()));   // <-- de la request, no hardcodeado
        pd.setProperty("timestamp", Instant.now());
        return pd;   // se serializa automáticamente como problem+json
    }
}
```

`HttpServletRequest` se autoinyecta como argumento en cualquier `@ExceptionHandler`, igual que en un `@RequestMapping` normal. `getRequestURI()` devuelve solo el path (`/reservas/abc-123`), sin query string — que es lo habitual y suficiente para `instance`.

> **Nota:** desde Spring Framework 6.1, Spring Boot ya rellena `instance` automáticamente con el path de la request en las excepciones que maneja de serie. El `setInstance` manual solo es necesario para vuestras propias excepciones.

Con `@ControllerAdvice` ese mismo método intentaría resolver una **vista** con el nombre del `toString()` del `ProblemDetail` y fallaría, salvo que se añada `@ResponseBody`:

```java
@ControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(DomainException.class)
    @ResponseBody   // <-- imprescindible con @ControllerAdvice
    ProblemDetail handle(DomainException ex) { ... }
}
```

**Matiz importante:** al extender `ResponseEntityExceptionHandler`, los handlers **heredados** devuelven `ResponseEntity<Object>`, y un `ResponseEntity` siempre va al body se use la anotación que se use. Por eso los métodos heredados funcionan igual con ambas anotaciones. Pero para **vuestros propios** `@ExceptionHandler` que devuelven `ProblemDetail` directamente, `@RestControllerAdvice` evita repetir `@ResponseBody` y es la opción estándar.

### `ResponseEntityExceptionHandler`

Clase base que ya devuelve `ProblemDetail` para las excepciones estándar de Spring. Solo hay que extenderla y añadir los handlers propios.

- Spring MVC (servlet): `org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler`
- WebFlux (reactivo): `org.springframework.web.reactive.result.method.annotation.ResponseEntityExceptionHandler`

---

## 6. Validación: detalle de errores por campo

Con `spring.mvc.problemdetails.enabled=true`, un `@Valid` que falla (lanza `MethodArgumentNotValidException`) ya produce un `ProblemDetail` por defecto.

Si se quiere el detalle por campo (equivalente al diccionario `errors`), se sobrescribe el método heredado. Se usa `Map<String, List<String>>` porque un mismo campo puede acumular varias violaciones (`@NotBlank` + `@Size`, por ejemplo); un `Map<String, String>` descartaría todas menos una. Esto se acerca más a `ValidationProblemDetails` de .NET, que expone `IDictionary<string, string[]>`.

```java
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.validation.FieldError;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers,
        HttpStatusCode status, WebRequest request) {

    ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
    pd.setTitle("Errores de validación");

    Map<String, List<String>> errors = ex.getBindingResult().getFieldErrors().stream()
        .collect(Collectors.groupingBy(
            FieldError::getField,
            Collectors.mapping(FieldError::getDefaultMessage, Collectors.toList())));

    pd.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(pd);
}
```

---

## 7. Tabla de equivalencias .NET → Spring Boot

Útil si venís del mundo .NET / ASP.NET Core:

| .NET (ASP.NET Core) | Spring Boot |
|---|---|
| `ProblemDetails` (plural) | `ProblemDetail` (**singular**) |
| `ValidationProblemDetails` | `ProblemDetail` + `setProperty("errors", ...)` |
| `AddProblemDetails()` | Propiedad `spring.mvc.problemdetails.enabled=true` |
| `IProblemDetailsService` + middleware de excepciones | `ResponseEntityExceptionHandler` + `@RestControllerAdvice` |
| `Results.Problem()` / `Results.ValidationProblem()` | Devolver `ProblemDetail` o lanzar `ErrorResponseException` |

### Diferencias de comportamiento a tener en cuenta

1. **`type` por defecto**: a diferencia de .NET, Spring **no** rellena `type` con un URI por defecto. Queda `about:blank` salvo que se asigne explícitamente.
2. **Sin `traceId` automático**: Spring no añade un identificador de correlación automáticamente. Si se quiere, hay que inyectarlo manualmente vía `setProperty`, normalmente desde el contexto de Micrometer Tracing.

---

## 8. Checklist para el ejercicio de slices

- [ ] Añadir `spring.mvc.problemdetails.enabled=true` en `application.yml`.
- [ ] Importar `ProblemDetail` desde `org.springframework.http` (singular).
- [ ] Crear excepciones de negocio extendiendo `ErrorResponseException`.
- [ ] Crear un handler global con `@RestControllerAdvice` que extienda `ResponseEntityExceptionHandler`.
- [ ] Asignar siempre `type`, `title` e `instance` (Spring no los rellena por vosotros).
- [ ] Para validación, sobrescribir `handleMethodArgumentNotValid` si se quiere el detalle por campo.
- [ ] Verificar que las respuestas de error llegan con content-type `application/problem+json`.