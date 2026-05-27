# ¿Dónde validamos?

Esta es la pregunta que aparece cada vez que tocamos validación. No tiene una única respuesta correcta. Tiene **dos respuestas legítimas** y un criterio para elegir entre ellas.

## Las dos capas que pueden validar

Cuando un request entra en el sistema vertical-slice, recorre este camino:

```
HTTP Request
    │
    ▼
┌─────────────────┐
│  @RestController│  ← deserializa JSON a Command
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Command        │  ← DTO de entrada al slice
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Handler        │  ← orquesta el caso de uso
└────────┬────────┘
         │
         ▼
┌─────────────────┐
│  Dominio        │  ← Aggregate / Entity / Value Object
└─────────────────┘
```

Hay dos sitios donde podemos validar:

1. **En el Command**: validación declarativa con Bean Validation (`@NotBlank`, `@Size`, `@Email`...). Se ejecuta automáticamente cuando Spring deserializa el JSON, antes de que el handler vea nada.

2. **En el Dominio**: validación imperativa dentro del constructor del agregado, o de un Value Object, o en un método de comportamiento. Se ejecuta cuando construimos el objeto.

Las dos son válidas. Hacen cosas distintas.

## Qué valida cada capa

### El Command valida **forma**

Pregunta a la que responde el Command:

> ¿Este JSON tiene la estructura mínima para que merezca la pena intentar procesarlo?

- ¿El campo `name` viene presente y no vacío?
- ¿El `email` parece un email?
- ¿El precio es un número?
- ¿La fecha está en formato ISO?
- ¿El tamaño del `description` no supera 500 caracteres?

Si algo aquí falla, el caso de uso **no debería ni arrancar**. Devolvemos 400 al cliente y nos ahorramos invocar al dominio para nada.

Esto es **fail-fast en el borde**. Y Bean Validation lo hace de forma declarativa, leíble y testeada por millones de proyectos.

### El Dominio valida **reglas de negocio**

Pregunta a la que responde el Dominio:

> ¿Este conjunto de valores forma un objeto que tiene sentido según las reglas del negocio?

- Una `Pizza` no puede tener precio negativo (eso lo cazaría también el Command, pero el dominio **no debe confiar en eso**).
- Una `Pizza` no puede tener menos de un ingrediente.
- Un `Ingredient` con tipo `BASE` no puede repetirse en la misma pizza.
- El total del pedido tiene que cuadrar con la suma de líneas.
- No se puede cancelar una reserva que ya está pagada.

Esto **no se puede expresar bien con anotaciones**. Necesita lógica. Necesita acceso a otros campos del agregado. Necesita lanzar excepciones de dominio con nombre propio (`PizzaSinIngredientesException`, no `ConstraintViolationException`).

## La regla del "siempre válido"

El dominio sigue el principio **always-valid**: si un objeto de dominio existe, está en estado válido. Punto.

Esto implica:

- No se puede construir una `Pizza` inválida. Si los datos no cumplen, el constructor lanza.
- No hay setters que dejen el objeto en estado roto a medio camino.
- No hay un método `isValid()` que el resto del código tenga que recordar llamar.

**Consecuencia importante**: el dominio **no confía** en que el Command haya validado. Repite las invariantes que le son esenciales. ¿Es eso duplicación?

No. Son **validaciones distintas con propósitos distintos**:

| Capa | Propósito | Ejemplo |
|------|-----------|---------|
| Command | Rechazar input malformado pronto | `@NotBlank String name` |
| Dominio | Garantizar invariantes del negocio | `if (name.isBlank()) throw new InvalidPizzaException(...)` |

El dominio puede ser usado desde otro sitio que no sea un Controller (un job, un consumer de eventos, un test). Por eso no puede asumir que alguien ha validado antes.

## Cuándo validar solo en uno de los dos sitios

### Solo en Command

Casos donde una regla es **puramente de entrada HTTP** y no tiene reflejo en el dominio:

- "El cliente debe enviar este header"
- "El tamaño máximo del body es X"
- "El campo `acceptedTerms` debe venir a `true`" (es un check de UI, no del modelo)

Estas reglas no pintan nada en el dominio. Se quedan en el borde.

### Solo en Dominio

Casos donde la regla **depende de estado o de otras entidades**:

- "No puedes cancelar una reserva con menos de 24h de antelación"
- "El stock no puede ir por debajo de cero"
- "El usuario no puede seguirse a sí mismo"

Estas reglas necesitan más contexto que el Command. Algunas incluso necesitan consultar el repositorio. Se quedan en el dominio (o en el handler si requieren orquestación).

## Cuándo validar en ambos

Cuando la regla es **estructural y de negocio a la vez**:

- "El nombre de la pizza es obligatorio" → `@NotBlank` en Command + check en constructor de `Pizza`.
- "El precio debe ser positivo" → `@Positive` en Command + check en `Money` Value Object.

Sí, se escribe dos veces. Y está bien que así sea. La del Command es para el cliente HTTP (mensaje amable, código 400). La del dominio es la red de seguridad del modelo.

## Resumen para los alumnos

1. **Command**: Bean Validation con anotaciones, declarativo, forma del input.
2. **Dominio**: lógica imperativa en constructores/métodos, invariantes de negocio.
3. **No duplicar es un objetivo secundario**. El primario es que el dominio **nunca** pueda existir en estado inválido.
4. Si dudas dónde poner una validación, hazte esta pregunta: *"¿Si un test usa esta clase sin pasar por el Controller, debe seguir cumpliéndose esta regla?"*. Si la respuesta es sí → dominio. Si la respuesta es no → Command.
