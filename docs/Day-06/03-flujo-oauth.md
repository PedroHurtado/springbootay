# Flujo OAuth

Ya sabemos validar tokens. Falta la pregunta de dónde salen esos tokens y cómo llega un cliente a tenerlos. Eso es OAuth.

## Qué es OAuth (y qué no es)

OAuth 2.0 es un **protocolo de autorización**. Define cómo una aplicación consigue permiso para acceder a recursos en nombre de un usuario, sin manejar su contraseña.

Conviene aclarar de entrada un malentendido habitual:

- OAuth **no** es un sistema de login ni un protocolo de autenticación en sentido estricto. Resuelve "esta app puede acceder a este recurso".
- Para "quién es el usuario" existe **OpenID Connect (OIDC)**, que es una capa construida **encima** de OAuth. En la práctica los proveedores reales (Google, Keycloak, Auth0, etc.) implementan ambos juntos, y por eso solemos hablar de ellos como una sola cosa.

Para este curso nos basta con la idea: OAuth es el mecanismo por el que se emiten y reparten los tokens que luego nuestra API valida.

## Los actores

OAuth define roles. Entenderlos es la mitad del trabajo:

| Rol | Quién es | Qué hace |
|-----|----------|----------|
| **Resource Owner** | El usuario | Es el dueño de los datos. Da su consentimiento. |
| **Client** | La aplicación (web, móvil) | Quiere acceder a los recursos en nombre del usuario. |
| **Authorization Server** | El emisor de tokens | Autentica al usuario y emite los tokens. |
| **Resource Server** | **Nuestra API** | Recibe el token, lo valida y sirve el recurso. |

Lo importante: **nuestra API es solo el Resource Server**. No autentica usuarios, no maneja contraseñas, no emite tokens. Solo recibe un token ya emitido y comprueba que sea válido (con lo que vimos de JWT + JWKS). Toda la complejidad del login vive en el Authorization Server, que es un componente aparte.

## El concepto clave: delegación

La idea central de OAuth es la **delegación de acceso sin compartir credenciales**.

El usuario nunca le da su contraseña a la aplicación cliente. En su lugar:

1. El cliente manda al usuario al Authorization Server.
2. El usuario se autentica **allí** (donde sí están sus credenciales) y da su consentimiento.
3. El Authorization Server le entrega al cliente un **token**, no la contraseña.
4. El cliente usa ese token para llamar a la API.

Así la contraseña solo la ve el Authorization Server. La app cliente y la API nunca la tocan. Si el token se ve comprometido, caduca y se puede revocar; una contraseña filtrada es mucho peor.

## El flujo más habitual: Authorization Code

Hay varios "grant types" (formas de obtener un token). El más común para aplicaciones con usuario es **Authorization Code**. Lo vemos paso a paso, que es lo que de verdad fija el concepto.

Escenario: una web (`app.fudie.eu`) quiere llamar a nuestra API (`api.fudie.eu`) en nombre del usuario.

```
┌─────────┐                ┌──────────────┐         ┌──────────────────┐         ┌──────────────┐
│ Usuario │                │    Cliente   │         │  Authorization   │         │ Resource     │
│(navegador)              │ (app.fudie)  │         │     Server       │         │ Server (API) │
└────┬────┘                └──────┬───────┘         └────────┬─────────┘         └──────┬───────┘
     │  1. Quiero entrar         │                          │                          │
     │ ─────────────────────────>│                          │                          │
     │                           │ 2. Redirige al login     │                          │
     │ <─────────────────────────│    del Auth Server       │                          │
     │                                                       │                          │
     │  3. Me autentico y doy consentimiento                 │                          │
     │ ─────────────────────────────────────────────────────>│                          │
     │                                                       │                          │
     │  4. Redirige de vuelta al cliente con un "code"       │                          │
     │ <─────────────────────────────────────────────────────│                          │
     │                           │                          │                          │
     │  5. (el navegador entrega │                          │                          │
     │      el code al cliente)  │ 6. Cambio el code por     │                          │
     │ ─────────────────────────>│    tokens (canal seguro) │                          │
     │                           │ ─────────────────────────>│                          │
     │                           │ 7. access_token (JWT)    │                          │
     │                           │ <─────────────────────────│                          │
     │                           │                                                      │
     │                           │ 8. GET /api/...  Authorization: Bearer <token>       │
     │                           │ ────────────────────────────────────────────────────>│
     │                           │                          9. valida token (JWKS) y    │
     │                           │ 10. respuesta            responde                     │
     │                           │ <────────────────────────────────────────────────────│
```

Explicación de los pasos que importan:

- **Pasos 3–4**: el usuario introduce sus credenciales **en el Authorization Server**, nunca en el cliente. El Auth Server devuelve un **código de autorización** temporal de un solo uso, no el token todavía.
- **Paso 6**: el cliente intercambia ese código por los tokens reales en una llamada de servidor a servidor. Este doble paso (primero un código, luego el token) evita que el token viaje expuesto en la URL del navegador.
- **Paso 7**: el cliente recibe un `access_token` (típicamente un JWT) y, normalmente, un `refresh_token`.
- **Pasos 8–10**: a partir de aquí entra todo lo que ya sabemos: el cliente manda el `Bearer` token, y nuestra API lo valida con JWKS.

> Hoy este flujo se usa siempre con una extensión llamada **PKCE**, que añade una protección extra para que un atacante no pueda usar un código interceptado. Para el nivel del curso basta con mencionar que existe y que es el estándar actual para apps web y móviles.

## Otro flujo útil: Client Credentials

Cuando **no hay usuario** de por medio (un backend que llama a otra API, un proceso automático), se usa el grant **Client Credentials**. Aquí no hay login ni consentimiento: la propia aplicación se identifica con un `client_id` y un `client_secret` y obtiene un token directamente.

```
Backend A                    Authorization Server
    │  client_id + client_secret      │
    │ ────────────────────────────────>│
    │  access_token                    │
    │ <────────────────────────────────│
    │                                  │
    │  Bearer token → API (Backend B)
```

Es el caso de comunicación máquina-a-máquina. El token resultante se valida exactamente igual en nuestra API.

## Access token vs refresh token

Dos tokens con propósitos distintos:

- **Access token**: el que se envía en cada petición a la API. Vida **corta** (minutos). Si se filtra, el daño es limitado porque caduca pronto.
- **Refresh token**: vida **larga**. No se manda a la API; sirve solo para pedir un nuevo access token al Authorization Server cuando el actual caduca, sin obligar al usuario a volver a loguearse.

Nuestra API (Resource Server) solo ve y valida **access tokens**. El refresh token es asunto entre el cliente y el Authorization Server.

## Cómo encaja todo

Cerrando el bloque, el ciclo completo de cómo se autentica una API:

1. El cliente obtiene un **access token** del **Authorization Server** mediante un flujo OAuth (normalmente Authorization Code + PKCE).
2. El cliente envía ese token a nuestra API en la cabecera `Authorization: Bearer ...`.
3. Nuestra API (**Resource Server**) valida el token: descarga las claves públicas vía **JWKS**, verifica la firma y comprueba los claims.
4. Si el token es válido, los **scopes** determinan a qué puede acceder.
5. Y todo esto solo es alcanzable desde el navegador para los orígenes que **CORS** permite.

Cada una de las tres piezas del bloque ocupa su lugar: OAuth emite, JWT/JWKS valida, CORS controla desde dónde puede llamar un navegador.

## Lo que NO necesitan saber todavía

Para no saturar, conviene dejar explícitamente fuera (mencionar que existe, no entrar):

- Tokens opacos e introspección.
- Implementar un Authorization Server propio.
- Detalles internos de PKCE.
- OIDC en profundidad (id_token, userinfo).

Con Authorization Code y Client Credentials tienen el modelo mental suficiente para entender cualquier integración real que se encuentren.
