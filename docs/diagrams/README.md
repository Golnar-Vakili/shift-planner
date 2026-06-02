# Thesis ER diagrams (Datenmodell)

## Files

| File | Description |
|------|-------------|
| `datenmodell-planung.svg` | Workforce planning domain (high-res vector, white background) |
| `datenmodell-kommunikation-workflow.svg` | Communication & workflow domain |
| `datenmodell-planung.mmd` | Mermaid source (planning) |
| `datenmodell-kommunikation-workflow.mmd` | Mermaid source (communication) |
| `generate_diagrams.py` | Regenerates SVG (and PNG if tools available) |

## LaTeX (recommended)

```latex
\usepackage{svg}
\includegraphics[width=\linewidth]{datenmodell-planung.svg}
```

Or convert SVG to PDF once with Inkscape:

```bash
inkscape datenmodell-planung.svg --export-filename=datenmodell-planung.pdf
```

## Mermaid preview

Use the `.mmd` files or `*-mermaid.md` files in Mermaid Live / VS Code.  
Syntax is plain `erDiagram` only (no `classDef`, no `%%{init}%%`).  
The DB column `app_notifications.type` is shown as `notification_type` in Mermaid to avoid the reserved word `type`.

## Regenerate SVG (colored, white background)

```bash
python generate_diagrams.py
```

## Color legend

- **Blue** (`users`) – central identity entity
- **Green** – shift planning (`shift_plans`, `shift_entries`)
- **Amber** – vacation (`vacation_requests`, `vacation_request_days`)
- **Purple** – messaging (`app_chat_messages`, `app_notifications`)
- **Rose** – support & overtime workflow tables

Solid relationship lines = Flyway FK enforced. Dashed lines = logical FK (Long IDs in Java only).
