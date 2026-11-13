"""Export the design-only straight-edge SVG into the runtime transparent PNG.

Requires Pillow. SVG is never packaged or decoded by the app.
"""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "design/anivio-branding/anivio-av-wordmark.svg"
OUTPUT = ROOT / "composeApp/src/commonMain/composeResources/drawable/app_logo_av_wordmark.png"
SCALE = 12  # Supersample before exporting at 3x the design dimensions.


def polygons(data):
    tokens = re.findall(r"[MLHVZ]|-?\d+(?:\.\d+)?", data)
    points = []
    x = y = 0.0
    index = 0
    while index < len(tokens):
        command = tokens[index]
        index += 1
        if command in ("M", "L"):
            x, y = map(float, tokens[index:index + 2])
            index += 2
        elif command == "H":
            x = float(tokens[index])
            index += 1
        elif command == "V":
            y = float(tokens[index])
            index += 1
        elif command == "Z":
            yield points
            points = []
            continue
        else:
            raise ValueError(f"Unsupported path command: {command}")
        points.append((x, y))
    if points:
        raise ValueError("Expected closed polygon")


svg = ET.parse(SOURCE).getroot()
width, height = map(int, svg.attrib["viewBox"].split()[2:])
mask = Image.new("1", (width * SCALE, height * SCALE))
for element in svg.iter("{http://www.w3.org/2000/svg}path"):
    transform = element.attrib.get("transform", "translate(0 0)")
    match = re.fullmatch(r"translate\((\d+) (\d+)\)", transform)
    if not match:
        raise ValueError(f"Unsupported transform: {transform}")
    dx, dy = map(int, match.groups())
    path_mask = Image.new("1", mask.size)
    for polygon in polygons(element.attrib["d"]):
        contour = Image.new("1", mask.size)
        ImageDraw.Draw(contour).polygon(
            [((x + dx) * SCALE, (y + dy) * SCALE) for x, y in polygon], fill=1
        )
        path_mask = ImageChops.logical_xor(path_mask, contour)
    mask = ImageChops.logical_or(mask, path_mask)
alpha = mask.convert("L").resize((width * 3, height * 3), Image.Resampling.LANCZOS)
image = Image.new("RGBA", alpha.size, (255, 255, 255, 0))
image.putalpha(alpha)
image.save(OUTPUT, optimize=True)
print(f"Exported {OUTPUT}: {image.width}x{image.height} RGBA")
