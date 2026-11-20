# Estudio: qué ve cada rol al entrar

Este estudio decide la **primera pantalla** de cada rol en CAUDAL B2B. El método parte de lo que cada persona tiene que decidir, sigue con los datos que necesita para eso y termina en los widgets. Cada widget usa un endpoint que ya existe, o uno nuevo de la versión B2B (marcado con **B2B**).

> Cambio de alcance B2B (2026-11-20): la Junta, la entidad de apoyo, la propuesta de turnos y la publicación se quitan del producto. Los roles son los de la institución (`OPERATOR`, `INSTITUTION_ADMIN`, `VIEWER`) y el de la plataforma (`PLATFORM_ADMIN`). Permisos en `Actores-y-permisos.md`.

## 1. Principios (tomados del diseño de HMI industrial, ISA-101)

Los sistemas SCADA de agua y de plantas industriales usan las pautas de HMI de alto desempeño de ANSI/ISA-101. CAUDAL toma de ahí cuatro ideas:

1. **Jerarquía de pantallas.**
   - Nivel 1: panorama de la institución.
   - Nivel 2: operación del tanque y de sus líneas de servicio.
   - Nivel 3: detalle de una válvula o un sensor.
   - Nivel 4: diagnóstico, con tendencias e historial.
   - Cada rol **entra al nivel que más usa**, y de ahí baja con uno o dos clics.
2. **El color señala lo anormal.** Lo normal se ve en tonos neutros de la marca (turquesas). El ámbar y el rojo quedan para las alertas: nivel bajo, sensor sospechoso, comando fallido. Así lo urgente se ve primero.
3. **Datos con contexto.** No basta "1,8 m": la cifra lleva la tendencia, el nivel en porcentaje, cuánto hace que se midió y, en su bloque, el origen del dato.
4. **El panorama no se llena de detalle.** El nivel 1 muestra pocos números grandes. El detalle va en las pantallas de abajo.

En CAUDAL B2B se agregan dos más:

5. **El asistente está en todas las pantallas.** Un panel lateral, rotulado "Asistente (modelo de lenguaje)", responde preguntas y dice si la respuesta vino del LLM o de las reglas de respaldo. No ejecuta nada.
6. **Pantalla completa.** El contenido usa todo el ancho, en celular y en escritorio. En escritorio, las páginas se arman en grillas de 2 o 3 columnas.

## 2. Inicio común del operador y del administrador: el tanque y las líneas

Operador (`OPERATOR`) y administrador (`INSTITUTION_ADMIN`) entran a la misma pantalla de inicio (`/inicio`). El administrador ve además los accesos de configuración. El `VIEWER` ve la misma pantalla sin los controles de válvula.

**Lo que decide el operador:**
- Qué línea abrir o cerrar ahora.
- Si le cree al sensor o si hay que revisarlo.
- Qué observación anotar (agua sucia, agua turbia, daño visible).
- Si el piloto debe seguir su recomendación.

**Por qué el tanque en vivo primero:** el operador piensa en el tanque y en sus llaves como algo físico. La vista del tanque muestra lo que mide el sensor en este momento. La escena 3D escalada a las medidas reales es la forma más directa de ver el nivel, y es el nivel 2 de ISA-101.

| Zona | Widget | Fuente de datos |
|---|---|---|
| Tarjeta "ahora" (arriba, a la izquierda) | **Tanque en vivo**: distancia cruda en mm, nivel, porcentaje, litros y edad del dato, con el estado del sensor (`OK`, `NO_ECHO`, `STALE`). Se refresca cada ~2 s. | `GET /tanks/{id}/live` (B2B) |
| Centro | **Escena 3D del tanque** con el nivel en vivo, escalada a las medidas reales de `org.tanks`. | `GET /tanks/{id}/live`, `GET /tanks/{id}/status` |
| Líneas de servicio | Una tarjeta por línea ("Acueducto 1", "Acueducto 2", con su nombre editable): estado de la válvula (`OPEN`, `CLOSED`, `UNKNOWN`) y botones **Abrir** y **Cerrar**, con el estado del último comando (`PENDING`, `DELIVERED`, `ACKED`, `FAILED`). Solo `INSTITUTION_ADMIN` y `OPERATOR` ven los botones. | `GET /valves/live` (B2B), `POST /valve-commands/manual` |
| Pronóstico | Tres bloques rotulados: **"Agua que queda"** (litros y porcentaje), **"Llega a la reserva en…"** (minutos, con la tasa actual) y **"Pronóstico (modelo Chronos)"** (banda p10, p50 y p90). | `GET /tanks/{id}/outlook` (B2B) |
| Eventos y piloto | Lista de eventos abiertos (fugas posibles, caídas anormales, fallas de sensor, alertas de nivel) y la recomendación del piloto con su motivo. Con modo `ASSISTED`, aceptar o rechazar. | `GET /events` (B2B), `GET /autopilot/recommendations` |
| Acciones rápidas | Registrar una observación manual y cerrar el día. | `POST /observations` (B2B), `POST /days/{date}/closure` |
| Panel lateral | **Asistente (modelo de lenguaje)** con las preguntas sugeridas "¿cuánta agua queda?", "¿cuándo llega a la reserva?" y "¿qué pasó hoy?". | `POST /assistant/ask` |

En el celular, la escena ocupa la pantalla, el panel lateral pasa a una hoja que se desliza desde abajo y se mantiene la barra de pestañas.

### 2.1 Lo que agrega el administrador

El administrador (`INSTITUTION_ADMIN`) entra a la misma pantalla y encuentra, en el menú de administración, las rutas de configuración:

| Ruta | Qué configura | Fuente |
|---|---|---|
| `/admin/tanque` | Forma, medidas y calibración del sensor. Botones "usar lectura actual como vacío/lleno". | `PATCH /tanks/{id}`, `POST /tanks/{id}/calibration` (B2B) |
| `/admin/lineas` | Líneas de servicio, cuántas hay, su válvula, su canal de servo y sus ángulos abierto y cerrado. | `GET/POST/PATCH /service-lines`, `PATCH /valves/{id}` (B2B) |
| `/admin/usuarios` | Usuarios de la institución y sus roles. | `GET/POST /users` |
| `/admin/piloto` | Modo del piloto (`MANUAL`, `ASSISTED`, `AUTOMATIC`), piso de reserva y tiempo de ausencia. | `PUT /autopilot/mode` (ruta por definir), `GET/PATCH /institution/settings` (B2B) |
| `/admin/dispositivos` | Sensor y actuadores registrados, su última telemetría y su estado. | `GET /devices` |
| Parámetros | Umbrales de nivel, tiempos de dato viejo, ventanas de tendencia y pronóstico. | `GET/PATCH /institution/settings` (B2B) |
| Calidad del pronóstico | Error de la banda Chronos contra la estimación simple (MAE) y cobertura del rango. | `GET /forecast-evaluation` |
| Reporte | Tarjeta con el resumen narrativo del periodo y un enlace al acta. | `POST /reports/narrative`, `GET /minutes` |

## 3. Cierre del día y actas (operador y administrador)

El cierre del día (`/cierre/:date`) muestra lo que el sistema ya resume: aperturas y cierres por línea con su origen (manual o piloto), nivel mínimo y máximo, litros entregados estimados, eventos y observaciones. El operador agrega las novedades y cierra el día una sola vez.

Las actas (`/actas`) muestran las actas generadas en PDF, con los cuatro estatus epistémicos: observado, estimado, inferido y confirmado.

## 4. Vista del visor (`VIEWER`): solo lectura

El `VIEWER` entra a `/inicio` con la misma información del operador, sin botones de válvula, sin observaciones y sin cierre. Puede ver el cierre del día y las actas (`/cierre/:date`, `/actas`).

## 5. Plataforma (`PLATFORM_ADMIN`): instituciones

**Lo que decide:** dar de alta una institución, su primer administrador y su hardware, y entrar a configurar una institución cuando hace falta soporte.

| Ruta | Qué ve | Fuente |
|---|---|---|
| `/plataforma/instituciones` | Lista de instituciones con su estado (activa, simulada), botón para crear desde plantilla y acceso a configurar cada una con token temporal (`act_as=platform`, auditado). | `GET /platform/institutions`, `POST /platform/institutions`, `POST /platform/institutions/{id}/act-as` (B2B) |
| Importación de historial | Solo para instituciones simuladas. | `POST /imports/readings`, permiso `IMPORT_SIMULATED` |

## 6. Login y entrada (público, antes de iniciar sesión)

El usuario elige primero la institución: un buscador sobre la lista pública (`GET /public/institutions`, solo `{slug, name}`) o la ruta `/i/:slug/login`. Luego escribe usuario y contraseña. El superadmin entra con la opción "CAUDAL (plataforma)". No hay página pública de horarios ni de daños.

## 7. Resumen de las rutas de inicio

| Rol | Ruta de inicio | Nivel ISA-101 |
|---|---|---|
| `OPERATOR` | `/inicio`: tanque en vivo, líneas y válvulas, pronóstico, eventos y asistente | 2 (operación) |
| `INSTITUTION_ADMIN` | `/inicio`, con accesos a `/admin/*` | 2 (operación), con configuración en el nivel 3 |
| `VIEWER` | `/inicio`, solo lectura | 2 (solo lectura) |
| `PLATFORM_ADMIN` | `/plataforma/instituciones` | 1 (panorama) |

## Fuentes

- ISA. ANSI/ISA-101.01, *Human Machine Interfaces for Process Automation Systems*, e ISA-TR101.02. Resumen de los niveles en [Control Global, "How to design effective HMIs"](https://www.controlglobal.com/articles/2018/how-to-design-effective-hmis) y [Automation.com, "Modern DCS graphics: level 1 overview displays"](https://www.automation.com/article/modern-dcs-graphics-level-1-overview-displays).
- Tatsoft. [ISA-101 HMI compliance how-to](https://docs.tatsoft.com/display/FX/ISA-101+HMI+Compliance+How-to).
- ORISE. [Boosting operator effectiveness: traditional vs high performance HMI](https://orise.com/boosting-operator-effectiveness-traditional-vs-high-performance-hmi).
- Las Virgenes Municipal Water District. [Estándar de pantallas SCADA](https://lvmwd.com/home/showpublisheddocument/12080/637117555607600000) (ejemplo de un acueducto).

Relacionados: [Actores-y-permisos.md](Actores-y-permisos.md), [Modulo-IA.md](Modulo-IA.md), [Estudio-llenado-de-tanques.md](Estudio-llenado-de-tanques.md), [Historias-de-usuario.md](Historias-de-usuario.md)
