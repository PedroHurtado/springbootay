# CORS

CORS (*Cross-Origin Resource Sharing*) es de las cosas que más confusión genera, porque se culpa a la API de un problema que en realidad impone el navegador. Vamos a desmontarlo.

## El origen

Un **origen** es la combinación de tres cosas: protocolo + dominio + puerto.

```
https://app.fudie.eu:443
└──┬──┘ └────┬─────┘ └┬┘
protocolo  dominio  puerto
```

Dos URLs son del **mismo origen** solo si coinciden las tres. Si cambia cualquiera, es un origen distinto:

| URL                          | ¿Mismo origen que `https://app.ejemplo.com`? |
|------------------------------|----------------------------------------------|
| `https://app.ejemplo.com/x`  | Sí (la ruta no cuenta)                        |
| `http://app.ejemplo.com`     | No (protocolo distinto)                       |
| `https://api.ejemplo.com`    | No (subdominio distinto)                      |
| `https://app.ejemplo.com:8080` | No (puerto distinto)                        |

## Qué problema resuelve CORS

Por defecto, el navegador aplica la **política del mismo origen** (*Same-Origin Policy*): una página web cargada desde un origen **no puede** hacer peticiones JavaScript a otro origen distinto. Es una protección del navegador para que una web maliciosa no haga llamadas en tu nombre a tu banco aprovechando que estás logueado.

El problema es que en arquitecturas reales el frontend y la API casi siempre están en orígenes distintos:

```
Frontend:  https://app.fudie.eu
API:       https://api.fudie.eu   ← origen distinto
```

Aquí la web necesita llamar a la API, pero son orígenes diferentes. El navegador bloquearía la llamada salvo que la API diga explícitamente "permito que app.fudie.eu me llame". Ese permiso es CORS.

## Lo más importante de entender

**CORS no es seguridad de tu API. CORS lo aplica el navegador, no tu servidor.**

Esto es clave y se malinterpreta constantemente:

- Una herramienta como Postman, curl o cualquier llamada desde otro backend **ignoran CORS por completo**. CORS no las afecta.
- CORS solo entra en juego cuando una **página web ejecutándose en un navegador** hace una petición JavaScript a otro origen.

Es decir: CORS no protege tu API de accesos no autorizados. Para eso están la autenticación y la autorización (los tokens). CORS solo controla qué páginas web pueden, desde el navegador, hacer llamadas a tu API por JavaScript.

> Si quitas CORS, tu API no queda "más insegura" frente a un atacante con curl. Simplemente las webs de otros orígenes dejarán de poder llamarla desde el navegador.

## Cómo funciona el intercambio

Cuando una web hace una petición a otro origen, el navegador añade una cabecera con el origen de quien llama:

```
GET /api/reservas
Origin: https://app.fudie.eu
```

El servidor responde indicando si ese origen está permitido:

```
Access-Control-Allow-Origin: https://app.fudie.eu
```

Si la cabecera de respuesta coincide con el origen de quien llamó, el navegador deja pasar la respuesta al JavaScript. Si no, el navegador la bloquea (aunque el servidor haya respondido correctamente; el dato llega al navegador pero este no se lo entrega al código).

## La petición preflight

Para peticiones "simples" (un `GET` sencillo, por ejemplo) el navegador hace la llamada directamente. Pero para peticiones que pueden modificar datos o que llevan cabeceras especiales (como `Authorization` con un token), el navegador hace primero una **petición previa de comprobación**, llamada *preflight*.

El preflight es una petición `OPTIONS` automática que el navegador envía **antes** de la real, preguntando "¿me dejas hacer esto?":

```
OPTIONS /api/reservas
Origin: https://app.fudie.eu
Access-Control-Request-Method: POST
Access-Control-Request-Headers: Authorization, Content-Type
```

El servidor responde qué permite:

```
Access-Control-Allow-Origin: https://app.fudie.eu
Access-Control-Allow-Methods: GET, POST, PUT, DELETE
Access-Control-Allow-Headers: Authorization, Content-Type
Access-Control-Max-Age: 3600
```

Solo si la respuesta del preflight autoriza el método y las cabeceras, el navegador lanza la petición real. El `Access-Control-Max-Age` indica cuánto tiempo puede el navegador cachear esta respuesta para no repetir el preflight en cada llamada.

Esto explica por qué a veces ves en las herramientas de desarrollo del navegador una petición `OPTIONS` que tú nunca programaste: la añade el navegador solo.

## Configuración en Spring

En una API con Spring lo habitual es configurar CORS de forma centralizada. Con Spring Security la configuración va dentro de la cadena de filtros:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())   // activa CORS usando el bean de abajo
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            );
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("https://app.fudie.eu"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
```

Puntos a explicar a los alumnos:

- `setAllowedOrigins` lista los orígenes concretos permitidos. **Evitar `*` en producción**, sobre todo si la API maneja credenciales.
- `setAllowedMethods` y `setAllowedHeaders` son lo que se valida en el preflight.
- `.cors(...)` en la cadena de seguridad es lo que le dice a Spring Security que respete esta configuración (si no, el filtro de seguridad podría bloquear el preflight antes de llegar a CORS).

## Errores típicos que verán

- **"He configurado CORS y sigue fallando"** → casi siempre es que el preflight `OPTIONS` no está siendo permitido, o que falta declarar la cabecera `Authorization` en `allowedHeaders`.
- **"En Postman funciona pero en el navegador no"** → es exactamente el comportamiento esperado: Postman no aplica CORS, el navegador sí. No es un fallo de la API, es CORS haciendo su trabajo.
- **`allowedOrigins("*")` junto con credenciales** → el navegador lo rechaza; comodín y credenciales no se permiten juntos.
