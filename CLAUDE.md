# CLAUDE.md

Este archivo guía a Claude Code en este repositorio. Las reglas del proyecto están en `AGENTS.md`, que es la fuente única; no las dupliques aquí:

@AGENTS.md

## Notas específicas para Claude Code

- **Idioma:** código en inglés; documentación, commits, PR, mensajes de error y Swagger en español (sección 0 de `AGENTS.md`).
- **Commits:** `tipo: descripción` en español, uno por unidad lógica. **Nunca** agregues `Co-Authored-By` de Claude ni "Generated with Claude Code" en commits, PR ni releases; la atribución está desactivada en la configuración del proyecto y el hook la rechaza.
- **Cuentas:** antes de commitear, cambia a la cuenta del integrante dueño de la tarea (`gh auth switch` + `git config user.name/user.email`) y verifica con `gh api user --jq .login`. Solo `NicoalsD`, `Drako2305` y `nicomora70`. Si la cuenta no está disponible, detente y pregunta; no publiques con otra.
- **Subagentes:** se pueden delegar tareas a Claude Haiku o a OpenCode Go (`~/tools/opencode-delegate`), pero solo Claude hace los commits, después de revisar el diff.
- **Antes de implementar** lee el documento de `docs/` del módulo y `.agents/input-validation.md`; nada de valores quemados.
- **Diagramas:** usa el MCP de draw.io (`search_shapes` para iconos) y exporta el `.png` junto al `.drawio`.
