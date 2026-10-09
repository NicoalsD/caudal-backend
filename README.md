# CAUDAL Backend

API REST de **CAUDAL**, el cuaderno digital del acueducto veredal: reglas versionadas de la Junta, lecturas validadas del tanque, pronóstico con IA y respaldo simple, propuesta de turnos explicada, aprobación de la Junta, publicación sin datos personales, cierre del día y actas.

Java 25 · Spring Boot 4.1 · PostgreSQL 18 · Swagger.

![Contexto](docs/images/c4-contexto.png)

## Estado

**Fase 0: planeación y arquitectura.** Este repositorio contiene por ahora las reglas del proyecto, la documentación canónica y los diagramas. El código empieza en la Fase 1 ([orden de trabajo](docs/Roles-y-planeacion.md)).

## Documentación

- [Índice de la documentación](docs/README.md): problema, actores, historias de usuario, requerimientos, reglas de negocio, arquitectura, modelo de datos, API, seguridad, patrones, IA, datos simulados, hardware, planeación y plan de commits.
- [AGENTS.md](AGENTS.md): reglas obligatorias para personas y agentes.
- [Flujo de trabajo](.agents/workflow.md): cuentas, ramas, commits y PR.
- [Política de seguridad](SECURITY.md) y [cómo contribuir](CONTRIBUTING.md).

## Cómo correrlo (desde la Fase 1)

```bash
cp .env.example .env                 # completa los valores locales
docker compose up -d db
./mvnw spring-boot:run
```

Swagger: `http://localhost:8080/swagger-ui.html`.

## Repositorios del proyecto

| Repositorio | Contenido |
|---|---|
| `caudal-backend` (este) | API, dominio, seguridad, base de datos y documentación canónica |
| [`caudal-frontend`](https://github.com/NicoalsD/caudal-frontend) | PWA del fontanero, panel de la Junta y página pública |
| [`caudal-ia`](https://github.com/NicoalsD/caudal-ia) | Pronóstico con Chronos |
| [`caudal-simulador`](https://github.com/NicoalsD/caudal-simulador) | Simulador de datos de la región y del hardware |

## Equipo

Nicolas Diaz (`NicoalsD`) · Drako Salazar (`Drako2305`) · Nicolas Mora (`nicomora70`). Proyecto de la materia Patrones de Software.
