# Historias de usuario

Este documento define 36 historias de usuario (HU-01 a HU-36), agrupadas por módulo canónico (M1 a M10). Cada historia tiene actor, enunciado, prioridad MoSCoW, criterios de aceptación en Gherkin, requisitos relacionados y patrones de diseño involucrados.

Cambio de alcance B2B (2026-11-20): CAUDAL es un producto B2B. Nosotros damos de alta cada **institución** y le configuramos su tanque, su sensor ultrasónico y sus válvulas. Cada institución ve solo sus datos. Se quitan las reglas versionadas, las propuestas de turnos, la aprobación de la Junta, la publicación pública, las lecturas manuales y sin conexión, los reportes de daño y las entidades de apoyo. Se agregan la configuración por institución, el sensor en vivo, el control de válvulas con confirmación (ack), las observaciones manuales y la maqueta con Arduino UNO.

Convenciones:

- **Prioridad (MoSCoW):** `must` (sin esto no hay MVP), `should` (importante, con alternativa temporal), `could` (deseable).
- **Requisitos:** `RF-xx` son requisitos funcionales y `RNF-xx` no funcionales, definidos en [Requerimientos.md](Requerimientos.md).
- **Patrones:** `Pxx` del catálogo de [Patrones-de-diseno.md](Patrones-de-diseno.md). Cuando una clase no aparece en ese catálogo, se marca "(clase propuesta, por definir)".
- **Códigos de error:** los códigos son `UPPER_SNAKE_CASE` en inglés, según el catálogo de [API.md](API.md), y el mensaje para el usuario va en español.
- **Valores de ejemplo:** las cifras de los ejemplos son de la maqueta o ilustrativas. Los parámetros reales viven en la base de datos de cada institución ([Configuracion-sin-valores-quemados.md](Configuracion-sin-valores-quemados.md)).

| Código | Significado | HTTP |
|---|---|---|
| `INVALID_CREDENTIALS` | Institución, usuario o contraseña incorrectos. Mensaje genérico, siempre el mismo. | 401 |
| `UNAUTHORIZED` | Token ausente, vencido, con `tv` no vigente o inválido. | 401 |
| `PASSWORD_CHANGE_REQUIRED` | La cuenta debe cambiar la contraseña antes de usar la API. | 403 |
| `FORBIDDEN` | El rol no tiene el permiso, o no hay membresía vigente en la institución. | 403 |
| `SIMULATED_ONLY` (propuesto) | Solo se importa historial en instituciones simuladas (`is_simulated = true`). | 403 |
| `NOT_FOUND` | El recurso no existe o pertenece a otra institución. | 404 |
| `RATE_LIMITED` | Se superó un límite de tasa. Incluye `Retry-After`. | 429 |
| `VALIDATION_ERROR` | Un campo no cumple sus límites. `details` indica el campo. | 400 |
| `REASON_REQUIRED` | Falta el motivo (10 a 500 caracteres) cuando el caso lo exige. | 422 |
| `CONFLICT` | El día ya tiene cierre, el usuario o el dispositivo ya existe, o el canal ya está asignado. | 409 |
| `INVALID_STATE_TRANSITION` | Transición de estado no permitida (por ejemplo, un comando ya terminado). | 409 |
| `INVALID_SIGNATURE`, `NONCE_REPLAY`, `TIMESTAMP_OUT_OF_WINDOW` | Verificación de firma de dispositivo. | 401 |

Formato de error de la API: `{"error":{"code":"ENGLISH_CODE","message":"Mensaje en español","details":{},"request_id":"..."}}`.

---

## M1: Login e instituciones

### HU-01: Elegir la institución e iniciar sesión

- **Actor:** cualquier persona con una cuenta en una institución (`INSTITUTION_ADMIN`, `OPERATOR`, `VIEWER`) y el superadmin de CAUDAL (`PLATFORM_ADMIN`).
- **Historia:** Como operador, quiero elegir primero mi institución y luego poner mi usuario y contraseña, para entrar a los datos de mi tanque y no a los de otra institución.
- **Prioridad:** must

```gherkin
Escenario: elegir institución en el directorio
  Dado que la persona abre la aplicación sin sesión
  Cuando busca "maqueta" en la lista de instituciones
  Entonces ve el nombre de cada institución activa y nada más (slug y nombre)
  Y al elegir una institución pasa al formulario de usuario y contraseña

Escenario: entrar por la ruta directa de la institución
  Dado la institución con slug "maqueta-caudal"
  Cuando abre /i/maqueta-caudal/login
  Entonces el formulario ya tiene la institución elegida
  Y al entrar con credenciales válidas recibe un access token y el refresh token en la cookie caudal_rt

Escenario: usuario que existe en otra institución
  Dado que el usuario "admin" existe en la institución "maqueta-caudal" pero no en "otra-institucion"
  Cuando envía POST /api/v1/auth/login con institution_slug "otra-institucion", username "admin" y su contraseña
  Entonces recibe 401 con código INVALID_CREDENTIALS y el mensaje "Institución, usuario o contraseña incorrectos"
  Y el mensaje no revela que el usuario existe en otra institución

Escenario: institución que no existe
  Cuando envía POST /api/v1/auth/login con un institution_slug inexistente
  Entonces recibe el mismo código, mensaje y tiempo de respuesta que con contraseña incorrecta

Escenario: entrar a la plataforma
  Dado que el superadmin elige la opción "CAUDAL (plataforma)"
  Cuando envía POST /api/v1/auth/login con institution_slug nulo
  Entonces el token lleva scope "platform" y no lleva institution_id
```

- **Requisitos:** RF-01, RF-39; RNF-01, RNF-02, RNF-05, RNF-11.
- **Patrones:** P11 (`Proxy`, `PublicInstitutionDirectoryProxy`, filtra el directorio público a `{slug, name}`); P09 (`AuthHttpClient`, decorador del cliente HTTP en el frontend).

### HU-02: Entrar a la pantalla inicial de mi rol

- **Actor:** `INSTITUTION_ADMIN`, `OPERATOR`, `VIEWER` y `PLATFORM_ADMIN`.
- **Historia:** Como lector, quiero entrar a una pantalla que solo me muestre lo que puedo ver, para saber qué pasa con el agua sin tocar nada por error.
- **Prioridad:** must

```gherkin
Escenario: operador entra a /inicio
  Dado un usuario OPERATOR autenticado
  Cuando entra a la aplicación
  Entonces ve /inicio con el tanque en vivo, las líneas con su válvula y los botones Abrir y Cerrar

Escenario: lector entra a la misma vista sin controles
  Dado un usuario VIEWER autenticado
  Cuando entra a la aplicación
  Entonces ve /inicio con el tanque en vivo, el pronóstico, los eventos y las actas
  Y no ve los botones Abrir ni Cerrar, ni el menú de administración

Escenario: lector intenta mutar desde la API
  Dado un usuario VIEWER
  Cuando envía POST /api/v1/valve-commands/manual
  Entonces recibe 403 con código FORBIDDEN

Escenario: superadmin sin institución
  Dado un usuario PLATFORM_ADMIN en el token de plataforma
  Cuando entra a la aplicación
  Entonces ve /plataforma/instituciones
  Y no ve datos operativos de ninguna institución
```

- **Requisitos:** RF-02, RF-37; RNF-07, RNF-31.
- **Patrones:** P10 (`Facade`, `CaudalApi` en el frontend); P19 (`Strategy`, pantalla de inicio según el rol, clase propuesta, por definir).

### HU-03: Cambio de contraseña obligatorio en el primer ingreso

- **Actor:** cualquier usuario con una cuenta nueva o restablecida.
- **Historia:** Como usuario nuevo, quiero fijar mi contraseña con la contraseña temporal que me dio el administrador de mi institución y cambiarla en el primer ingreso, para que nadie más conozca mi acceso.
- **Prioridad:** must

```gherkin
Escenario: primer ingreso fuerza el cambio
  Dado una cuenta con must_change_password en verdadero
  Cuando inicia sesión con la contraseña temporal
  Entonces la aplicación muestra solo el formulario de cambio de contraseña
  Y cualquier otra llamada a la API responde 403 con código PASSWORD_CHANGE_REQUIRED

Escenario: contraseña nueva débil o que contiene el usuario
  Dado una cuenta en cambio obligatorio
  Cuando envía una contraseña de 11 caracteres
  Entonces recibe 400 con código VALIDATION_ERROR y mensaje "La contraseña debe tener entre 12 y 128 caracteres"
  Cuando envía una contraseña que aparece en la lista de comunes o que contiene su usuario
  Entonces recibe 422 con mensaje "La contraseña es demasiado común" o "La contraseña no puede contener el usuario"

Escenario: no se reutiliza una de las últimas 5 contraseñas
  Dado que la contraseña nueva coincide con una de las últimas 5 en iam.password_history
  Cuando envía el cambio
  Entonces recibe 422 con mensaje "No puede repetir una contraseña usada recientemente"
  Y la contraseña actual no cambia
```

- **Requisitos:** RF-03; RNF-01, RNF-04.
- **Patrones:** P13 (comando de cambio de contraseña, clase `ChangePasswordCommand`, clase propuesta, por definir); P09 (`AuditingCommandHandler`, registra el cambio sin guardar contraseñas).

### HU-04: Bloqueo por intentos fallidos

- **Actor:** cualquier usuario. El administrador de la institución y el superadmin revisan los eventos en auditoría.
- **Historia:** Como administrador, quiero que las cuentas se bloqueen tras varios intentos fallidos, para que nadie adivine una contraseña.
- **Prioridad:** must

```gherkin
Escenario: bloqueo de cuenta tras cinco fallos
  Dado una cuenta activa con failed_login_count en 4
  Cuando falla el quinto intento en menos de 15 minutos desde el primero
  Entonces la cuenta pasa a LOCKED con locked_until a 15 minutos
  Y se registra ACCOUNT_LOCKED en iam.security_events con severidad MEDIUM
  Y el siguiente intento responde 401 con INVALID_CREDENTIALS, sin indicar que está bloqueada

Escenario: bloqueo escalonado
  Dado una cuenta que ya se bloqueó una vez (lockout_level 1)
  Cuando vuelve a fallar cinco veces tras el desbloqueo
  Entonces el bloqueo dura 30 minutos
  Y cada nuevo bloqueo duplica el anterior, con un máximo de 24 horas

Escenario: límite por IP e institución
  Dado una IP con 20 intentos fallidos en 15 minutos contra una misma institución
  Cuando envía un intento más
  Entonces recibe 429 con código RATE_LIMITED
  Y no se evalúa la contraseña
```

- **Requisitos:** RF-04; RNF-03, RNF-19.
- **Patrones:** P18 (`State`, estados de cuenta `ACTIVE`, `LOCKED`, `DISABLED`, clase propuesta, por definir); P17 (`Observer`, publica el evento de seguridad).

### HU-05: Cerrar sesión en todos los dispositivos

- **Actor:** cualquier usuario autenticado.
- **Historia:** Como usuario, quiero cerrar mi sesión en todos mis dispositivos, para proteger mi cuenta si pierdo el celular.
- **Prioridad:** should

```gherkin
Escenario: cierre global
  Dado que la misma cuenta tiene sesiones en dos celulares
  Cuando envía POST /api/v1/auth/logout-all desde uno de ellos
  Entonces token_version se incrementa en iam.users
  Y todos los refresh tokens quedan revocados con revoked_reason LOGOUT_ALL
  Y el access token del otro celular responde 401 con código UNAUTHORIZED en su siguiente llamada

Escenario: reutilización de refresh token
  Dado un refresh token ya rotado
  Cuando alguien lo vuelve a presentar en POST /api/v1/auth/refresh
  Entonces se revoca toda la familia de tokens con revoked_reason REUSE_DETECTED
  Y se registra TOKEN_REUSE_DETECTED en iam.security_events con severidad HIGH
  Y recibe 401 con código UNAUTHORIZED
```

- **Requisitos:** RF-05; RNF-02, RNF-19.
- **Patrones:** P13 (`CommandBus`); P11 (`Proxy`, en el cliente del frontend, verifica la sesión antes de cada llamada, clase propuesta, por definir).

### HU-06: Activar MFA (TOTP) para administradores

- **Actor:** `INSTITUTION_ADMIN` y `PLATFORM_ADMIN`. Para `OPERATOR` y `VIEWER` el MFA no está activo.
- **Historia:** Como administrador, quiero activar un código de verificación en mi celular, para que una contraseña robada no baste para entrar y cambiar la configuración de la institución.
- **Prioridad:** should

```gherkin
Escenario: activación con código válido
  Dado un usuario sin MFA activo
  Cuando inicia POST /api/v1/auth/mfa/enroll y confirma con un código TOTP válido
  Entonces se guarda el secreto cifrado con AES-256-GCM
  Y se muestran 10 códigos de respaldo una sola vez, guardados como hash

Escenario: código incorrecto
  Dado un usuario con MFA en proceso de activación
  Cuando envía un código TOTP incorrecto
  Entonces recibe 401 con código UNAUTHORIZED y mensaje "El código no es válido"
  Y se registra MFA_FAILED en iam.security_events

Escenario: código repetido
  Dado un código TOTP ya usado en el último paso de tiempo
  Cuando se envía de nuevo, aunque sea válido
  Entonces se rechaza (anti-repetición del paso)

Escenario: obligatoriedad (por definir)
  Dado que el MFA del INSTITUTION_ADMIN puede pasar a obligatorio
  Entonces la regla de obligatoriedad está por definir (ver Actores-y-permisos.md, sección 2)
```

- **Requisitos:** RF-06; RNF-01, RNF-02, RNF-09.
- **Patrones:** P06 (`Adapter`, `TotpVerifierAdapter`, clase propuesta, por definir).

### HU-07: El superadmin crea una institución desde una plantilla

- **Actor:** `PLATFORM_ADMIN` (superadmin de CAUDAL).
- **Historia:** Como superadmin de CAUDAL, quiero dar de alta una institución copiando una plantilla y crear su primer administrador y sus dispositivos, para dejarla lista para configurar.
- **Prioridad:** must

```gherkin
Escenario: alta desde plantilla
  Dado un token de plataforma (scope "platform")
  Cuando envía POST /api/v1/platform/institutions con nombre, slug y is_simulated en falso
  Entonces la institución se crea con una copia de la plantilla: tanque, líneas, válvulas y org.institution_settings
  Y la operación se hace con la función SECURITY DEFINER platform.create_institution
  Y se registra en audit.audit_log con el actor PLATFORM_ADMIN

Escenario: primer administrador y dispositivos
  Dado una institución recién creada
  Cuando envía POST /api/v1/platform/institutions/{id}/admin con username y contraseña temporal
  Entonces se crea un INSTITUTION_ADMIN con must_change_password en verdadero
  Y se registran los dispositivos de la institución (sensor y válvulas) para que reciban su clave pública

Escenario: entrar a configurar una institución (act_as)
  Dado el superadmin con un token de plataforma
  Cuando envía POST /api/v1/platform/institutions/{id}/act-as
  Entonces recibe un token de 15 minutos con institution_id y act_as "platform"
  Y tiene los permisos de INSTITUTION_ADMIN solo en esa institución
  Y la emisión y las acciones quedan auditadas con la institución objetivo
  Y al expirar, el acceso no se renueva (por definir si se permite volver a entrar sin límite)

Escenario: el superadmin no ve datos de una institución sin act_as
  Dado un token de plataforma sin act_as
  Cuando pide GET /api/v1/tanks
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-07, RF-39; RNF-07, RNF-19.
- **Patrones:** P05 (`Prototype`, `InstitutionTemplate`, copia tanque, líneas y configuración); P13 (`CommandBus`).

---

## M2: Configuración de la institución

### HU-08: Administrar usuarios de la institución

- **Actor:** `INSTITUTION_ADMIN` (`USER_MANAGE`).
- **Historia:** Como administrador de la institución, quiero crear usuarios con su rol, para que cada persona vea y opere solo lo que le corresponde.
- **Prioridad:** must

```gherkin
Escenario: crear un operador
  Dado que el administrador está autenticado en la institución "maqueta-caudal"
  Cuando crea un usuario con rol OPERATOR y username "operador-1"
  Entonces la cuenta queda con must_change_password en verdadero
  Y recibe un código de activación de un solo uso, que se muestra una sola vez
  Y el usuario queda único dentro de la institución: (institution_id, username)

Escenario: el mismo nombre de usuario en otra institución
  Dado que "operador-1" ya existe en "maqueta-caudal"
  Cuando el administrador de "otra-institucion" crea un usuario "operador-1"
  Entonces la creación es válida, porque la unicidad es por institución

Escenario: cambio de rol
  Dado un usuario OPERATOR con sesiones activas
  Cuando el administrador lo cambia a VIEWER
  Entonces token_version se incrementa, sus refresh tokens se revocan con revoked_reason ROLE_CHANGED
  Y en su siguiente llamada, el acceso se corta con 401

Escenario: operador intenta administrar usuarios
  Dado un usuario OPERATOR
  Cuando llama a POST /api/v1/users
  Entonces recibe 403 con código FORBIDDEN
  Y se registra PERMISSION_DENIED en iam.security_events

Escenario: usuario de otra institución
  Dado un usuario que pertenece a "otra-institucion"
  Cuando el administrador de "maqueta-caudal" pide PATCH /api/v1/users/{id} de esa cuenta
  Entonces recibe 404 con código NOT_FOUND

Escenario: vigencia de la membresía (por definir)
  Dado que la vigencia de la membresía (valid_from y valid_to) está por definir
  Entonces el rol se asigna sin vigencia hasta que se decida (ver Actores-y-permisos.md, sección 1)
```

- **Requisitos:** RF-08, RF-37, RF-39; RNF-07, RNF-19.
- **Patrones:** P13 (`CommandBus` para los comandos de administración); P09 (`AuditingCommandHandler`).

### HU-09: Configurar el tanque: forma y medidas

- **Actor:** `INSTITUTION_ADMIN` (`INSTITUTION_CONFIGURE`).
- **Historia:** Como administrador, quiero registrar la forma y las medidas del tanque de mi institución, para que el sistema convierta la distancia del sensor en nivel y en litros.
- **Prioridad:** must

```gherkin
Escenario: tanque cilíndrico
  Dado el tanque de la institución con shape en RECTANGULAR
  Cuando el administrador cambia shape a CYLINDER y diameter_mm a 200 con PATCH /api/v1/tanks/{id}
  Entonces la geometría del tanque pasa a CylinderGeometry
  Y los litros de /tanks/{id}/live se calculan con π · (diameter_mm / 2)²

Escenario: tanque rectangular
  Dado un tanque con shape RECTANGULAR
  Cuando el administrador guarda length_mm 400 y width_mm 300
  Entonces la geometría pasa a RectangularGeometry y el área es length_mm · width_mm

Escenario: medida que no corresponde a la forma
  Dado un tanque con shape CYLINDER
  Cuando el administrador envía length_mm
  Entonces recibe 422 con código VALIDATION_ERROR y details.field "length_mm"
  Y el tanque no cambia

Escenario: medidas fuera de límites
  Cuando el administrador envía height_mm con 0
  Entonces recibe 400 con código VALIDATION_ERROR
  Y el límite numérico de cada medida sale de FieldLimits (por definir)
```

- **Requisitos:** RF-09, RF-38; RNF-04, RNF-22.
- **Patrones:** P19 (`Strategy`, `TankGeometry` con `CylinderGeometry` y `RectangularGeometry`); P13 (`CommandBus`).

### HU-10: Calibrar el sensor con la lectura en vivo

- **Actor:** `INSTITUTION_ADMIN` (`INSTITUTION_CONFIGURE`).
- **Historia:** Como administrador, quiero calibrar el sensor con el tanque vacío y lleno usando la lectura que el sensor mide en ese momento, para que el nivel y los litros sean correctos sin hacer cuentas a mano.
- **Prioridad:** must

```gherkin
Escenario: calibrar vacío con la lectura actual
  Dado el tanque vacío y una lectura en vivo con sensor_status OK y distance_mm 350
  Cuando el administrador pulsa "usar lectura actual como vacío"
  Entonces la app envía POST /api/v1/tanks/{id}/calibration con point EMPTY
  Y empty_distance_mm queda en 350
  Y el puente recibe la nueva calibración en GET /api/v1/devices/config sin reiniciarse

Escenario: calibrar lleno con la lectura actual
  Dado el tanque lleno y una lectura en vivo con sensor_status OK y distance_mm 80
  Cuando el administrador pulsa "usar lectura actual como lleno"
  Entonces full_distance_mm queda en 80

Escenario: lectura sin eco no sirve para calibrar
  Dado una lectura en vivo con sensor_status NO_ECHO
  Entonces los botones de calibración están desactivados
  Y si se envía la petición igual, recibe 422 con código VALIDATION_ERROR

Escenario: calibración inconsistente
  Dado empty_distance_mm 350
  Cuando el administrador calibra full_distance_mm con 400
  Entonces recibe 422 con código VALIDATION_ERROR porque full_distance_mm debe ser menor que empty_distance_mm
  Y no cambia nada

Escenario: altura que no cabe (margen por definir)
  Dado height_mm 300 y un margen de montaje por definir
  Cuando empty_distance_mm supera height_mm más el margen
  Entonces recibe 422 con código VALIDATION_ERROR
```

- **Requisitos:** RF-10, RF-37; RNF-03, RNF-08, RNF-22.
- **Patrones:** P19 (`Strategy`, `TankGeometry` valida la calibración); P13 (`CommandBus`).

### HU-11: Configurar líneas de servicio y válvulas

- **Actor:** `INSTITUTION_ADMIN` (`INSTITUTION_CONFIGURE`).
- **Historia:** Como administrador, quiero decidir cuántas líneas de servicio tiene mi institución, qué válvula controla cada una y en qué ángulos abre y cierra, para que el sistema mueva los servos de la maqueta o de la instalación real.
- **Prioridad:** must

```gherkin
Escenario: crear dos líneas con su válvula
  Dado la institución "maqueta-caudal" sin líneas configuradas
  Cuando el administrador crea "Acueducto 1" con POST /api/v1/service-lines
  Y agrega una válvula motorizada con servo_channel 1, open_angle_deg 90 y closed_angle_deg 0 con POST /api/v1/service-lines/{id}/valves
  Entonces la línea queda con una válvula en el canal 1
  Y el puente recibe el canal y los ángulos en GET /api/v1/devices/config sin reiniciarse

Escenario: cambiar el ángulo de apertura en caliente
  Dado la válvula de "Acueducto 1" con open_angle_deg 90
  Cuando el administrador cambia open_angle_deg a 75 con PATCH /api/v1/valves/{id}
  Entonces la siguiente orden de apertura se envía con el ángulo 75
  Y el firmware no cambia (recibe ángulos crudos)

Escenario: canal repetido (propuesto)
  Dado que el canal 1 ya está asignado a una válvula de la institución
  Cuando se intenta asignar el canal 1 a otra válvula
  Entonces recibe 409 con código CONFLICT (regla propuesta, por confirmar)

Escenario: ángulo fuera del rango del servo
  Cuando el administrador envía open_angle_deg 200
  Entonces recibe 422 con código VALIDATION_ERROR (el rango físico del servo está en RNF-04)

Escenario: línea que sale de servicio
  Dado la línea "Acueducto 2"
  Cuando el administrador la desactiva con is_active en falso
  Entonces la línea deja de aparecer en el inicio, y el historial se conserva (no hay borrado)
```

- **Requisitos:** RF-11, RF-37, RF-39; RNF-04, RNF-08, RNF-22.
- **Patrones:** P13 (`CommandBus`); P08 (`Composite`, `ServiceLineNode` y `ValveNode` para mostrar la red, clase propuesta, por definir).

### HU-12: Ajustar los parámetros de la institución

- **Actor:** `INSTITUTION_ADMIN` (`INSTITUTION_CONFIGURE`). Lo ven `OPERATOR` y `VIEWER` (`STATUS_VIEW`).
- **Historia:** Como administrador, quiero ajustar los parámetros de mi institución (reserva, alertas, pronóstico, piloto), para que cada tanque se opere con sus propios valores.
- **Prioridad:** should

```gherkin
Escenario: cambiar la reserva
  Dado org.institution_settings con reserve_level_pct en 20
  Cuando el administrador envía PATCH /api/v1/institution/settings con reserve_level_pct 25
  Entonces el nuevo valor rige desde la siguiente lectura
  Y el cambio queda auditado con el usuario y el valor anterior
  Y no se crea una versión nueva de reglas (no hay reglas versionadas)

Escenario: valor fuera de límites
  Cuando el administrador envía reserve_level_pct 140
  Entonces recibe 400 con código VALIDATION_ERROR
  Y no cambia nada

Escenario: operador no puede cambiar parámetros
  Dado un usuario OPERATOR
  Cuando envía PATCH /api/v1/institution/settings
  Entonces recibe 403 con código FORBIDDEN
  Pero puede leer GET /api/v1/institution/settings (STATUS_VIEW)
```

- **Requisitos:** RF-12, RF-37; RNF-03, RNF-19, RNF-22.
- **Patrones:** P13 (`CommandBus`); P09 (`AuditingCommandHandler`).

---

## M3: Sensor y tanque en vivo

### HU-13: Ver el sensor y el tanque en vivo

- **Actor:** `OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER`.
- **Historia:** Como operador, quiero ver en la pantalla lo que mide el sensor ahora, en mm, en nivel, en porcentaje y en litros, para saber cuánta agua hay sin subirme al tanque.
- **Prioridad:** must

```gherkin
Escenario: lectura en vivo
  Dado el sensor enviando DISTANCE cada ~2 segundos
  Cuando el operador abre /inicio
  Entonces la app consulta GET /api/v1/tanks/{id}/live cada 2 segundos
  Y muestra distance_mm, level_mm, level_pct, volume_l y la antigüedad (age_seconds)
  Y los números usan coma decimal (por ejemplo, "50,0 %")

Escenario: dato viejo
  Dado que age_seconds supera stale_after_seconds (30 en el ejemplo)
  Cuando se consulta el estado
  Entonces sensor_status es STALE
  Y la pantalla muestra "El dato tiene más de 30 segundos. Revise el sensor"
  Y no se muestra un nivel nuevo como si fuera actual

Escenario: sin eco
  Dado que el último punto fue -1
  Entonces sensor_status es NO_ECHO y la pantalla lo muestra sin inventar un nivel

Escenario: sin lecturas
  Dado un tanque sin lecturas válidas
  Cuando se consulta el estado
  Entonces la pantalla muestra "Todavía no hay lecturas" y no muestra un nivel inventado
```

- **Requisitos:** RF-13; RNF-12, RNF-13, RNF-15, RNF-34, RNF-25.
- **Patrones:** P10 (`Facade`, `TankLiveFacade` en la API y `CaudalApi` en el frontend); P18 (`State`, `SensorStatus`: OK, NO_ECHO, STALE); P19 (`Strategy`, `TankGeometry`).

### HU-14: Ver la escena 3D del tanque y las válvulas

- **Actor:** `OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER` (`NETWORK_VIEW`).
- **Historia:** Como operador, quiero ver el tanque, las líneas y las válvulas en 3D con el nivel del agua en vivo, para entender el sistema de un vistazo.
- **Prioridad:** should

```gherkin
Escenario: escena a todo el ancho
  Dado un usuario autenticado con NETWORK_VIEW
  Cuando abre la escena
  Entonces ve el tanque, las líneas y las válvulas a todo el ancho de la pantalla
  Y la topología sale de GET /api/v1/network

Escenario: nivel a escala
  Dado un tanque de 300 mm de altura y un nivel de 135 mm
  Cuando la escena se dibuja
  Entonces el agua llega a la mitad del tanque en 3D
  Y la escena se actualiza cada 2 segundos por polling

Escenario: válvula cerrada y abierta
  Dado que la válvula de "Acueducto 1" está en OPEN según GET /api/v1/valves/live
  Entonces la línea muestra flujo animado
  Y si está en CLOSED o UNKNOWN, no muestra flujo

Escenario: teclado y lector de pantalla
  Dado un usuario que no usa el puntero
  Cuando navega con teclado
  Entonces cada válvula tiene un botón equivalente al de la escena
```

- **Requisitos:** RF-14; RNF-13, RNF-31.
- **Patrones:** P10 (`Facade`, `CaudalApi`); P17 (`Observer`, estado en vivo por polling).

---

## M4: Válvulas y comandos

### HU-15: Abrir o cerrar una válvula con confirmación del actuador

- **Actor:** `OPERATOR` e `INSTITUTION_ADMIN` (`VALVE_COMMAND_MANUAL`).
- **Historia:** Como operador, quiero abrir o cerrar la válvula de una línea desde la pantalla y ver si el actuador respondió, para regar o cortar sin salir al tanque y con la certeza de que la orden llegó.
- **Prioridad:** must

```gherkin
Escenario: cerrar una válvula con ack visible
  Dado un operador con VALVE_COMMAND_MANUAL y la válvula de "Acueducto 1" en OPEN
  Cuando pulsa Cerrar y confirma
  Entonces la app envía POST /api/v1/valve-commands/manual con valve_id, action CLOSE y un motivo opcional
  Y el estado del comando se muestra PENDING, luego DELIVERED y luego ACKED
  Y el paso a ACKED ocurre en menos de 5 segundos con el puente conectado (RNF-33)

Escenario: motivo opcional con texto por defecto
  Dado un operador que no escribe motivo
  Cuando envía el comando
  Entonces el comando se guarda con el texto por defecto configurado en manual_reason
  Y todo comando manual tiene motivo

Escenario: sin permiso
  Dado un VIEWER
  Entonces ve el estado de las válvulas pero no ve los botones Abrir ni Cerrar
  Y si envía la petición igual, recibe 403 con código FORBIDDEN

Escenario: válvula de otra institución
  Dado una válvula de "otra-institucion"
  Cuando el operador de "maqueta-caudal" envía POST /api/v1/valve-commands/manual con esa válvula
  Entonces recibe 404 con código NOT_FOUND

Escenario: prioridad humana sobre el piloto
  Dado el piloto en AUTOMATIC
  Cuando el operador envía un comando manual
  Entonces el piloto queda en pausa para esa válvula y la pantalla lo indica
  Y la reanudación del piloto está por definir (HU-24)

Escenario: dos toques
  Dado un operador en /inicio
  Cuando quiere cerrar una válvula
  Entonces el cierre se hace con dos toques: elegir la válvula y confirmar (RNF-12)
```

- **Requisitos:** RF-15, RF-37, RF-38; RNF-08, RNF-12, RNF-19, RNF-32, RNF-33.
- **Patrones:** P13 (`OpenValveCommand` y `CloseValveCommand`, `CommandBus`); P18 (`State`, `ValveCommandState`); P15 (`Mediator`, coordina la selección de la válvula y el diálogo, clase propuesta, por definir).

### HU-16: Ver el estado y la vigencia de cada comando

- **Actor:** `OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER`. El puente es el actor que cambia el estado.
- **Historia:** Como operador, quiero saber en qué estado está cada orden (pendiente, entregada, confirmada, vencida o fallida), para no pensar que una válvula se movió si no se movió.
- **Prioridad:** should

```gherkin
Escenario: flujo normal
  Dado un comando CLOSE creado a las 10:00:00 con expires_at a las 10:01:00 (valor por definir)
  Cuando el puente lo recoge con GET /api/v1/devices/commands a las 10:00:02
  Entonces el comando pasa a DELIVERED
  Y el puente envía "S1 0" por serie
  Y cuando el Arduino responde DONE, el puente hace POST /api/v1/devices/commands/{id}/ack
  Y el comando pasa a ACKED y GET /api/v1/valves/live muestra CLOSED

Escenario: comando que vence sin entregarse
  Dado un comando PENDING con expires_at ya pasado
  Entonces pasa a EXPIRED y no se ejecuta
  Y los comandos vencidos no se reenvían solos

Escenario: comando fallido no cambia el estado de la válvula
  Dado una válvula en OPEN y un comando CLOSE que termina en FAILED
  Entonces GET /api/v1/valves/live sigue mostrando OPEN
  Y el evento de comando fallido aparece en /eventos (HU-20)

Escenario: ACKED no es agua que fluye
  Dado un comando ACKED
  Entonces la pantalla dice "El actuador respondió", sin afirmar que el agua fluyó

Escenario: dos comandos activos para la misma válvula (por definir)
  Dado un comando PENDING para una válvula
  Cuando llega otro comando para la misma válvula
  Entonces la API rechaza el segundo o reemplaza el primero (decisión por definir)
```

- **Requisitos:** RF-15, RF-16; RNF-08, RNF-19, RNF-33.
- **Patrones:** P18 (`State`, `ValveCommandState`: PENDING, DELIVERED, ACKED, FAILED, EXPIRED, CANCELLED); P17 (`Observer`, publica el cambio de estado).

---

## M5: Pronóstico y eventos

### HU-17: Ver las tres salidas del pronóstico, rotuladas

- **Actor:** `OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER`.
- **Historia:** Como operador, quiero ver por separado cuánta agua queda, cuánto falta para la reserva y lo que dice el modelo Chronos, para no confundir un cálculo con una predicción.
- **Prioridad:** must

```gherkin
Escenario: agua que queda
  Dado una última lectura válida de 215 mm en un tanque con empty_distance_mm 350 y full_distance_mm 80
  Cuando se consulta GET /api/v1/tanks/{id}/outlook
  Entonces el bloque "Agua que queda" muestra "4,24 L (50,0 %)"
  Y el origen es OBSERVED

Escenario: tiempo hasta la reserva
  Dado un nivel de 135 mm, una reserva de 20 % (54 mm), y una pendiente de -2 mm/min
  Cuando se consulta el outlook
  Entonces el bloque "Llega a la reserva en…" muestra "40,5 min"
  Y el origen es ESTIMATED (determinista, sin modelo)

Escenario: el nivel no baja
  Dado una pendiente igual o mayor que cero
  Entonces el bloque dice "No se vacía al ritmo actual"

Escenario: ya en reserva
  Dado un nivel por debajo de la reserva
  Entonces el bloque dice "Ya está en reserva"

Escenario: banda Chronos
  Dado caudal-ia disponible
  Cuando se consulta el outlook
  Entonces el bloque "Pronóstico (modelo Chronos)" muestra p10, p50 y p90 en pasos de forecast_step_seconds hasta forecast_horizon_steps
  Y el origen es ESTIMATED con is_fallback en falso
  Y el pronóstico muestra la escala de la institución (por ejemplo, "en los próximos 60 minutos")
```

- **Requisitos:** RF-17; RNF-10, RNF-25, RNF-28.
- **Patrones:** P19 (`Strategy`, `TrendStrategy` y `Forecaster`); P06 (`Adapter`, `IaForecastClientAdapter`); P11 (`Proxy`, `RemoteForecasterProxy`).

### HU-18: Respaldo si la IA no responde

- **Actor:** sistema (activo para todos los roles que ven el pronóstico).
- **Historia:** Como operador, quiero que la app siga mostrando el pronóstico aunque caudal-ia esté caído, para no quedarme sin información.
- **Prioridad:** must

```gherkin
Escenario: timeout de la IA
  Dado que caudal-ia no responde en 10 segundos
  Cuando se pide el pronóstico
  Entonces el backend guarda una estimación simple con fallback_reason IA_TIMEOUT
  Y la pantalla muestra "Estimación simple: seguirá con la pendiente actual" con la banda que da la estimación
  Y la respuesta es 200, no un error

Escenario: circuito abierto
  Dado que el circuit breaker está en estado Abierto
  Cuando se pide un pronóstico
  Entonces no se llama a la IA y el motivo es CIRCUIT_OPEN

Escenario: historial insuficiente
  Dado una institución con menos puntos que forecast_context_steps
  Cuando caudal-ia responde 422 INSUFFICIENT_HISTORY
  Entonces el backend guarda la estimación simple con fallback_reason INSUFFICIENT_HISTORY
  Y guarda ambos pronósticos para la evaluación (HU-21)
```

- **Requisitos:** RF-18; RNF-10, RNF-16.
- **Patrones:** P11 (`RemoteForecasterProxy`, remoto con respaldo); P18 (`State`, `CircuitBreakerState`: Cerrado, Abierto, Semiabierto); P19 (`Strategy`, `Forecaster`, con la estimación simple como estrategia alterna).

### HU-19: Detectar anomalías y revisarlas

- **Actor:** sistema (detección). `INSTITUTION_ADMIN` confirma o descarta (`ANOMALY_REVIEW`).
- **Historia:** Como administrador, quiero que el sistema avise de un sensor pegado, de una caída anormal o de una posible fuga, y poder confirmar o descartar cada aviso, para que las decisiones no se basen en sospechas no revisadas.
- **Prioridad:** should

```gherkin
Escenario: sensor pegado con válvula abierta
  Dado que la válvula de "Acueducto 1" está abierta
  Y el valor no cambia durante sensor_stuck_window_seconds (120 en el ejemplo)
  Cuando el sistema revisa la ventana
  Entonces crea una anomalía SENSOR_FAULT en estado OPEN

Escenario: valor estable con todo cerrado
  Dado que todas las válvulas están cerradas
  Y el valor no cambia durante la ventana
  Entonces no se crea SENSOR_FAULT, porque un valor estable es normal con todo cerrado

Escenario: caída anormal
  Dado un límite max_level_change_per_minute de 2 mm/min
  Y una tendencia de -3 mm/min
  Entonces se crea ABNORMAL_DROP
  Y con -2 mm/min no se crea, porque no supera el límite

Escenario: posible fuga
  Dado que todas las válvulas están cerradas
  Y el nivel baja más que la tolerancia de fuga (por definir; 3 mm en el ejemplo)
  Entonces se crea POSSIBLE_LEAK en estado OPEN

Escenario: confirmar una sospecha
  Dado una anomalía POSSIBLE_LEAK en OPEN
  Cuando el administrador envía PATCH /api/v1/anomalies/{id} con status CONFIRMED y una nota
  Entonces la anomalía queda CONFIRMED
  Y el estatus epistémico sigue siendo INFERRED hasta que se verifique en campo

Escenario: descartar exige motivo
  Cuando el administrador envía status DISMISSED sin motivo
  Entonces recibe 422 con código REASON_REQUIRED

Escenario: el operador no revisa anomalías
  Dado un usuario OPERATOR
  Cuando envía PATCH /api/v1/anomalies/{id}
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-19, RF-37; RNF-19, RNF-28.
- **Patrones:** P17 (`Observer`, `DomainEventPublisher`); P13 (`CommandBus`); P18 (`State`, `AnomalyState`: OPEN, CONFIRMED, DISMISSED).

### HU-20: Ver los eventos de la institución

- **Actor:** `OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER` (permiso de lectura por definir, ver Actores-y-permisos.md, sección 3.2).
- **Historia:** Como operador, quiero ver en una sola lista las alertas de nivel, los comandos fallidos, las anomalías y las observaciones, para saber qué pasó y qué requiere atención.
- **Prioridad:** must

```gherkin
Escenario: alerta de nivel bajo
  Dado low_level_pct en 30 y un nivel de 25 %
  Cuando el operador abre /eventos
  Entonces ve la alerta de nivel bajo con la hora y el nivel

Escenario: alerta de nivel crítico
  Dado critical_level_pct en 10 y un nivel de 8 %
  Entonces ve una alerta de nivel crítico, además de la de nivel bajo

Escenario: eventos mezclados con su origen
  Dado una anomalía SENSOR_FAULT, un comando FAILED y una observación
  Cuando consulta GET /api/v1/events
  Entonces la lista muestra los tres, con su tipo, su hora y su origen
  Y se pagina con cursor opaco (limit de 1 a 100)

Escenario: sin eventos
  Dado una institución sin eventos en el periodo
  Entonces la pantalla muestra "No hay eventos en este periodo"
```

- **Requisitos:** RF-20; RNF-04, RNF-28.
- **Patrones:** P17 (`Observer`, fuentes de eventos); P18 (`State`, `TankLevelState`: Alto, Bajo, Crítico, para las alertas).

### HU-21: Evaluar la IA frente a la estimación simple

- **Actor:** `INSTITUTION_ADMIN` (`EVALUATION_VIEW`) y el superadmin con act_as.
- **Historia:** Como administrador, quiero comparar el pronóstico de Chronos con la estimación simple, para saber si la IA mejora algo antes de confiar en ella.
- **Prioridad:** should

```gherkin
Escenario: evaluación con muestra suficiente
  Dado 45 pronósticos evaluados con IA y con estimación simple
  Cuando se consulta GET /api/v1/forecast-evaluation
  Entonces la respuesta muestra MAE del p50, WQL, cobertura de [p10, p90] y skill para cada horizonte
  Y skill = 1 − MAE_IA / MAE_simple por horizonte

Escenario: muestra insuficiente
  Dado 12 pronósticos evaluados
  Entonces se muestra "Muestra insuficiente: 12 de 30 pronósticos" y no hay veredicto sobre la IA

Escenario: cobertura fuera del rango
  Dado una cobertura de 65 % con 40 pronósticos
  Entonces el veredicto es "La IA no cumple el criterio de cobertura (70 % a 90 %)"

Escenario: skill no calculable
  Dado un MAE de la estimación simple igual a 0
  Entonces el valor se muestra como "No calculable" y no como un número
```

- **Requisitos:** RF-21; RNF-18, RNF-25.
- **Patrones:** P20 (`Template Method`, `Backtest`); P19 (`Strategy`, `Forecaster`).

---

## M6: Piloto automático y asistente

El piloto automático es el actor de sistema `AUTOPILOT` (ver [Actores-y-permisos.md](Actores-y-permisos.md)). Tiene tres modos por institución: `MANUAL`, `ASSISTED` y `AUTOMATIC`, guardados en `org.institution_settings.autopilot_mode`. Solo tiene dos guardas: el piso de reserva (`reserve_floor_pct`) y la prioridad humana (cualquier acción manual pausa el piloto). Las recomendaciones (`OPEN`, `CLOSE`, `WAIT`, `INSPECT`) las decide el piloto con sus reglas. El asistente de IA (LLM) solo responde preguntas y redacta reportes: no decide ni envía comandos. Si el LLM falla, responde un respaldo por reglas y plantillas.

### HU-22: Configurar el modo del piloto automático

- **Actor:** `INSTITUTION_ADMIN` (`AUTOPILOT_CONFIGURE`).
- **Historia:** Como administrador, quiero elegir el modo del piloto (manual, asistido o automático) y su piso de reserva, para decidir cuánto puede hacer el sistema sin que yo intervenga.
- **Prioridad:** must

```gherkin
Escenario: cambiar el modo
  Dado una institución con el piloto en MANUAL
  Cuando el administrador cambia el modo a ASSISTED con AUTOPILOT_CONFIGURE
  Entonces institution_settings.autopilot_mode guarda ASSISTED
  Y se registra el cambio en audit.audit_log con el usuario

Escenario: piso de reserva y prioridad humana
  Dado que el administrador edita reserve_floor_pct y operator_absence_minutes
  Cuando guarda valores dentro de los límites de FieldLimits
  Entonces los nuevos valores rigen desde la siguiente ejecución del piloto
  Y un valor fuera de límites recibe 400 con código VALIDATION_ERROR

Escenario: la ruta de configuración está por definir
  Dado que la ruta exacta del cambio de modo (PUT /autopilot/mode o PATCH de settings) está por definir
  Entonces el contrato se fija en API.md antes de implementarlo

Escenario: un rol sin permiso
  Dado un usuario OPERATOR o VIEWER
  Cuando intenta cambiar el modo
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-22, RF-37; RNF-07, RNF-19, RNF-22.
- **Patrones:** P18 (`State`, `AutopilotMode`: MANUAL, ASSISTED y AUTOMATIC); P13 (`CommandBus`); P09 (`AuditingCommandHandler`).

### HU-23: Aceptar o rechazar una recomendación del piloto

- **Actor:** `OPERATOR` e `INSTITUTION_ADMIN` (aceptar exige `VALVE_COMMAND_MANUAL`; rechazar exige `ASSISTANT_USE`). Con el piloto en modo `ASSISTED`.
- **Historia:** Como operador, quiero ver qué recomienda el piloto y su motivo, y aceptarla o rechazarla con un toque, para decidir con ayuda sin perder el control.
- **Prioridad:** must

```gherkin
Escenario: ver la recomendación
  Dado una recomendación {action: CLOSE, valve_id, reason} generada por el piloto con sus reglas
  Cuando el operador abre /inicio con ASSISTANT_USE
  Entonces ve la acción, la válvula, el motivo y la hora de la recomendación

Escenario: aceptar en modo ASSISTED
  Dado una recomendación pendiente y el piloto en ASSISTED
  Cuando un usuario con VALVE_COMMAND_MANUAL pulsa Aceptar
  Entonces la decisión queda ACCEPTED en ops.autopilot_decisions con decided_by
  Y se genera un comando con autopilot_decision_id que pasa a ACKED cuando el actuador responde

Escenario: rechazar
  Dado una recomendación pendiente
  Cuando el usuario pulsa Rechazar
  Entonces la decisión queda REJECTED y no se genera comando

Escenario: modo MANUAL
  Dado el piloto en MANUAL
  Entonces la recomendación solo se muestra, sin botones de aceptar ni de rechazar
```

- **Requisitos:** RF-23; RNF-08, RNF-19, RNF-29.
- **Patrones:** P13 (`CommandBus`); P18 (`State`, resultado de la decisión: aceptada, rechazada o ejecutada); P17 (`Observer`, evento de decisión).

### HU-24: El piloto actúa solo cuando el operador no está, sin bajar de la reserva

- **Actor:** sistema (`AUTOPILOT`); efecto visible para `OPERATOR` e `INSTITUTION_ADMIN`.
- **Historia:** Como administrador, quiero que el sistema abra o cierre válvulas cuando nadie está operando, sin dejar el tanque bajo el piso de reserva, para que el servicio no dependa de que alguien esté mirando.
- **Prioridad:** must

```gherkin
Escenario: ausencia del operador en modo AUTOMATIC
  Dado el piloto en AUTOMATIC y una recomendación CLOSE de la válvula de "Acueducto 2"
  Y ningún usuario ha enviado un comando a esa válvula en operator_absence_minutes (30 en el ejemplo)
  Cuando corre el ciclo del piloto
  Entonces se crea una decisión en ops.autopilot_decisions con status EXECUTED
  Y se crea un comando CLOSE con autopilot_decision_id, firmado y entregado al puente

Escenario: prioridad humana
  Dado el operador abre "Acueducto 1" a las 10:00
  Cuando el piloto corre a las 10:20
  Entonces recomienda WAIT, porque 20 min es menos que 30 min
  Y a partir de las 10:30 puede actuar sobre esa válvula

Escenario: piso de reserva
  Dado un nivel de 12 %, por debajo de reserve_floor_pct (15 %)
  Cuando la recomendación sería abrir una válvula
  Entonces el piloto no abre en ninguna línea y registra la decisión como SKIPPED con el motivo RESERVE_FLOOR
  Y si una válvula ya está abierta, la recomendación es CLOSE

Escenario: pausa al actuar a mano
  Dado el piloto en AUTOMATIC
  Cuando un usuario envía un comando manual
  Entonces el piloto queda en pausa y no ejecuta nada hasta que se reanude
  Y el criterio de reanudación está por definir

Escenario: el asistente caído no detiene el piloto
  Dado que el LLM no responde
  Cuando corre el ciclo del piloto
  Entonces el piloto decide con sus reglas, porque el LLM no participa en las decisiones

Escenario: reglas que eligen entre OPEN, WAIT e INSPECT (por definir)
  Dado que la regla que elige entre OPEN, WAIT e INSPECT está por definir en el backend
  Entonces esta historia no se implementa en esa parte hasta confirmarla
```

- **Requisitos:** RF-24, RF-23; RNF-08, RNF-19, RNF-29, RNF-32.
- **Patrones:** P18 (`State`, `AutopilotMode`); P19 (`Strategy`, recomendador por reglas); P17 (`Observer`, evento de decisión).

### HU-25: Preguntar al asistente

- **Actor:** `INSTITUTION_ADMIN`, `OPERATOR`, `VIEWER` con `ASSISTANT_USE` (el lector no lo tiene por defecto; ver la matriz de permisos).
- **Historia:** Como operador, quiero preguntarle al asistente en lenguaje natural, por ejemplo "¿cuánta agua queda?", para entender la situación sin leer gráficas.
- **Prioridad:** should

```gherkin
Escenario: pregunta respondida con los datos del outlook
  Dado un usuario con ASSISTANT_USE
  Cuando envía POST /api/v1/assistant/ask con "¿cuánta agua queda?"
  Entonces recibe "Quedan 4,24 L (50,0 %)", con los datos del outlook
  Y la respuesta indica que su origen es el modelo de lenguaje (source LLM)

Escenario: preguntas sugeridas
  Dado que el asistente está abierto en /inicio
  Entonces ofrece "¿cuánta agua queda?", "¿cuándo llega a la reserva?" y "¿qué pasó hoy?"

Escenario: el asistente no actúa
  Dado una pregunta que pide abrir una válvula
  Cuando el asistente responde
  Entonces no se crea ningún comando de válvula
  Y la respuesta indica que el asistente solo responde preguntas

Escenario: respaldo por reglas
  Dado que el LLM no responde en 20 segundos (INSIGHTS_READ_TIMEOUT_MS)
  Cuando se hace la pregunta
  Entonces responde 200 con un texto de plantilla con los mismos datos y source RULES

Escenario: pregunta inválida
  Cuando la pregunta está vacía o supera el límite de FieldLimits
  Entonces recibe 400 con código VALIDATION_ERROR
```

- **Requisitos:** RF-25; RNF-04, RNF-29, RNF-30.
- **Patrones:** P06 (`Adapter`, cliente de `/v1/insights/ask`); P11 (`Proxy`, con respaldo).

### HU-26: Reporte narrativo del periodo

- **Actor:** `INSTITUTION_ADMIN` (`REPORT_VIEW`).
- **Historia:** Como administrador, quiero un reporte del periodo con resumen, hallazgos y recomendaciones en español, para presentarlo a quien lo necesite.
- **Prioridad:** must

```gherkin
Escenario: reporte del periodo
  Dado un periodo con lecturas, comandos y decisiones del piloto
  Cuando el administrador abre GET /api/v1/reports con from y to
  Entonces ve los agregados (nivel mínimo y máximo, horas abierta por válvula, decisiones del piloto)
  Y al pedir POST /api/v1/reports/narrative recibe el texto con resumen, hallazgos y recomendaciones

Escenario: imprimir
  Dado un reporte en pantalla
  Cuando el usuario pulsa Imprimir
  Entonces se usa la impresión del navegador (no se genera PDF en el servidor)

Escenario: LLM caído
  Dado que el LLM no responde
  Cuando se pide la narrativa
  Entonces el texto sale de una plantilla con los mismos agregados y source RULES

Escenario: periodo inválido
  Cuando from es posterior a to
  Entonces recibe 400 con código VALIDATION_ERROR
```

- **Requisitos:** RF-26; RNF-20, RNF-28, RNF-29.
- **Patrones:** P22 (`Interpreter`, plantillas del respaldo); P06 (`Adapter`, cliente de `/v1/insights/report`).

---

## M7: Observaciones, cierre del día y actas

### HU-27: Registrar una observación manual

- **Actor:** `OPERATOR` e `INSTITUTION_ADMIN` (`OBSERVATION_CREATE`).
- **Historia:** Como operador, quiero anotar lo que veo en campo, como agua sucia o daño visible, para que quede en el historial y en el acta como dato observado.
- **Prioridad:** must

```gherkin
Escenario: registrar agua sucia
  Dado un operador con OBSERVATION_CREATE
  Cuando envía POST /api/v1/observations con tipo WATER_DIRTY y el texto "Agua café en la manguera 2"
  Entonces la observación se guarda con la hora que asigna el servidor
  Y aparece en /eventos como evento OBSERVED

Escenario: tipos válidos
  Dado los tipos WATER_DIRTY, WATER_MUDDY, VISIBLE_DAMAGE y OTHER
  Cuando se envía un tipo fuera de esa lista
  Entonces recibe 400 con código VALIDATION_ERROR

Escenario: texto demasiado corto
  Cuando el texto tiene 9 caracteres
  Entonces recibe 422 con código REASON_REQUIRED y mensaje "Escriba al menos 10 caracteres"

Escenario: el lector no registra observaciones
  Dado un usuario VIEWER
  Cuando envía POST /api/v1/observations
  Entonces recibe 403 con código FORBIDDEN

Escenario: hora del cliente (por definir)
  Dado que aceptar la hora del cliente está por definir
  Entonces la hora la asigna siempre el servidor hasta decidirlo
```

- **Requisitos:** RF-27, RF-38; RNF-04, RNF-19.
- **Patrones:** P13 (`CommandBus`); P17 (`Observer`, publica la observación como evento).

### HU-28: Cerrar el día con las novedades

- **Actor:** `OPERATOR` e `INSTITUTION_ADMIN` (`DAY_CLOSE`).
- **Historia:** Como operador, quiero que el sistema resuma el día y poder agregar mis novedades antes de cerrarlo, para que el registro del día sea completo sin llenar formularios largos.
- **Prioridad:** must

```gherkin
Escenario: resumen automático del día
  Dado un día con tres aperturas y cierres en "Acueducto 1" y una observación
  Cuando el operador abre GET /api/v1/days/{date}/closure
  Entonces ve cada apertura y cierre con su origen (manual o piloto)
  Y el nivel mínimo y máximo, en mm y en %
  Y los eventos y las observaciones del día
  Y los litros entregados estimados, con el método por definir

Escenario: cierre con novedades
  Dado el resumen del día
  Cuando el operador escribe "Se cortó el agua a las 9" y envía POST /api/v1/days/{date}/closure
  Entonces el día queda cerrado con la nota
  Y el cierre alimenta el acta de ese periodo

Escenario: segundo cierre del mismo día
  Dado un día ya cerrado
  Cuando se envía otro cierre
  Entonces recibe 409 con código CONFLICT

Escenario: nota demasiado larga
  Cuando la nota tiene más de 10 líneas o más de 500 caracteres
  Entonces recibe 400 con código VALIDATION_ERROR

Escenario: lector ve el cierre
  Dado un usuario VIEWER
  Cuando consulta GET /api/v1/days/{date}/closure
  Entonces ve el cierre, pero no puede escribir en él
```

- **Requisitos:** RF-28, RF-37; RNF-19, RNF-22.
- **Patrones:** P13 (`CloseDayCommand`); P18 (`State`, estado del día: ABIERTO, CERRADO); P17 (`Observer`).

### HU-29: Generar un acta con cuatro estatus

- **Actor:** `INSTITUTION_ADMIN` (`MINUTES_GENERATE`). Lo ven todos los roles con `MINUTES_VIEW`.
- **Historia:** Como administrador, quiero generar un acta del periodo que separe lo observado, lo estimado, lo inferido y lo confirmado, para que nadie confunda una sospecha con un hecho.
- **Prioridad:** should

```gherkin
Escenario: acta con cuatro secciones
  Dado un periodo con lecturas, observaciones, pronósticos, anomalías, comandos y cierres
  Cuando el administrador envía POST /api/v1/minutes con period_from y period_to
  Entonces el acta queda en DRAFT con las secciones observado, estimado, inferido y confirmado
  Y cada registro aparece solo en la sección de su estatus epistémico

Escenario: comando ACKED en el acta
  Dado un comando ACKED
  Entonces el acta dice que el actuador respondió, sin afirmar que el agua fluyó

Escenario: acta final inmutable
  Dado un acta en estado FINAL con pdf_sha256 guardado
  Cuando se intenta regenerar o modificar
  Entonces recibe 409 y se conserva el PDF original

Escenario: acta de institución simulada
  Dado una institución con is_simulated en verdadero
  Cuando se genera el acta
  Entonces el PDF incluye la marca "Datos simulados"

Escenario: el operador no genera actas
  Dado un usuario OPERATOR
  Cuando envía POST /api/v1/minutes
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-29, RF-37; RNF-19, RNF-22, RNF-28.
- **Patrones:** P20 (`Template Method`, `DocumentGenerator`: encabezado, secciones y firmas); P21 (`Visitor`, `MinutesSectionVisitor`, clasifica registros por estatus); P04 (`Builder`, `MinutesBuilder`).

---

## M8: Dispositivos y maqueta

### HU-30: Registrar un dispositivo y su clave pública

- **Actor:** `INSTITUTION_ADMIN` (`DEVICE_MANAGE`). Quién puede dar de alta claves (solo plataforma o también el administrador) está por definir.
- **Historia:** Como administrador, quiero registrar un sensor o una válvula con su clave pública, para que sus datos se acepten solo si vienen firmados.
- **Prioridad:** should

```gherkin
Escenario: registro con clave pública
  Dado un dispositivo tipo LEVEL_SENSOR
  Cuando se envía POST /api/v1/devices y luego POST /api/v1/devices/{id}/keys con la clave pública Ed25519
  Entonces la base de datos guarda solo la clave pública
  Y la respuesta nunca incluye una clave privada

Escenario: rotación de clave
  Dado un dispositivo con una clave activa
  Cuando se registra una clave nueva
  Entonces la anterior sigue aceptada 7 días (configurable) y después queda revocada

Escenario: identificador repetido
  Cuando se registra un dispositivo con un identificador ya existente en la institución
  Entonces recibe 409 con código CONFLICT
```

- **Requisitos:** RF-30; RNF-08, RNF-19.
- **Patrones:** P06 (`Adapter`, `DeviceTelemetryAdapter`).

### HU-31: Recibir telemetría firmada de distancia

- **Actor:** dispositivo (puente USB `maqueta-usb` o sensor).
- **Historia:** Como sistema, quiero aceptar la distancia del sensor solo si viene firmada y es nueva, para que nadie pueda inyectar niveles falsos ni reenviar mensajes viejos.
- **Prioridad:** should

```gherkin
Escenario: telemetría válida en mm
  Dado un dispositivo activo con firma Ed25519 correcta, timestamp dentro de ±300 segundos y nonce nuevo
  Cuando envía POST /api/v1/devices/telemetry con la métrica DISTANCE en mm
  Entonces recibe 202 y el punto crudo se guarda en devices.telemetry_points
  Y el backend lo convierte a nivel con la geometría del tanque (HU-09)
  Y el nonce se guarda durante 600 segundos

Escenario: sin eco
  Dado que el HC-SR04 no tiene eco
  Cuando el puente envía -1 como valor de DISTANCE
  Entonces el punto cuenta para SENSOR_FAULT y no produce nivel

Escenario: ritmo de envío
  Dado el puente enviando cada 2 segundos
  Entonces son 30 peticiones firmadas por minuto, por debajo del límite de 60 por dispositivo

Escenario: nonce repetido
  Dado un nonce usado hace 2 minutos
  Cuando el mismo nonce llega de nuevo
  Entonces recibe 401 con código NONCE_REPLAY y se registra en iam.security_events

Escenario: firma inválida o timestamp fuera de ventana
  Cuando la firma no valida con la clave del dispositivo
  Entonces recibe 401 con código INVALID_SIGNATURE
  Cuando el timestamp está a 301 segundos del servidor
  Entonces recibe 401 con código TIMESTAMP_OUT_OF_WINDOW

Escenario: lote demasiado grande
  Cuando envía más de 100 puntos en una petición
  Entonces recibe 400 con código VALIDATION_ERROR
```

- **Requisitos:** RF-31; RNF-04, RNF-08, RNF-15.
- **Patrones:** P06 (`Adapter`, `DeviceTelemetryAdapter`); P12 (`Chain of Responsibility`, validación de telemetría: sin eco, zona ciega, rango, futuro y nonce; la cadena de firma es clase propuesta, por definir).

### HU-32: Maqueta física: telemetría y válvulas con Arduino UNO

- **Actor:** dispositivo (maqueta física `maqueta-caudal`).
- **Historia:** Como equipo de CAUDAL, quiero una maqueta con un Arduino UNO por USB, un HC-SR04 y dos servos MG90S que pisan dos mangueras, que hable con el backend, para probar el lazo completo con hardware real.
- **Prioridad:** should

```gherkin
Escenario: telemetría de la maqueta
  Dado la maqueta registrada con su clave pública
  Cuando el puente lee "D <mm>" del UNO por serie
  Entonces envía DISTANCE firmada cada 2 segundos

Escenario: ejecutar una orden
  Dado un comando CLOSE para la línea "Acueducto 1" (canal 1)
  Cuando el puente lo recibe en GET /api/v1/devices/commands
  Entonces envía "S1 0" por serie, el UNO responde DONE y el puente hace POST /api/v1/devices/commands/{id}/ack
  Y la manguera queda pisada o libre según el ángulo

Escenario: el firmware no cambia
  Dado que el firmware recibe ángulos crudos
  Entonces los ángulos abierto y cerrado viven en la base de datos y el firmware no los conoce

Escenario: pérdida de contacto (por definir)
  Dado que el puente se desconecta
  Cuando pasa el tiempo límite del protocolo
  Entonces la válvula toma su fail_safe_position, cuyo valor y disparador están por definir

Escenario: alimentación de los servos
  Dado que los dos MG90S se alimentan solo por USB
  Cuando la placa aparece como READY en medio de una maniobra
  Entonces el problema es de alimentación y no de código (ver RG-01 en Riesgos.md)
```

- **Requisitos:** RF-32, RF-31, RF-15; RNF-08, RNF-32, RNF-33.
- **Patrones:** P06 (`Adapter`, puente serie hacia el UNO y hacia el backend); P18 (`State`, `ValveCommandState`).

### HU-33: Maqueta virtual en el simulador

- **Actor:** equipo de CAUDAL (superadmin de plataforma).
- **Historia:** Como equipo de CAUDAL, quiero una maqueta virtual en `caudal-simulador` que use el mismo protocolo que la física, para construir y probar el tablero y el piloto sin el hardware.
- **Prioridad:** should

```gherkin
Escenario: lazo cerrado
  Dado un escenario YAML y la maqueta virtual registrada en una institución simulada
  Cuando el simulador corre en modo live
  Entonces envía DISTANCE firmada cada 2 segundos y consulta GET /api/v1/devices/commands cada 2 segundos
  Y ejecuta la orden en su modelo y responde con el ack

Escenario: comando vencido
  Dado un comando con expires_at pasado
  Entonces la maqueta virtual no lo ejecuta

Escenario: firma
  Dado la maqueta virtual
  Entonces usa el mismo protocolo Ed25519 que el dispositivo real
```

- **Requisitos:** RF-33, RF-31, RF-15; RNF-08, RNF-22.
- **Patrones:** P06 (`Adapter`); P13 (`OpenValveCommand` y `CloseValveCommand`); P18 (`State`, `ValveCommandState`).

---

## M9: Auditoría

### HU-34: Consultar la bitácora y los eventos de seguridad

- **Actor:** `INSTITUTION_ADMIN` (`AUDIT_VIEW`) y el superadmin con act_as.
- **Historia:** Como administrador, quiero consultar quién hizo qué y cuándo en mi institución, para investigar un problema o demostrar quién abrió o cerró una válvula.
- **Prioridad:** should

```gherkin
Escenario: filtro por fecha
  Dado eventos de auditoría de varios días
  Cuando se consulta GET /api/v1/audit-log con un rango de fechas y limit 20
  Entonces la respuesta trae hasta 20 eventos con cursor opaco firmado para la siguiente página

Escenario: comando de válvula con su autor
  Dado un comando manual de cierre enviado por un operador
  Cuando se consulta la bitácora
  Entonces el evento muestra el usuario, la válvula, la acción y el motivo

Escenario: acción del superadmin con act_as
  Dado que el superadmin entró con act_as a una institución
  Cuando se consulta la bitácora de esa institución
  Entonces aparece la emisión del token y cada acción, con el actor PLATFORM_ADMIN

Escenario: sin secretos en la respuesta
  Dado un evento de cambio de contraseña
  Entonces el evento no contiene la contraseña, ni tokens, ni la IP en claro (solo HMAC)

Escenario: el operador no ve la auditoría
  Dado un usuario OPERATOR
  Cuando consulta GET /api/v1/security-events
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-34; RNF-09, RNF-19, RNF-24.
- **Patrones:** P09 (`AuditingCommandHandler`); P17 (`Observer`, publica los eventos de seguridad).

---

## M10: Instituciones simuladas

### HU-35: Importar historial simulado solo en instituciones simuladas

- **Actor:** superadmin de CAUDAL (`IMPORT_SIMULATED`, solo con act_as en instituciones simuladas). El administrador de la institución no tiene este permiso.
- **Historia:** Como equipo de CAUDAL, quiero cargar un año de datos simulados en una institución simulada, para probar el pronóstico y la evaluación con un historial realista.
- **Prioridad:** should

```gherkin
Escenario: importación en una institución simulada
  Dado la institución "demo-b2b" con is_simulated en verdadero
  Cuando el superadmin con act_as envía POST /api/v1/imports/readings con 1.000 registros en un archivo de 200 KiB
  Entonces se crea una fila en sim.import_batches con kind READINGS y los conteos aceptados y rechazados
  Y las lecturas se marcan como origen IMPORT

Escenario: importación en una institución real
  Dado la institución "maqueta-caudal" con is_simulated en falso
  Cuando se intenta importar
  Entonces recibe 403 con código SIMULATED_ONLY
  Y se registra IMPORT_REJECTED en iam.security_events con severidad HIGH

Escenario: archivo demasiado grande
  Cuando el cuerpo supera 1 MiB o 5.000 filas
  Entonces recibe 413 o 400 con código VALIDATION_ERROR y no importa nada

Escenario: el administrador de una simulada no importa
  Dado un INSTITUTION_ADMIN de una institución simulada
  Cuando intenta importar
  Entonces recibe 403 con código FORBIDDEN
```

- **Requisitos:** RF-35; RNF-04, RNF-07.
- **Patrones:** P12 (`Chain of Responsibility`, validación de cada fila); P17 (`Observer`, evento de importación).

### HU-36: Mostrar que los datos son simulados

- **Actor:** todos los roles.
- **Historia:** Como operador o lector, quiero ver claramente cuándo los datos de mi institución son simulados, para no tomar una prueba como si fuera la realidad.
- **Prioridad:** must

```gherkin
Escenario: institución simulada
  Dado una institución con is_simulated en verdadero
  Cuando se abre /inicio, /eventos, un cierre, un acta o el asistente
  Entonces aparece la marca "Datos simulados" en la pantalla y en el PDF
  Y la API devuelve el campo simulated en verdadero

Escenario: institución real
  Dado la institución "maqueta-caudal" con is_simulated en falso
  Cuando se abre /inicio
  Entonces no aparece la marca y simulated es falso

Escenario: mensaje generado
  Dado una institución simulada
  Cuando se genera un reporte narrativo
  Entonces el texto empieza con "[Datos simulados]"
```

- **Requisitos:** RF-36; RNF-21, RNF-28.
- **Patrones:** P22 (`Interpreter`, plantillas de mensajes con la marca); P07 (`Bridge`, la marca se muestra igual en cualquier pantalla o documento).

---

## Resumen por módulo

| Módulo | Historias | Cantidad | Prioridad (must / should) |
|---|---|---|---|
| M1 Login e instituciones | HU-01 a HU-07 | 7 | 5 / 2 |
| M2 Configuración de la institución | HU-08 a HU-12 | 5 | 4 / 1 |
| M3 Sensor y tanque en vivo | HU-13 a HU-14 | 2 | 1 / 1 |
| M4 Válvulas y comandos | HU-15 a HU-16 | 2 | 1 / 1 |
| M5 Pronóstico y eventos | HU-17 a HU-21 | 5 | 3 / 2 |
| M6 Piloto y asistente | HU-22 a HU-26 | 5 | 4 / 1 |
| M7 Observaciones, cierre y actas | HU-27 a HU-29 | 3 | 2 / 1 |
| M8 Dispositivos y maqueta | HU-30 a HU-33 | 4 | 0 / 4 |
| M9 Auditoría | HU-34 | 1 | 0 / 1 |
| M10 Instituciones simuladas | HU-35 a HU-36 | 2 | 1 / 1 |

Total: 36 historias. Prioridades: 21 `must` y 15 `should`.

Alcance por fase: ver [Caudal.md](Caudal.md). Las historias de M8 (dispositivos y maqueta) y M10 (importación) son de las fases de integración con el hardware y del simulador; la prueba de banco del lazo de válvulas (HU-15 y HU-32) va antes que cualquier otra cosa.

Relacionados: [Caudal.md](Caudal.md) · [Actores-y-permisos.md](Actores-y-permisos.md) · [Requerimientos.md](Requerimientos.md) · [Reglas-de-negocio.md](Reglas-de-negocio.md) · [Riesgos.md](Riesgos.md)
