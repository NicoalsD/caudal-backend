# CAUDAL: visión general del proyecto

> **El cuaderno del acueducto, pero digital.**

Este documento explica qué problema resuelve CAUDAL, qué incluye esta versión, cómo funciona el sistema en palabras simples y cómo se organiza el trabajo. Es la puerta de entrada a la documentación. Los detalles viven en los demás documentos de `docs/` (ver la línea "Relacionados" al final).

## 1. Problema

En las veredas de Guaitarilla (Nariño, Colombia) el agua se reparte por turnos. Hoy el proceso es manual:

- **Racionamiento a mano:** para cada sector, alguien abre y cierra válvulas según lo que dice el tanque. No hay un orden escrito que todos sigan.
- **Registros en papel:** el nivel del tanque, las novedades y los daños se anotan en cuadernos. Se pierden, no se comparten y es difícil revisarlos después.
- **Decisiones sin explicación:** cuando un sector se queda sin agua más tiempo que otro, nadie puede mostrar por qué se decidió así. La comunidad no ve la razón y la Junta no tiene un registro de lo que aprobó.
- **Información incompleta:** no hay un pronóstico del nivel del tanque. Se decide con la última lectura y la intuición.

El resultado: agua mal repartida, discusiones en la comunidad y poca capacidad de anticipar un día de escasez.

## 2. Objetivos

### 2.1 Objetivo general

Hacer el racionamiento menos manual, con registros digitales en lugar de papel, un pronóstico del nivel del tanque con IA y decisiones explicadas y aprobadas por la Junta del acueducto.

### 2.2 Objetivos específicos

| ID | Objetivo | Medida de verificación |
|---|---|---|
| OE-01 | Registrar el nivel del tanque desde el celular, también sin señal. | Una lectura se guarda sin conexión y llega al servidor una sola vez al sincronizar. |
| OE-02 | Validar cada lectura con reglas simples y pedir corrección cuando no tiene sentido. | Un valor fuera de rango no se acepta sin corrección. Las correcciones no borran el dato original. |
| OE-03 | Mostrar el estado del tanque con su antigüedad y un pronóstico con rango. | El estado indica hace cuántas horas se tomó la lectura. El pronóstico muestra p10, p50 y p90. |
| OE-04 | Generar una propuesta de turnos explicada por código y parámetros. | Cada turno tiene al menos una razón estructurada (`reason_code`). |
| OE-05 | Obligar a la Junta a aprobar o modificar (con motivo) antes de publicar. | Ningún horario se publica sin decisión registrada. |
| OE-06 | Informar a las familias sin exponer datos personales. | La página pública, el mensaje de WhatsApp y el cartel no muestran nombres ni teléfonos. |
| OE-07 | Dejar trazabilidad: reglas versionadas, decisiones, correcciones y auditoría. | Todo cambio relevante queda en un registro de solo inserción (append-only). |
| OE-08 | Medir si la IA ayuda de verdad, comparándola con una estimación simple. | Existe una evaluación con MAE, WQL, cobertura y skill por horizonte. |
| OE-09 | Probar el sistema sin datos reales, con un simulador que produce datos verosímiles. | Los datos del acueducto demo se marcan como "Datos simulados" en todas las pantallas. |

## 3. Alcance

### 3.1 Incluye en esta versión

- Aplicación web instalable (PWA) para el fontanero, el panel de la Junta y la página pública.
- API REST en Java 25 con Spring Boot 4.1, base de datos PostgreSQL 18 con siete esquemas.
- Servicio de pronóstico con IA (Chronos) con respaldo por estimación simple.
- Reglas de la Junta versionadas, propuesta de turnos, decisión, publicación, cierre del día y actas en PDF.
- Página pública, mensaje para WhatsApp (el sistema lo genera y el usuario lo copia) y cartel en PDF.
- Reportes de daño desde el formulario público, con código de seguimiento.
- Simulador de datos de la región y del hardware, con API local y tablero.
- Dispositivos firmados (Ed25519) simulados, con su API de telemetría y comandos.
- Importación de historial simulado, solo en el acueducto demo.

### 3.2 No incluye en esta versión

| Elemento | Motivo | Cómo se trata |
|---|---|---|
| Hardware real (sensor, ESP32-S3, válvulas motorizadas, LoRa o LTE, panel solar, gabinete) | Costo, tiempo y ausencia de terreno para probarlo. | Solo existe la propuesta documentada en `caudal-backend/docs/` y el diagrama `hardware-propuesto`. Los valores técnicos son de referencia: verificar con fichas y proveedores locales. |
| Datos reales de la región (niveles, lluvias, consumo, fugas) | No se dispone de ellos. | Todo se simula con `caudal-simulador` y se marca "Datos simulados". |
| Envío automático de WhatsApp o SMS | El sistema solo genera el texto y el botón lo copia. | Por definir si se incorpora en una versión futura. |
| Control automático de válvulas sin aprobación | La decisión es de la Junta. | Los comandos solo salen de turnos aprobados, o manuales con motivo de `BOARD_ADMIN`. |
| Facturación, cobro de agua o pagos | Fuera del problema. | No aplica. |
| Validación clínica o técnica formal de la IA | Requiere más datos. | El criterio de evaluación queda documentado (ver `Requerimientos.md`). |

El sistema **no reemplaza a la Junta ni al fontanero**. Propone, explica y registra. La decisión es humana.

## 4. Cómo funciona: los 10 pasos

Esta sección resume el flujo completo en palabras simples. El diagrama está en la sección 6.

![Flujo de CAUDAL en diez pasos](images/flujo-caudal.png)

1. **La Junta pone las reglas** (una vez y cada vez que cambien): sectores y orden de sus válvulas; horario en que se puede operar; reserva mínima de agua; qué hacer según el nivel del tanque (ejemplo: tanque alto = 16 horas de servicio al día, bajo = 8 horas, crítico = 3 horas solo para lo prioritario, como la escuela). Cada cambio queda como una versión nueva; siempre se sabe qué reglas estaban vigentes en cada momento.
2. **El fontanero mira el tanque y lo anota en el celular:** número de la regla pintada, si el agua se ve normal o con barro, y si notó algún daño. Sin señal, el celular lo guarda y lo envía cuando vuelva internet.
3. **El sistema revisa que el dato tenga sentido** (sin IA, reglas simples): si la regla va de 0 a 5 y escriben 8, pide corregir; detecta datos repetidos o sin fecha. Las correcciones nunca borran el dato original.
4. **El sistema muestra cómo está el tanque:** último nivel, hace cuántas horas se tomó y si viene bajando. Le pregunta a la IA (Chronos) el nivel probable en 1 a 3 días con un rango ("lo más probable es 2,1, pero podría estar entre 1,8 y 2,4"). Si la IA no responde, usa una estimación simple ("seguirá igual que hoy") y todo sigue funcionando.
5. **El sistema arma una propuesta de turnos:** según el nivel y las reglas calcula cuántas horas de agua hay para repartir hoy. Primero los sectores prioritarios; después el que lleva más tiempo sin agua. Cada decisión se explica ("Sector Alto lleva 38 horas sin servicio").
6. **La Junta revisa y decide:** aprueba tal cual o cambia la propuesta, pero si cambia debe escribir por qué. Sin aprobación no se publica nada.
7. **Las familias se enteran:** página pública sin nombres ni teléfonos, mensaje para WhatsApp (botón que copia el mensaje) o cartel impreso. También pueden reportar un daño.
8. **Al final del día, el fontanero anota qué pasó realmente:** turnos cumplidos, a medias y novedades.
9. **La Junta saca un acta cuando la necesite**, separando cuatro tipos de información: **observado** (lo que se vio), **estimado** (lo que se calculó), **inferido** (lo que se sospecha) y **confirmado** (lo confirmado en campo).
10. **Los demás actores:** el equipo del proyecto mantiene el sistema y compara la IA con la estimación simple para saber si de verdad ayuda; las entidades de apoyo solo ven resúmenes que la Junta autorice.

Puntos clave de los pasos:

- Los pasos 3 y 5 son deterministas (reglas simples, sin IA). El paso 4 es el único que consulta la IA, y tiene un respaldo determinista (la estimación simple).
- El paso 6 es el único punto donde algo pasa de "propuesto" a "publicado". Es una compuerta obligatoria.
- El paso 8 alimenta las horas sin servicio de la siguiente propuesta: solo cuentan los turnos confirmados.

## 5. Módulos funcionales

| ID | Módulo | Qué hace | Pasos | Repos principales |
|---|---|---|---|---|
| M1 | Login y usuarios | Ingreso, contraseñas, bloqueo, membresías por acueducto, cierre de sesión global, MFA opcional. | 2, 10 | `caudal-backend`, `caudal-frontend` |
| M2 | Reglas de la Junta versionadas | Borradores, bandas de nivel, activación con motivo, inmutabilidad de reglas activas. | 1 | `caudal-backend`, `caudal-frontend` |
| M3 | Lecturas offline con validación y correcciones | Registro sin señal, sincronización por lotes, cadena de validación, correcciones con motivo. | 2, 3 | `caudal-backend`, `caudal-frontend` |
| M4 | Estado del tanque y pronóstico | Estado con antigüedad del dato, pronóstico con rango, respaldo, anomalías. | 4 | `caudal-backend`, `caudal-ia`, `caudal-frontend` |
| M5 | Propuesta de turnos explicada | Cálculo de horas disponibles, asignación y explicaciones estructuradas. | 5 | `caudal-backend` |
| M6 | Decisión de la Junta | Aprobar, modificar con motivo o rechazar; conservar la propuesta original. | 6 | `caudal-backend`, `caudal-frontend` |
| M7 | Publicación y reportes de daño | Horario publicado, página pública, WhatsApp, cartel, reporte de daño con código de seguimiento. | 7 | `caudal-backend`, `caudal-frontend` |
| M8 | Cierre del día | Registro de turnos cumplidos, a medias o no ejecutados, y novedades. | 8 | `caudal-backend`, `caudal-frontend` |
| M9 | Actas y resúmenes para entidades | Actas en PDF con cuatro secciones; autorizaciones con vigencia para entidades de apoyo. | 9, 10 | `caudal-backend`, `caudal-frontend` |
| M10 | Evaluación IA vs estimación simple | Métricas de error, cobertura y skill por horizonte. | 10 | `caudal-backend`, `caudal-ia` |
| M11 | Dispositivos | Registro de dispositivos, telemetría firmada, comandos de válvula desde turnos aprobados. | Futuro (hardware) | `caudal-backend`, `caudal-simulador` |
| M12 | Auditoría | Bitácora de cambios, eventos de seguridad, cadena de hashes. | Transversal | `caudal-backend`, `caudal-frontend` |
| M13 | Datos simulados e importación de historial | Generación de datos, backfill en el acueducto demo, marcas de "Datos simulados". | Transversal | `caudal-simulador`, `caudal-backend` |

Las historias de usuario de cada módulo están en [Historias-de-usuario.md](Historias-de-usuario.md). Los requisitos, en [Requerimientos.md](Requerimientos.md).

## 6. Diagramas

| Diagrama | Archivo | Contenido |
|---|---|---|
| Flujo de CAUDAL | `images/flujo-caudal.png` | Los diez pasos y los actores que intervienen en cada uno. |
| Casos de uso | `images/casos-de-uso.png` | Actores y casos de uso por rol. Ver [Actores-y-permisos.md](Actores-y-permisos.md). |
| Contenedores (C4) | `images/c4-contenedores.png` | PWA, API, PostgreSQL, servicio IA y simulador. |
| Modelo de datos | `images/modelo-de-datos.png` | Siete esquemas y 57 tablas (7 páginas en el archivo fuente). |

Las fuentes `.drawio` están junto a su exportación `.png` en `docs/images/`.

## 7. Repositorios

| Repositorio | Tecnología | Contenido | Responsable actual |
|---|---|---|---|
| [`NicoalsD/caudal-backend`](https://github.com/NicoalsD/caudal-backend) | Java 25 LTS, Spring Boot 4.1.x, PostgreSQL 18 | API REST, dominio, seguridad, base de datos. Documentación canónica en `docs/`. | Drako2305, con NicoalsD |
| [`NicoalsD/caudal-frontend`](https://github.com/NicoalsD/caudal-frontend) | React 19, Vite, TypeScript (PWA) | App del fontanero (offline), panel de la Junta y página pública. | NicoalsD, con Drako2305 |
| [`NicoalsD/caudal-ia`](https://github.com/NicoalsD/caudal-ia) | Python 3.12, FastAPI, Chronos | Servicio de pronóstico, sin estado y sin acceso a la base de datos. | nicomora70 (cuando se una) |
| [`NicoalsD/caudal-simulador`](https://github.com/NicoalsD/caudal-simulador) | Python 3.12, FastAPI, tablero Vite/React | Gemelo digital de la región y del hardware. | nicomora70 (cuando se una) |

Ramas: `main` (versiones con etiqueta) y `develop` (integración, rama por defecto). Las reglas de GitHub exigen PR y el check `commit-lint`, prohíben el force-push y el borrado, y solo permiten merge commit.

## 8. Principios

Estos principios aplican a todo el sistema. Cualquier cambio que los contradiga requiere una decisión documentada.

1. **Sin aprobación no se publica.** Ningún horario llega a la comunidad sin una decisión registrada de la Junta (`APPROVED`, `APPROVED_WITH_CHANGES`).
2. **Las correcciones nunca borran.** Una lectura corregida conserva el dato original. La corrección es un registro nuevo con motivo (`reading_corrections`). Los turnos reemplazados usan `supersedes_id`.
3. **Lo público no tiene datos personales.** La página pública, el WhatsApp y el cartel no muestran nombres, teléfonos ni direcciones de personas. El `PublicScheduleProxy` quita esos datos antes de responder.
4. **La IA nunca bloquea la operación.** Si el servicio de IA falla o su circuito está abierto, el sistema usa la estimación simple y lo indica. El fontanero puede registrar lecturas y la Junta puede decidir sin IA.
5. **Lo observado, lo estimado, lo inferido y lo confirmado se distinguen.** Cada dato lleva su estatus epistémico (`OBSERVED`, `ESTIMATED`, `INFERRED`, `CONFIRMED`) y ninguno se presenta como otro.
6. **Lo simulado se declara.** Todo dato del acueducto demo se marca como "Datos simulados" en pantallas, PDF y mensajes.
7. **Los parámetros de negocio viven en la base de datos.** Cambiar "Máximo 24" por 23 crea una nueva versión de reglas, no un cambio de código.

## 9. Glosario mínimo

Los términos completos están en [Glosario.md](Glosario.md). Los más usados:

- **Regla pintada (limnimétrica):** escala de números pintada en el tanque. El fontanero anota el número que ve.
- **Turno:** período en que un sector recibe agua.
- **Banda de nivel:** rango de nivel del tanque (`HIGH`, `LOW`, `CRITICAL`) con sus horas de servicio.
- **Propuesta:** conjunto de turnos calculado por el sistema, pendiente de decisión de la Junta.
- **Acueducto demo:** acueducto de prueba (`aqueducts.is_demo = true`), simulado por definición.

Relacionados: [Actores-y-permisos.md](Actores-y-permisos.md) · [Historias-de-usuario.md](Historias-de-usuario.md) · [Requerimientos.md](Requerimientos.md) · [Riesgos.md](Riesgos.md)
