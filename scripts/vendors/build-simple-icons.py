#!/usr/bin/env python3
"""Gera o ativo local do Simple Icons (ADR 0047, ADR 0021) a partir do tarball npm da versão fixada.

Uso:
    python scripts/vendors/build-simple-icons.py caminho/simple-icons-16.35.0.tgz

Baixe o tarball de https://registry.npmjs.org/simple-icons/-/simple-icons-16.35.0.tgz (a origem não é confiada: o
script confere o SHA-256 fixado, lê só os membros esperados e não extrai nada em disco). Saída, determinística:
  app/src/main/assets/vendors/simple-icons.tsv  uma linha por marca, ordenada pelo slug
  app/src/main/assets/vendors/catalog.json      manifesto: origem, versão, hashes e contagem
Colunas do TSV: slug, título, cor (hex), apelidos separados por "|", licença, diretrizes, origem, caminho do desenho (24 x 24).
"""
import hashlib
import json
import re
import sys
import tarfile
from pathlib import Path

VERSION = "16.35.0"
TARBALL_SHA256 = "20ebead4f6bc93464812a5e260d19fc4d84772e5eeff69a6fbea263ca2441c5f"
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app" / "src" / "main" / "assets" / "vendors"
PATH_RE = re.compile(r'^<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><title>.*?</title><path d="([^"]+)"/></svg>\s*$', re.S)


def clean(text: str) -> str:
    if any(c in text for c in "\t\n\r|"):
        raise SystemExit(f"Texto com separador reservado: {text!r}")
    return text


def main(tarball: Path) -> None:
    raw = tarball.read_bytes()
    digest = hashlib.sha256(raw).hexdigest()
    if digest != TARBALL_SHA256:
        raise SystemExit(f"SHA-256 inesperado: {digest}")
    with tarfile.open(fileobj=__import__("io").BytesIO(raw), mode="r:gz") as tar:
        def member(name: str) -> bytes:
            f = tar.extractfile("package/" + name)
            if f is None:
                raise SystemExit(f"Membro ausente: {name}")
            return f.read()

        package = json.loads(member("package.json"))
        if package["version"] != VERSION:
            raise SystemExit(f"Versão inesperada: {package['version']}")
        data = json.loads(member("data/simple-icons.json"))
        rows = []
        seen = set()
        for entry in data:
            slug = entry["slug"]
            if slug in seen or not re.fullmatch(r"[a-z0-9_]+", slug):
                raise SystemExit(f"Slug inválido ou repetido: {slug!r}")
            seen.add(slug)
            hex_color = entry["hex"].upper()
            if not re.fullmatch(r"[0-9A-F]{6}", hex_color):
                raise SystemExit(f"Cor inválida em {slug}: {hex_color}")
            svg = member(f"icons/{slug}.svg").decode("utf-8")
            match = PATH_RE.match(svg)
            if not match:
                raise SystemExit(f"SVG fora do formato esperado: {slug}")
            aliases = entry.get("aliases", {})
            names = list(aliases.get("aka", [])) + list(aliases.get("loc", {}).values())
            rows.append((
                slug, clean(entry["title"]), hex_color, "|".join(clean(n) for n in dict.fromkeys(names)),
                clean(entry.get("license", {}).get("type", "")), clean(entry.get("guidelines", "")), clean(entry["source"]),
                match.group(1),
            ))
    rows.sort(key=lambda r: r[0])
    OUT.mkdir(parents=True, exist_ok=True)
    tsv = "".join("\t".join(r) + "\n" for r in rows).encode("utf-8")
    (OUT / "simple-icons.tsv").write_bytes(tsv)
    manifest = {
        "library": "Simple Icons",
        "version": VERSION,
        "tarball": f"https://registry.npmjs.org/simple-icons/-/simple-icons-{VERSION}.tgz",
        "tarballSha256": TARBALL_SHA256,
        "asset": "simple-icons.tsv",
        "assetSha256": hashlib.sha256(tsv).hexdigest(),
        "count": len(rows),
        "columns": ["slug", "title", "hex", "aliases", "license", "guidelines", "source", "path"],
        "generator": "scripts/vendors/build-simple-icons.py",
    }
    (OUT / "catalog.json").write_text(json.dumps(manifest, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print(f"{len(rows)} marcas, {len(tsv)} bytes, sha256 {manifest['assetSha256']}")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    main(Path(sys.argv[1]))
