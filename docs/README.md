# Documentación de CAUDAL

**CAUDAL** es el cuaderno digital del agua de cada institución. Es un producto B2B: CAUDAL da de alta cada institución y la configura según su lugar (tanque, sensor ultrasónico y válvulas). La institución opera el tanque en vivo, abre y cierra válvulas con confirmación del dispositivo, ve un pronóstico claro, registra observaciones y cierra el día con actas.

> Proyecto académico de la materia **Patrones de Software**. Backend en Java (exigencia del curso), frontend PWA, módulo de IA con Chronos y un asistente con un LLM compatible con OpenAI, simulador de instituciones y una maqueta física con Arduino UNO.
>
> **Equipo:** Nicolas Diaz (`NicoalsD`) · Drako Salazar (`Drako2305`) · Nicolas Mora (`nicomora70`)

![Cómo funciona CAUDAL](images/flujo-caudal.png)

(diagrama en actualización)

Esta carpeta es la **documentación canónica** de los 4 repositorios. Los demás repositorios enlazan aquí.

> **Alcance B2B (2026-11-20):** cada institución tiene sus datos aislados. El sensor mide el nivel en vivo y se calibra desde la app. Las válvulas se operan desde la app con su ack. El pronóstico se muestra en tres salidas separadas, con un piloto automático de tres modos y un asistente para preguntas. Se elimina la operación por turnos, la página pública y los reportes de familias.

## Producto

| Documento | Contenido |
|---|---|
| [Caudal](Caudal.md) | Problema B2B, objetivos, alcance, los 7 pasos, módulos M1 a M10 y principios |
| [Actores y permisos](Actores-y-permisos.md) | Superadmin de plataforma, admin, operador, lector y dispositivos; roles y matriz de permisos |
| [Historias de usuario](Historias-de-usuario.md) | HU con prioridad MoSCoW y criterios Gherkin |
| [Requerimientos](Requerimientos.md) | Funcionales (RF) y no funcionales (RNF) con trazabilidad |
| [Reglas de negocio](Reglas-de-negocio.md) | Instituciones, geometría del tanque, sensor en vivo, pronóstico, eventos, válvulas, piloto, cierre y actas |
| [Glosario](Glosario.md) | Términos del dominio y su nombre en el código |

## Técnico

| Documento | Contenido |
|---|---|
| [Stack tecnológico](Stack-tecnologico.md) | Tecnologías por capa con su justificación y alternativas descartadas |
| [Arquitectura](Arquitectura.md) | C4, arquitectura hexagonal, flujos de login, válvula, telemetría y pronóstico, despliegue y decisiones |
| [Modelo de datos](Modelo-de-datos.md) | Esquemas, tablas, relaciones, migraciones y semilla (número de tablas por verificar al regenerar) |
| [Diccionario de datos](Diccionario-de-datos.md) | Cada columna con tipo, restricciones y clasificación (generado) |
| [API REST](API.md) | Convenciones, errores, Swagger y todos los endpoints |
| [Patrones de diseño](Patrones-de-diseno.md) | Catálogo P01 a P23 por repositorio, con dueño y prueba |
| [Módulo de IA](Modulo-IA.md) | Pronóstico con Chronos en la escala de cada institución, asistente con LLM, respaldo, circuit breaker y evaluación |
| [Contrato backend-IA](Contrato-IA.md) | Endpoints de pronóstico y del asistente (`/v1/insights/*`), esquemas, errores y pruebas de contrato |
| [Datos simulados](Datos-simulados.md) | Instituciones simuladas, cómo se marcan los datos e importación de historial |
| [Simulador y hardware](Simulador-y-hardware.md) | Maqueta real (Arduino UNO por USB), puente `caudal-sim maqueta-usb`, maqueta virtual y hardware de campo como referencia |
| [Protocolo de dispositivos](Protocolo-de-dispositivos.md) | Firmas Ed25519, telemetría `DISTANCE`, configuración firmada y comandos de válvula |

## Seguridad y configuración

| Documento | Contenido |
|---|---|
| [Seguridad](Seguridad.md) | Modelo de amenazas y controles: login por institución, superadmin, aislamiento y hardware |
| [Seguridad de la base de datos](Seguridad-de-la-base-de-datos.md) | Roles, RLS por `institution_id`, funciones `platform.*`, append-only y cadena de hashes |
| [Configuración sin valores quemados](Configuracion-sin-valores-quemados.md) | Una sola fuente por valor: `org.institution_settings`, geometría y calibración del tanque, `FieldLimits`, entorno e i18n |

## Estudios (v2)

| Documento | Contenido |
|---|---|
| [Estudio de llenado de tanques](Estudio-llenado-de-tanques.md) | Cadena por gravedad, partes del tanque, operación diaria y modelo físico para la escena 3D y el simulador |
| [Estudio de dashboards por rol](Estudio-dashboards-por-rol.md) | Qué decide cada rol, su primera pantalla y los endpoints y widgets que la sustentan |

## Gestión

| Documento | Contenido |
|---|---|
| [Roles y planeación](Roles-y-planeacion.md) | Roles del equipo, fases, dependencias, DoR y DoD |
| [Plan de commits](Plan-de-commits.md) | Commits previstos por repositorio, fase e integrante |
| [Riesgos](Riesgos.md) | Riesgos con probabilidad, impacto, mitigación y contingencia |
| [Versión 1.0.0](Version-1.0.0.md) | Endurecimiento, verificación, despliegue y pendientes de la etiqueta `v1.0.0` (versión anterior al alcance B2B) |
| [Flujo de trabajo](../.agents/workflow.md) | Cuentas, ramas, commits y pull requests |

## Diagramas

Todos se hacen con draw.io (MCP de draw.io y sus iconos). Cada diagrama tiene su fuente `.drawio` y su exportación `.png` en [`images/`](images/). Los diagramas marcados "en actualización" reflejan el modelo anterior hasta que se reexporten.

| Diagrama | Archivo |
|---|---|
| Flujo de CAUDAL en 7 pasos | [flujo-caudal](images/flujo-caudal.png) |
| Casos de uso | [casos-de-uso](images/casos-de-uso.png) |
| Contexto y contenedores (C4) | [c4-contexto](images/c4-contexto.png) (en actualización) · [c4-contenedores](images/c4-contenedores.png) (en actualización) |
| Arquitectura hexagonal | [capas-backend](images/capas-backend.png) |
| Modelo de datos | [general](images/modelo-de-datos.png) · [iam](images/modelo-de-datos-iam.png) · [org](images/modelo-de-datos-org.png) · [ops lecturas](images/modelo-de-datos-ops-lecturas.png) · [ops turnos](images/modelo-de-datos-ops-turnos.png) · [reporting, audit, sim](images/modelo-de-datos-reporting-audit-sim.png) · [devices](images/modelo-de-datos-devices.png) (en actualización) |
| Secuencias | [login](images/secuencia-login.png) · [comando de válvula](images/secuencia-comando-valvula.png) · [telemetría](images/secuencia-telemetria.png) · [pronóstico](images/secuencia-pronostico.png) |
| Estados | [comando de válvula](images/estados-comando-valvula.png) · [circuit breaker](images/estados-circuit-breaker.png) |
| Patrones | [catálogo](images/catalogo-patrones.png) · [backend](images/patrones-backend.png) (en actualización) |
| IA y simulación | [arquitectura de la IA](images/arquitectura-ia.png) · [datos simulados](images/flujo-datos-simulados.png) · [modelo físico](images/modelo-fisico-acueducto.png) · [hardware propuesto](images/hardware-propuesto.png) (en actualización) |
| Gestión | [fases](images/dependencias-fases.png) · [Git Flow](images/git-flow.png) · [CI/CD](images/ci-cd.png) · [despliegue](images/despliegue.png) |

Relacionados: [AGENTS.md](../AGENTS.md) · [README](../README.md)
