# JWT y validación por JWKS

Ya sabemos que una API es *stateless* y que cada petición trae un token. Toca ver qué es ese token, cómo está hecho y cómo nuestra API comprueba que es auténtico.

## Qué es un JWT

JWT (*JSON Web Token*) es un formato estándar de token. Es una cadena de texto larga que, a simple vista, parece ruido:

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0Iiwic2NvcGUiOiJyZWFkIn0.SflKxw...
```

Pero tiene estructura. Son **tres partes separadas por puntos**:

```
eyJhbGci...  .  eyJzdWIi...  .  SflKxw...
└─ header ─┘    └─ payload ─┘   └─ firma ─┘
```

Las dos primeras partes (header y payload) son simplemente JSON codificado en Base64Url. No están cifradas: cualquiera puede decodificarlas y leerlas. Esto sorprende a los alumnos, así que conviene dejarlo claro: **un JWT no es secreto, es verificable**.

### Header

Indica el algoritmo de firma y el tipo:

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "abc-123"
}
```

El `kid` (*key id*) es importante: identifica **con qué clave** se firmó el token. Lo usaremos en la validación.

### Payload (claims)

Contiene la información del token, llamada **claims** (afirmaciones):

```json
{
  "sub": "user-42",
  "iss": "https://auth.fudie.eu",
  "exp": 1735689600,
  "iat": 1735686000,
  "scope": "reservas:read reservas:write"
}
```

Claims estándar que conviene conocer:

| Claim | Significado |
|-------|-------------|
| `sub` | *subject* — a quién identifica el token (el usuario) |
| `iss` | *issuer* — quién emitió el token |
| `exp` | *expiration* — cuándo caduca (timestamp) |
| `iat` | *issued at* — cuándo se emitió |
| `aud` | *audience* — para qué API está destinado |
| `scope` | permisos concedidos |

### Firma

La tercera parte es la **firma**. Es lo que hace que el token sea de fiar. Se calcula sobre el header + payload usando una clave, y garantiza dos cosas:

1. **Integridad**: si alguien modifica un solo carácter del payload, la firma deja de cuadrar.
2. **Autenticidad**: solo quien tiene la clave correcta pudo generar esa firma.

> Por eso da igual que el payload sea legible. No puedes cambiar tu `scope` de `read` a `write` porque, en cuanto toques el payload, la firma no validará y la API rechazará el token.

## Firma simétrica vs asimétrica

Hay dos familias de algoritmos de firma, y la diferencia es central para entender JWKS:

**Simétrica (ej. `HS256`)**: una única clave secreta sirve para firmar y para verificar. Quien verifica necesita la misma clave que quien firma. Problema: nuestra API tendría que conocer el secreto del emisor, lo cual obliga a compartir secretos. No escala bien.

**Asimétrica (ej. `RS256`)**: hay un par de claves.
- La **clave privada** la guarda solo el emisor y sirve para **firmar**.
- La **clave pública** se puede repartir libremente y sirve para **verificar**.

Con asimétrica, nuestra API solo necesita la **clave pública** del emisor para validar firmas. No conoce ningún secreto, no puede falsificar tokens, solo comprobarlos. Esto es lo que se usa en la práctica y lo que habilita JWKS.

## JWKS: cómo obtiene la API la clave pública

Aquí está la pieza que da nombre a este tema.

Si la API valida con la clave pública del emisor, ¿de dónde la saca? Opción ingenua: copiar la clave a mano en la configuración. Problema: las claves **rotan** (se cambian periódicamente por seguridad), y si está copiada a mano, cada rotación rompe la API.

La solución es **JWKS** (*JSON Web Key Set*). El emisor publica sus claves públicas en una URL estándar:

```
https://auth.fudie.eu/.well-known/jwks.json
```

Ese endpoint devuelve un conjunto de claves públicas en formato JSON:

```json
{
  "keys": [
    {
      "kid": "abc-123",
      "kty": "RSA",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuu...",
      "e": "AQAB"
    },
    {
      "kid": "def-456",
      "kty": "RSA",
      "...": "..."
    }
  ]
}
```

Fíjate en el `kid` de cada clave: es el mismo `kid` que vimos en el header del JWT.

### El proceso de validación completo

Cuando llega un token, la API hace lo siguiente:

```
1. Lee el header del JWT  → extrae el "kid"
2. Va al JWKS del emisor   → busca la clave pública con ese "kid"
3. Verifica la firma del JWT con esa clave pública
4. Comprueba los claims:
      - exp: ¿no ha caducado?
      - iss: ¿lo emitió quien esperamos?
      - aud: ¿es para esta API?  (si aplica)
5. Si todo cuadra → petición autenticada
   Si algo falla  → 401 Unauthorized
```

La gracia de JWKS:

- La API **no necesita preguntar al emisor en cada petición**. Descarga el JWKS una vez y lo **cachea**. La validación de cada token es local y rápida.
- Cuando el emisor rota claves, publica las nuevas en el JWKS y la API las recoge sin tocar configuración. El `kid` permite tener varias claves conviviendo durante la transición.

Esto es lo que hace que JWT + JWKS escale: miles de validaciones por segundo sin ida y vuelta a un servidor central.

## Configuración en Spring: el Resource Server

Spring Security trae el rol de **Resource Server** preparado para esto. Recordemos: nuestra API no emite tokens, solo los valida.

Dependencia:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Y prácticamente toda la configuración es declarativa. Basta con indicar de dónde sacar las claves:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.fudie.eu
```

Con solo el `issuer-uri`, en el arranque Spring consulta el endpoint de metadatos del emisor, descubre la URL del JWKS y se autoconfigura para validar firmas, comprobar `exp`/`nbf` y validar el claim `iss`.

Si se prefiere no depender de que el emisor esté arriba al arrancar, se puede apuntar directamente al JWKS:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          jwk-set-uri: https://auth.fudie.eu/.well-known/jwks.json
```

La cadena de seguridad que activa la validación de JWT:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/public/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            );
        return http.build();
    }
}
```

Lo que ocurre por debajo, sin que nosotros lo programemos:

- Spring lee el `Bearer <token>` de la cabecera `Authorization`.
- Usa un `JwtDecoder` (basado en Nimbus) que descarga y cachea el JWKS.
- Verifica firma + claims.
- Si es válido, deja la información del token disponible para los controladores; si no, responde `401`.

## Leer los datos del token en un controlador

Una vez validado, los claims están accesibles:

```java
@GetMapping("/api/reservas/mias")
public List<Reserva> misReservas(@AuthenticationPrincipal Jwt jwt) {
    String userId = jwt.getSubject();          // el claim "sub"
    String email  = jwt.getClaimAsString("email");
    // ...
}
```

## Autorización por scopes

Los `scope` del token se mapean automáticamente a autoridades con el prefijo `SCOPE_`. Eso permite proteger endpoints por permiso:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.GET,  "/api/reservas/**").hasAuthority("SCOPE_reservas:read")
    .requestMatchers(HttpMethod.POST, "/api/reservas/**").hasAuthority("SCOPE_reservas:write")
    .anyRequest().authenticated()
)
```

Aquí se ve la separación con la que abrimos el bloque: el JWT (autenticación, "quién eres") nos da la identidad, y los scopes (autorización, "qué puedes hacer") deciden el acceso.
