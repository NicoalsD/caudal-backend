# Política de seguridad

Gracias por ayudar a proteger CAUDAL, el sistema que organiza el racionamiento del agua en las veredas de Guaitarilla (Nariño, Colombia). Un fallo de seguridad aquí puede afectar a familias reales, a sus decisiones sobre el agua y a sus datos personales, así que tu reporte importa.

## Cómo reportar una vulnerabilidad

**No abras un issue público.** Un issue público expone la vulnerabilidad antes de que exista una corrección.

Repórtala en privado desde la pestaña **Security** de este repositorio:

1. Ve a **Security** > **Advisories** > **Report a vulnerability**.
2. Describe el problema con la información de la sección siguiente.
3. Envía el reporte. Solo el equipo del proyecto puede verlo.

Si no puedes usar la pestaña Security, escribe a los responsables del repositorio por un canal privado del equipo (por definir). No publiques el detalle en ningún otro lugar.

## Qué incluir

- Qué parte del sistema afecta: endpoint (`/api/v1/...`), pantalla, script o tabla.
- Qué versión o commit probaste (etiqueta o hash de `develop`).
- Pasos exactos para reproducirlo, con datos de prueba. No uses datos reales de personas ni de acueductos reales.
- Qué impacto tendría: qué datos o acciones quedarían expuestos, y para quién.
- Cualquier prueba de concepto, captura o registro que ayude. Quita contraseñas, tokens y cookies antes de enviarlos.
- Si quieres que te mencionemos en la corrección, dilo en el reporte.

Para vulnerabilidades en la base de datos, las cabeceras, la autenticación o los dispositivos, indica también qué control de la [política de seguridad](docs/Seguridad.md) crees que falla.

## Tiempos de respuesta objetivo

Estos tiempos son objetivos del equipo, no garantías. Se confirman antes de la primera versión pública (por verificar).

| Etapa | Objetivo |
|---|---|
| Acuse de recibo | Propuesta: 72 horas hábiles (verificar) |
| Confirmación de si es una vulnerabilidad y de su severidad | Propuesta: 14 días (verificar) |
| Corrección para severidad crítica o alta | Por definir |
| Corrección para severidad media o baja | Por definir |
| Publicación del aviso de seguridad | Después de la corrección, coordinada con quien reportó |

Te mantendremos informado del avance en el mismo reporte privado.

## Versiones soportadas

| Rama o versión | ¿Recibe correcciones de seguridad? |
|---|---|
| `develop` | Sí. Es la rama de integración |
| Última etiqueta publicada en `main` (`vX.Y.Z`) | Sí |
| Etiquetas anteriores | No, salvo decisión del equipo |

El proyecto está en desarrollo (fases 0 a 12). Antes de la versión `v1.0.0`, las correcciones llegan a `develop` y se etiquetan en la siguiente versión.

## Alcance

Dentro del alcance:

- La API REST `/api/v1/*` de este repositorio (`caudal-backend`), incluida la autenticación, la autorización, la validación de entradas, el aislamiento por acueducto (RLS), la auditoría y la firma de dispositivos.
- La base de datos, sus roles, sus privilegios y sus triggers, tal como se definen en las migraciones.
- La configuración de despliegue que está en el repositorio (`Dockerfile`, `docker-compose`, workflows de GitHub Actions).

Fuera del alcance:

- Los repositorios `caudal-frontend`, `caudal-ia` y `caudal-simulador`. Sus vulnerabilidades se reportan en esos repositorios, con la misma política.
- Los datos del acueducto demo, que son simulados.
- Ataques que requieren acceso físico a un dispositivo o a un gateway.
- Ingeniería social contra integrantes del equipo.
- Pruebas de carga o denegación de servicio sin coordinación previa.
- Problemas de configuración de servicios de terceros (Neon, Render, Vercel, Hugging Face) que no vienen de este código. Repórtalos al proveedor, y avísanos si afectan a CAUDAL.
- Hallazgos sin impacto demostrado, como cabeceras faltantes en páginas que no tienen datos.

## Reglas para quien investiga

- Prueba solo sobre tu propia instalación local o sobre el acueducto demo.
- No accedas, modifiques ni borres datos que no sean tuyos.
- No uses ataques que degraden el servicio de otros usuarios.
- Si accedes a datos sin querer, deja de leer, no los copies y avísanos.

Investigar de buena fe conforme a estas reglas no genera acciones legales de parte del proyecto.

## Reconocimiento

Con tu permiso, te mencionaremos en las notas de la corrección. Podemos acordar otra forma de reconocimiento.

## Más información

La política completa de seguridad (modelo de amenazas, controles y cifras) está en [`docs/Seguridad.md`](docs/Seguridad.md). La de la base de datos está en [`docs/Seguridad-de-la-base-de-datos.md`](docs/Seguridad-de-la-base-de-datos.md).

Relacionados: [`docs/Seguridad.md`](docs/Seguridad.md), [`docs/Seguridad-de-la-base-de-datos.md`](docs/Seguridad-de-la-base-de-datos.md), [`.agents/security.md`](.agents/security.md)
