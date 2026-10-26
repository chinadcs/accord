"""Indexa runs do PC sem confundir diagnóstico parcial, reavaliação e inferência nova."""
import argparse
import json
from pathlib import Path


def collect(root):
    rows = []
    for folder in sorted(root.glob("run-*")):
        for path in sorted(folder.glob("*.jsonl")):
            records = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
            start = next((r for r in records if r["type"] == "run_start"), {})
            grade = next((r["summary"] for r in records if r["type"] == "quality_summary"), None)
            if not grade:
                continue
            suite = start.get("suite", "dev")
            manifest_path = folder / "manifest.json"
            manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig")) if manifest_path.exists() else {}
            private = start.get("cases", {})
            expected = (private.get("private", 0) if suite == "private"
                        else 100 if suite == "expanded" and start.get("expandedVersion") == 1
                        else 27 if suite == "brands" and start.get("brandVersion") == 1
                        else {"holdout": 30, "dev": 37}.get(suite, 0))
            inferences = [r for r in records if r["type"] == "infer"]
            complete = (records[-1].get("reason") == "done" and grade["total"] == expected
                        and expected > 0 and len(inferences) == expected and len({r.get("case") for r in inferences}) == expected
                        and (suite != "private" or (private.get("fictitious") == 0 and private.get("selected") == expected
                                                   and all(r.get("sensitive") is True for r in inferences))))
            memory = next((r for r in records if r["type"] == "memory_summary"), {})
            rows.append({
                "run": folder.name, "model": start["model"], "suite": suite,
                "privateDatasetSha256": start.get("privateDatasetSha256", manifest.get("privateFileSha256")),
                "batteryVersion": start.get("batteryVersion"), "promptVersion": start.get("promptVersion"),
                "expandedVersion": start.get("expandedVersion"),
                "brandVersion": start.get("brandVersion"),
                "criteriaVersion": start.get("criteriaVersion", grade.get("criteriaVersion", 1)),
                "validatorVersion": start.get("validatorVersion", 1),
                "promptKind": start.get("promptKind", "extraction"),
                "stage": start.get("stage"), "complete": complete,
                "approved": complete and grade["allMet"], "summary": grade,
                "averageMiB": memory.get("averageBytes", 0) / 1048576 if memory.get("samples") else None,
                "file": path.relative_to(root).as_posix(),
            })
    return rows


def render(rows):
    out = ["# Runs de qualidade no PC", "",
           "Aprovação exige conjunto completo e todos os critérios na mesma run. "
           "Desenvolvimento, conjunto adicional, privadas e reavaliação são identificados separadamente. "
           "A condição de conjunto não visto depende do protocolo da run; reutilização não é avaliação cega nova. "
           "Métrica v1 inclui categoria no contador zero; v2 separa categoria (>=80%) de erro financeiro/alvo (zero). "
           "Tempo/RAM do PC não comprovam desempenho nos celulares.", "",
           "| Run | Modelo | Conjunto | Bateria / prompt | Casos | Intenção | Valor/papel | Categoria | Data | Perguntas | Erradas claras | RAM média | Situação |",
           "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |"]

    def rate(r):
        return f'{100 * r["ok"] / r["total"]:.1f}%' if r["total"] else "n/a"

    for row in rows:
        s = row["summary"]
        kind = f'reavaliação ({row["suite"]})' if row["stage"] == "quality-pc-regrade" else row["suite"]
        if row["suite"] == "holdout" and row["stage"] != "quality-pc-regrade":
            kind = "adicional"
        if row["suite"] == "expanded":
            kind += f' v{row.get("expandedVersion", "?")}'
        if row["suite"] == "private" and row.get("privateDatasetSha256"):
            kind += f' ({row["privateDatasetSha256"][:8]})'
        status = "APROVADO" if row["approved"] else "não aprovado" if row["complete"] else "parcial"
        memory = f'{row["averageMiB"]:.1f} MiB' if row["averageMiB"] is not None else "sem nova medição"
        cells = [f'[{row["run"]}]({row["run"]}/)', row["model"], kind,
                 f'{row["batteryVersion"]} / métrica{row["criteriaVersion"]} / validador{row.get("validatorVersion", 1)} / {row.get("promptKind", "extraction")}-v{row["promptVersion"] or "herdado"}', str(s["total"]),
                 rate(s["intent"]), rate(s["amountRole"]), rate(s["category"]), rate(s["date"]),
                 rate(s["unnecessaryQuestions"]), str(s["acceptedWrongClear"]), memory, status]
        out.append("| " + " | ".join(cells) + " |")
    return "\n".join(out) + "\n"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("root", nargs="?", type=Path, default=Path("poc/results/pc"))
    args = parser.parse_args()
    rows = collect(args.root)
    (args.root / "index.md").write_text(render(rows), encoding="utf-8")
    (args.root / "index.json").write_text(json.dumps(rows, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f'{len(rows)} resultados indexados em {args.root / "index.md"}')


if __name__ == "__main__":
    main()
