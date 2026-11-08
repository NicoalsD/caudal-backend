# Estudio: qué ve cada rol al entrar

Este estudio decide la **primera pantalla** de cada rol en CAUDAL v2. El método parte de lo que cada persona tiene que decidir, sigue con los datos que necesita para eso y termina en los widgets. Cada widget usa un endpoint que ya existe, o uno nuevo de la v2 (marcado con **v2**).

## 1. Principios (tomados del diseño de HMI industrial, ISA-101)

Los sistemas SCADA de agua y de plantas industriales usan las pautas de HMI de alto desempeño de ANSI/ISA-101. CAUDAL toma de ahí cuatro ideas:

1. **Jerarquía de pantallas.**
   - Nivel 1: panorama de todo el acueducto.
   - Nivel 2: operación de un tanque y sus sectores.
   - Nivel 3: detalle de una llave o un sensor.
   - Nivel 4: diagnóstico, con tendencias e historial.
   - Cada rol **entra al nivel que más usa**, y de ahí baja con uno o dos clics.
2. **El color señala lo anormal.** Lo normal se ve en tonos neutros de la marca (turquesas). El ámbar y el rojo quedan para las alertas: nivel bajo, sensor sospechoso, comando fallido. Así lo urgente se ve primero.
3. **Datos con contexto.** No basta "1,8 m": la cifra lleva la tendencia, la banda de la regla (verde, ámbar, rojo), cuánto hace que se midió y el pronóstico.
4. **El panorama no se llena de detalle.** El nivel 1 muestra pocos números grandes. El detalle va en las pantallas de abajo.

En CAUDAL v2 se agregan dos más:

5. **El asistente de IA está en todas las pantallas.** Un panel lateral muestra la recomendación del momento con su motivo, en ASSISTED deja aceptarla o rechazarla, y tiene la caja "Pregúntale a Caudal". También dice si la respuesta vino del LLM o de las reglas de respaldo.
6. **Pantalla completa.** El contenido usa todo el ancho, en celular y en escritorio. En escritorio, las páginas se arman en grillas de 2 o 3 columnas.

## 2. Fontanero (`OPERATOR`): la escena 3D primero

**Lo que decide:**
- Qué llave abrir o cerrar ahora.
- Si le cree al sensor o va a medir.
- Si un daño se reporta.
- Si toma el control o lo cede al piloto automático.

**Por qué la escena 3D:** el fontanero piensa en el acueducto como algo físico: el tanque, los tubos, las llaves. Una escena que se parece a lo que tiene en frente le exige menos esfuerzo que una tabla. Así trabajan los sinópticos (mímicos) de un SCADA, y es el nivel 2 de ISA-101.

| Zona | Widget | Fuente de datos |
|---|---|---|
| Centro (toda la pantalla) | **Escena 3D**: entrada con partículas, tanque con el nivel en vivo y la banda de color, rebose, una tubería por sector con partículas cuya velocidad sale del caudal (ver [Estudio de llenado](Estudio-llenado-de-tanques.md)) y **llaves 3D que se pulsan**. | `GET /network`, `GET /tanks/{id}/status`, `GET /devices`, telemetría **v2** |
| Sobre cada llave | Etiqueta con el sector, el estado (abierta o cerrada) y el comando en curso (pendiente, entregado o confirmado). | `GET /sectors/{id}/valves`, comandos **v2** |
| Al pulsar una llave | Ventana con Abrir o Cerrar, el motivo y una confirmación. | `POST /valve-commands/manual` (ahora también para OPERATOR, **v2**) |
| Barra superior | Modo actual (Manual, Asistido o Automático) y el botón "Tomar control" o "Ceder al piloto". | `autopilot_settings` **v2** |
| Panel lateral | Turno actual con su cuenta regresiva, el siguiente turno, la recomendación del asistente y las alertas (anomalías, sensor viejo). | `GET /days/{date}/schedule`, `GET /anomalies`, recomendaciones **v2** |
| Acciones rápidas | Registrar una lectura, reportar un daño y marcar un turno ejecutado. | Pantallas que ya existen |

En el celular, la escena ocupa la pantalla, el panel lateral pasa a una hoja que se desliza desde abajo y se mantiene la barra de pestañas.

## 3. Junta (`BOARD_ADMIN`, `BOARD_MEMBER`): panorama y decisiones

**Lo que decide:**
- Aprobar, modificar o rechazar la propuesta de turnos.
- Cambiar las reglas y las regiones.
- Elegir el modo del piloto.
- Revisar las anomalías y los incidentes.
- Leer el reporte para la asamblea.

Es el nivel 1 de ISA-101: pocos números grandes y luego las tareas pendientes.

| Zona | Widget | Fuente de datos |
|---|---|---|
| Fila de KPIs | Nivel actual con su banda, **días de reserva** (con el pronóstico p50), cobertura de la semana por región (horas servidas contra horas prometidas) e incidentes abiertos. | `GET /tanks/{id}/status`, `GET /tanks/{id}/forecasts/latest`, `GET /days/{date}/schedule`, `GET /incidents` |
| Gráfica principal | Nivel de los últimos 14 días y el pronóstico con su rango p10–p90, más las líneas de las bandas. | `GET /readings`, `GET /tanks/{id}/forecasts/latest` |
| Mini escena 3D | La misma escena del fontanero, más pequeña y de solo lectura, con un clic para abrirla completa. | Igual que en la sección 2 |
| Pendientes | La propuesta de turnos por aprobar, las anomalías sin resolver y los reportes de daño nuevos. | `GET /schedule-proposals`, `GET /anomalies`, `GET /incidents` |
| Registro del piloto | Las últimas decisiones del piloto: qué hizo, por qué, de dónde vino la decisión (LLM o reglas) y quién la aceptó. | `autopilot_decisions` **v2** |
| Reporte | Tarjeta con el resumen narrativo de la semana, escrito por el asistente, y un enlace al reporte completo. | `POST /reports/narrative` **v2** |
| Panel lateral | El asistente de IA. | **v2** |

## 4. Equipo del proyecto (`PROJECT_TEAM`): calidad del modelo y de los sensores

**Lo que decide:** si el modelo de IA sirve, si un sensor necesita calibrarse y si el piloto se está equivocando.

| Widget | Fuente de datos |
|---|---|
| Error del pronóstico contra la persistencia (MAE) y cobertura del rango p10–p90 | `GET /forecast-evaluation` |
| Salud de los dispositivos: última telemetría, batería, señal y llave sin respuesta | `GET /devices` |
| Tiempo de los comandos, desde que se crean hasta el ack | Comandos **v2** |
| Respuestas del asistente que vinieron de las reglas (el LLM falló o se quedó sin cuota) | `autopilot_decisions` **v2** |
| Anomalías por tipo | `GET /anomalies` |

## 5. Entidad de apoyo (`SUPPORT_ENTITY`): seguimiento

**Lo que decide:** si la vereda necesita apoyo (carrotanque, obra o reparación).

| Widget | Fuente de datos |
|---|---|
| Horas de servicio por región en el mes, contra el mes anterior | `GET /summaries` |
| Incidentes del periodo y tiempo de cierre | `GET /summaries` |
| Tendencia del nivel por mes | `GET /summaries` |
| El reporte narrativo compartido | `GET /summaries`, reporte **v2** |

## 6. Público (familias): saber cuándo llega el agua

Esta pantalla no cambia de fondo: el horario publicado de su región, el tanque en un semáforo y el botón para reportar un daño (`GET /public/{aqueductSlug}/schedule`). Se agrega un filtro por región.

## 7. Resumen de las rutas de inicio

| Rol | Ruta de inicio | Nivel ISA-101 |
|---|---|---|
| OPERATOR | `/operador/inicio`: escena 3D | 2 (operación) |
| BOARD_ADMIN / BOARD_MEMBER | `/junta/tanque`: panorama de la Junta | 1 (panorama) |
| PROJECT_TEAM | `/equipo/evaluacion`: calidad | 4 (diagnóstico) |
| SUPPORT_ENTITY | `/entidad/resumenes`: seguimiento | 1 (panorama) |
| Público | `/a/:aqueductSlug` | 1 |

## Fuentes

- ISA. ANSI/ISA-101.01, *Human Machine Interfaces for Process Automation Systems*, e ISA-TR101.02. Resumen de los niveles en [Control Global, "How to design effective HMIs"](https://www.controlglobal.com/articles/2018/how-to-design-effective-hmis) y [Automation.com, "Modern DCS graphics: level 1 overview displays"](https://www.automation.com/article/modern-dcs-graphics-level-1-overview-displays).
- Tatsoft. [ISA-101 HMI compliance how-to](https://docs.tatsoft.com/display/FX/ISA-101+HMI+Compliance+How-to).
- ORISE. [Boosting operator effectiveness: traditional vs high performance HMI](https://orise.com/boosting-operator-effectiveness-traditional-vs-high-performance-hmi).
- Las Virgenes Municipal Water District. [Estándar de pantallas SCADA](https://lvmwd.com/home/showpublisheddocument/12080/637117555607600000) (ejemplo de un acueducto).
