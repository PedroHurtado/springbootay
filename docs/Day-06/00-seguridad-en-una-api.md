# Seguridad en una API REST

Antes de tocar Spring Security conviene tener claro qué problema estamos resolviendo. Una API no tiene sesiones, no tiene formularios de login, no guarda cookies de usuario. Es un servicio que recibe peticiones HTTP y devuelve datos. La pregunta clave es siempre la misma:

> Cuando llega una petición, ¿quién la hace y tiene permiso para hacerla?

## Autenticación vs autorización

Son dos cosas distintas y conviene no mezclarlas:

- **Autenticación** (*authentication*): ¿quién eres? Verificar la identidad de quien hace la petición.
- **Autorización** (*authorization*): ¿qué puedes hacer? Una vez sé quién eres, decidir si te dejo acceder a un recurso.

Primero se autentica, luego se autoriza. Si no sé quién eres, no puedo decidir qué puedes hacer.

## ¿Por qué una API es diferente de una web tradicional?

En una aplicación web clásica (la típica de servidor con plantillas) el flujo es:

1. El usuario rellena un formulario de login.
2. El servidor crea una **sesión** y guarda un identificador en una cookie.
3. En cada petición siguiente, la cookie viaja de vuelta y el servidor sabe quién eres.

El estado de "estás logueado" vive en el servidor. Esto se llama **autenticación con estado** (*stateful*).

Una API REST trabaja distinto. Es **sin estado** (*stateless*): el servidor no guarda nada entre peticiones. Cada petición tiene que traer consigo toda la información necesaria para identificar a quien la hace. Esa información es un **token**.

```
Petición a una API protegida:

GET /api/reservas
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6...
```

Ese `Bearer <token>` en la cabecera `Authorization` es lo que sustituye a la cookie de sesión. El token lo lleva el cliente y lo presenta en cada llamada. El servidor lo valida y, si es correcto, sabe quién eres.

## Lo que veremos en este bloque

Tres piezas que cubren el ciclo completo de cómo se autentica una API:

1. **CORS** — el navegador, antes de dejar que una web llame a tu API desde otro origen, hace comprobaciones. Hay que entender qué controla CORS y qué no.
2. **JWT y validación por JWKS** — qué es un token JWT, cómo está hecho y cómo nuestra API verifica que es auténtico sin tener que preguntar a nadie en cada petición.
3. **Flujo OAuth** — quién emite esos tokens, cómo los obtiene el cliente y cómo encaja todo en un sistema real.

Un matiz importante para todo el bloque: **nuestra API no genera tokens, solo los valida**. Quien emite los tokens es un servidor de autorización (un *Authorization Server*). Nuestra API es un *Resource Server*: recibe el token, comprueba que es válido y sirve el recurso. Esta separación de roles es la base de todo lo que viene.
