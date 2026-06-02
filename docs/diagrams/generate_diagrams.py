#!/usr/bin/env python3
"""Generate thesis-ready ER diagram SVG/PNG exports."""

from __future__ import annotations
import subprocess
import sys
from pathlib import Path

OUT = Path(__file__).resolve().parent
FONT = "Segoe UI, Arial, Helvetica, sans-serif"

# Colors (academic, soft)
CORE = {"fill": "#DBEAFE", "stroke": "#1D4ED8"}
SHIFT = {"fill": "#ECFDF5", "stroke": "#047857"}
VACATION = {"fill": "#FEF3C7", "stroke": "#B45309"}
COMM = {"fill": "#EDE9FE", "stroke": "#6D28D9"}
WORKFLOW = {"fill": "#FFF1F2", "stroke": "#BE123C"}
LINE = "#374151"
TEXT = "#111827"
MUTED = "#4B5563"
FK_NOTE = "#6B7280"


def esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def entity(x: int, y: int, w: int, title: str, fields: list[str], style: dict) -> str:
    line_h = 20
    header_h = 34
    body_h = len(fields) * line_h + 14
    h = header_h + body_h
    lines = []
    lines.append(
        f'<g class="entity">'
        f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="10" ry="10" '
        f'fill="{style["fill"]}" stroke="{style["stroke"]}" stroke-width="2.2"/>'
        f'<rect x="{x}" y="{y}" width="{w}" height="{header_h}" rx="10" ry="10" '
        f'fill="{style["stroke"]}" opacity="0.12"/>'
        f'<line x1="{x}" y1="{y + header_h}" x2="{x + w}" y2="{y + header_h}" '
        f'stroke="{style["stroke"]}" stroke-width="1.5"/>'
        f'<text x="{x + 14}" y="{y + 23}" font-family="{FONT}" font-size="16" '
        f'font-weight="700" fill="{TEXT}">{esc(title)}</text>'
    )
    fy = y + header_h + 18
    for f in fields:
        lines.append(
            f'<text x="{x + 14}" y="{fy}" font-family="{FONT}" font-size="14" '
            f'fill="{TEXT}">{esc(f)}</text>'
        )
        fy += line_h
    lines.append("</g>")
    return "\n".join(lines), h


def rel_line(x1: int, y1: int, x2: int, y2: int, label: str, dashed: bool = False) -> str:
    mx, my = (x1 + x2) // 2, (y1 + y2) // 2
    dash = ' stroke-dasharray="7,5"' if dashed else ""
    return (
        f'<path d="M{x1},{y1} L{mx},{y1} L{mx},{y2} L{x2},{y2}" fill="none" '
        f'stroke="{LINE}" stroke-width="1.8"{dash} marker-end="url(#arrow)"/>'
        f'<text x="{mx + 6}" y="{my - 6}" font-family="{FONT}" font-size="12" '
        f'fill="{MUTED}">{esc(label)}</text>'
    )


def rel_direct(x1: int, y1: int, x2: int, y2: int, label: str, dashed: bool = False) -> str:
    dash = ' stroke-dasharray="7,5"' if dashed else ""
    mx, my = (x1 + x2) // 2, (y1 + y2) // 2
    return (
        f'<line x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}" stroke="{LINE}" '
        f'stroke-width="1.8"{dash} marker-end="url(#arrow)"/>'
        f'<text x="{mx + 6}" y="{my - 4}" font-family="{FONT}" font-size="12" '
        f'fill="{MUTED}">{esc(label)}</text>'
    )


def svg_wrap(title: str, subtitle: str, body: str, w: int, h: int) -> str:
    return f'''<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">
  <defs>
    <marker id="arrow" markerWidth="10" markerHeight="8" refX="9" refY="4" orient="auto">
      <polygon points="0 0, 10 4, 0 8" fill="{LINE}"/>
    </marker>
  </defs>
  <rect width="100%" height="100%" fill="#ffffff"/>
  <text x="40" y="42" font-family="{FONT}" font-size="22" font-weight="700" fill="{TEXT}">{esc(title)}</text>
  <text x="40" y="66" font-family="{FONT}" font-size="13" fill="{MUTED}">{esc(subtitle)}</text>
  {body}
  <text x="40" y="{h - 24}" font-family="{FONT}" font-size="11" fill="{FK_NOTE}">
    Solid lines: Flyway FK enforced · Dashed lines: logical FK (Java Long IDs)
  </text>
</svg>
'''


def planung_diagram() -> str:
    w, ew = 1280, 248
    parts = []
    # users top center
    u, uh = entity(516, 100, ew, "users", [
        "id  PK",
        "username",
        "contract_type",
        "admin",
    ], CORE)
    parts.append(u)
    # shift_plans left
    sp, sph = entity(80, 300, ew, "shift_plans", [
        "id  PK",
        "user_id  FK",
        "period_start / period_end",
        "status",
        "submitted_at / reviewed_at",
        "reviewed_by_id",
        "deleted_at",
    ], SHIFT)
    parts.append(sp)
    # shift_entries below shift_plans
    se, seh = entity(80, 560, ew, "shift_entries", [
        "id  PK",
        "plan_id  FK",
        "entry_date",
        "start_time / end_time",
        "shift_type",
        "minutes",
        "review_status",
        "reviewed_by_id  FK",
    ], SHIFT)
    parts.append(se)
    # vacation_requests right
    vr, vrh = entity(952, 300, ew, "vacation_requests", [
        "id  PK",
        "employee_id  FK",
        "year",
        "status",
        "submitted_at / reviewed_at",
        "reviewed_by_id  FK",
        "rejection_reason",
        "deleted_at",
    ], VACATION)
    parts.append(vr)
    # vacation_request_days below
    vd, vdh = entity(952, 600, ew, "vacation_request_days", [
        "id  PK",
        "vacation_request_id  FK",
        "request_date",
        "review_status",
        "reviewed_by_id  FK",
        "reviewed_at",
    ], VACATION)
    parts.append(vd)
    # relationships
    rels = [
        rel_direct(640, 100 + uh, 204, 300, "1 : n  owns"),
        rel_direct(204, 300 + sph, 204, 560, "1 : n  contains"),
        rel_direct(640, 100 + uh, 1076, 300, "1 : n  submits"),
        rel_direct(1076, 300 + vrh, 1076, 600, "1 : n  contains"),
        rel_line(640, 100 + uh, 204, 560 + seh // 2, "reviews", dashed=True),
        rel_line(640, 100 + uh, 1076, 300 + vrh // 2, "reviews", dashed=True),
        rel_line(640, 100 + uh, 1076, 600, "reviews", dashed=True),
    ]
    body = "\n".join(parts + rels)
    return svg_wrap(
        "Datenmodell – Workforce Planning",
        "users · shift_plans · shift_entries · vacation_requests · vacation_request_days",
        body,
        w,
        900,
    )


def kommunikation_diagram() -> str:
    w, ew = 1400, 240
    parts = []
    u, uh = entity(580, 100, ew, "users", [
        "id  PK",
        "username",
        "admin",
    ], CORE)
    parts.append(u)
    sup, _ = entity(40, 320, ew, "support_requests", [
        "id  PK",
        "user_id  FK nullable",
        "category",
        "message",
        "status",
        "created_at",
    ], WORKFLOW)
    parts.append(sup)
    chat, ch = entity(300, 320, ew, "app_chat_messages", [
        "id  PK",
        "sender_id  FK",
        "receiver_id  FK",
        "content",
        "sent_at",
        "read_at",
    ], COMM)
    parts.append(chat)
    notif, _ = entity(560, 320, ew, "app_notifications", [
        "id  PK",
        "user_id  FK",
        "notification_type",
        "message",
        "link",
        "created_at",
        "read_at",
    ], COMM)
    parts.append(notif)
    ot, oth = entity(820, 320, ew, "app_overtime_requests", [
        "id  PK",
        "created_by  FK",
        "title",
        "status",
        "work_date",
        "created_at",
    ], WORKFLOW)
    parts.append(ot)
    rec, _ = entity(1100, 520, ew, "app_overtime_request_recipients", [
        "id  PK",
        "request_id  FK",
        "employee_id  FK",
        "response_status",
        "responded_at",
    ], WORKFLOW)
    parts.append(rec)
    rels = [
        rel_direct(700, 100 + uh, 160, 320, "1 : n  submits"),
        rel_direct(700, 100 + uh, 420, 320, "1 : n  sends", dashed=True),
        rel_direct(700, 100 + uh, 420, 320 + ch // 2, "1 : n  receives", dashed=True),
        rel_direct(700, 100 + uh, 680, 320, "1 : n  gets", dashed=True),
        rel_direct(700, 100 + uh, 940, 320, "1 : n  creates", dashed=True),
        rel_direct(940, 320 + oth, 1220, 520, "1 : n  targets"),
        rel_direct(700, 100 + uh, 1220, 580, "1 : n  assigned", dashed=True),
    ]
    body = "\n".join(parts + rels)
    return svg_wrap(
        "Datenmodell – Communication & Workflow",
        "users · support · chat · notifications · overtime",
        body,
        w,
        920,
    )


def write_files():
    files = {
        "datenmodell-planung.svg": planung_diagram(),
        "datenmodell-kommunikation-workflow.svg": kommunikation_diagram(),
    }
    for name, content in files.items():
        (OUT / name).write_text(content, encoding="utf-8")
        print(f"Wrote {name}")

    # PNG export (best effort; Pillow works on Windows without Cairo)
    if not export_png_pillow():
        if not export_png_svglib(files):
            if not export_png_cairosvg(files):
                try_png_via_inkscape(files)


def export_png_cairosvg(files: dict[str, str]) -> bool:
    try:
        import cairosvg  # type: ignore
    except (ImportError, OSError):
        return False
    try:
        _ = cairosvg  # noqa: ensure native cairo loads
    except OSError:
        return False
    for svg_name in files:
        png_name = svg_name.replace(".svg", ".png")
        cairosvg.svg2png(
            url=str(OUT / svg_name),
            write_to=str(OUT / png_name),
            output_width=2800,
            output_height=2000,
        )
        print(f"Wrote {png_name}")
    return True


def export_png_pillow() -> bool:
    """Rasterize SVG via Edge/Chrome headless if available."""
    html = OUT / "export-png.html"
    if not html.exists():
        return False
    browsers = [
        ["msedge", "--headless", "--disable-gpu", "--window-size=2800,2000"],
        ["chrome", "--headless", "--disable-gpu", "--window-size=2800,2000"],
        ["chromium", "--headless", "--disable-gpu", "--window-size=2800,2000"],
    ]
    url = html.as_uri()
    for base in browsers:
        exe = base[0]
        try:
            for i, name in enumerate(["datenmodell-planung", "datenmodell-kommunikation-workflow"]):
                out = OUT / f"{name}.png"
                subprocess.run(
                    base + [f"--screenshot={out}", url + f"#page={i+1}"],
                    check=True,
                    capture_output=True,
                    timeout=60,
                )
            print("Wrote PNG files (headless browser)")
            return True
        except (FileNotFoundError, subprocess.CalledProcessError, subprocess.TimeoutExpired):
            continue
    return False


def export_png_svglib(files: dict[str, str]) -> bool:
    try:
        from svglib.svglib import svg2rlg  # type: ignore
        from reportlab.graphics import renderPM  # type: ignore
    except (ImportError, OSError):
        return False
    for svg_name in files:
        png_name = svg_name.replace(".svg", ".png")
        drawing = svg2rlg(str(OUT / svg_name))
        renderPM.drawToFile(drawing, str(OUT / png_name), fmt="PNG", dpi=200)
        print(f"Wrote {png_name}")
    return True


def try_png_via_inkscape(files: dict[str, str]) -> None:
    for svg_name in files:
        png_name = svg_name.replace(".svg", ".png")
        try:
            subprocess.run(
                ["inkscape", str(OUT / svg_name), "--export-type=png", f"--export-filename={OUT / png_name}", "-d", "300"],
                check=True,
                capture_output=True,
            )
            print(f"Wrote {png_name} (inkscape)")
        except (FileNotFoundError, subprocess.CalledProcessError):
            break


if __name__ == "__main__":
    write_files()
