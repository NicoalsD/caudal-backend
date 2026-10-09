# Configuración: versión operativa para agentes de backend

Regla única: **un valor vive en una sola capa**. Antes de escribir un número, un texto o una URL en el backend, decide dónde va. La explicación completa, con ejemplos y tablas, está en [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md).

## 1. Dónde va cada tipo de valor

| Tipo de valor | Ejemplos | Destino | Cómo se usa en el código |
|---|---|---|---|
| Regla de negocio de la Junta | Horas por banda, reserva, máximo de horas por día (24 hoy), prioridades, horario, orden de válvulas, horizonte de pronóstico | Tablas `org.rule_sets` y sus hijas (versión vigente) | Se lee la versión vigente desde el caso de uso. Nunca una constante |
| Catálogo | Aspecto del agua, categorías de daño | `org.catalog_items` | Se consulta por código. Nunca un `if` con textos |
| Límite técnico o de seguridad | Longitud de nombre (80), patrón de usuario, tamaño de cuerpo, paginación | `FieldLimits` (`co.caudal.shared`) | `@Size`, `@Pattern`, `@Column(length)` y placeholders de Flyway |
| Constante física | `HOURS_PER_DAY = 24` | Constante con nombre en `co.caudal.shared` | Se usa el nombre, nunca el número |
| Variable de entorno | URL de la base, orígenes CORS, duraciones de token, timeouts de la IA | `application.yml` con `${VAR}` y `.env.example` | Propiedades agrupadas en `@ConfigurationProperties` |
| Secreto | Claves JWT, contraseñas, token de la IA, clave de MFA | Variables de entorno del gestor de la plataforma | Sin valor por defecto. Si falta, la aplicación no arranca |
| Texto para el usuario | Mensajes de error y de la UI | `messages_es.properties` (backend) y `src/i18n/es.ts` (frontend) | Clave con marcador: `Máximo {max}` |

## 2. Prohibido

- Escribir una regla de negocio en código, en un enum, en una migración o en `application.yml`.
- Escribir un límite de `FieldLimits` como literal en un DTO, una entidad o una migración.
- Usar un número mágico en código (Checkstyle `MagicNumber` y ESLint `no-magic-numbers` lo marcan). Las constantes con nombre sí son válidas.
- Poner un secreto en el código, en el YAML, en `.env.example` con un valor real o en un `VITE_*`.
- Dar un valor por defecto inseguro a un secreto.
- Leer `System.getenv` directamente desde un bean o un controlador. Las propiedades se leen desde su record de configuración.
- Escribir un texto visible directamente en un componente o en una excepción de negocio.
- Repetir un valor en dos capas. Si un valor ya existe en `FieldLimits`, se usa la constante; no se copia el número.
- Suprimir un linter (`@SuppressWarnings("checkstyle:MagicNumber")`, `eslint-disable`) sin motivo escrito y revisión.

## 3. Cómo cambiar un parámetro de negocio

1. Crear una versión nueva de reglas (borrador copiado de la vigente, `POST /api/v1/rule-sets`).
2. Editar el campo en el borrador (`PATCH /api/v1/rule-sets/{id}`).
3. Activar con motivo (`POST /api/v1/rule-sets/{id}/activate`). La versión anterior queda inmutable.

Si un cambio de regla pide tocar código o una migración de esquema, el diseño está mal: revisarlo antes de seguir.

## 4. Cómo agregar una variable de entorno

1. Agregar la propiedad a su record de `@ConfigurationProperties`, con su valor por defecto **solo si no es secreta**.
2. Agregar la variable a `.env.example` con un marcador (`<valor-del-gestor-de-secretos>`).
3. Agregar una fila a la tabla de la sección 4 de [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md): nombre, para qué, ejemplo seguro y si es secreta.
4. Si es secreta, verificar que los logs la redactan (ver [`.agents/security.md`](security.md)).

## 5. Lista rápida antes de un PR

- [ ] No hay literales de negocio en código ni en YAML.
- [ ] Los límites de entrada usan `FieldLimits`.
- [ ] Las migraciones usan placeholders de Flyway para longitudes y patrones.
- [ ] Las variables nuevas están en `.env.example` y en la tabla de configuración.
- [ ] Los secretos nuevos no tienen valor por defecto.
- [ ] Los textos nuevos están en `messages_es.properties`.
- [ ] Las pruebas de deriva (`FieldLimitsEndpointDriftIT`, `FieldLimitsDatabaseDriftIT`, `NoBusinessLiteralsArchTest`) pasan.

Relacionados: [`docs/Configuracion-sin-valores-quemados.md`](../docs/Configuracion-sin-valores-quemados.md), [`input-validation.md`](input-validation.md), [`security.md`](security.md)
