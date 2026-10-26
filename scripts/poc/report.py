"""Relatório da Etapa 0 (S3 §2) a partir dos resultados da bancada (poc/results/<aparelho>/*.jsonl).

Só resume o que foi medido: não aprova modelo, motor, quantização nem hospedagem. A decisão é do usuário
(critério de saída 8). Onde não há dado, o relatório diz "sem dados" em vez de supor. As frases reais do casal
nunca chegam aqui: a bancada não as grava, só identificadores, tempos e rótulos categóricos.

Uso: python scripts/poc/report.py [--results poc/results] [--catalog poc/assets/models.json] [-o relatorio.md]
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

LATENCY_GOAL_MS = 5000.0
DOWNLOAD_CAP = 2_000_000_000
THERMAL = {0: "nenhum", 1: "leve", 2: "moderado", 3: "severo", 4: "crítico", 5: "emergência", 6: "desligamento"}


@dataclass
class Run:
    path: Path
    label: str
    records: list[dict] = field(default_factory=list)

    @property
    def start(self) -> dict:
        return next((r for r in self.records if r.get("type") == "run_start"), {})

    @property
    def stage(self) -> str:
        return self.start.get("stage", "")

    @property
    def model(self) -> str:
        return self.start.get("model", "")

    def of(self, kind: str) -> list[dict]:
        return [r for r in self.records if r.get("type") == kind]


def load_runs(root: Path) -> list[Run]:
    runs: list[Run] = []
    for path in sorted(root.glob("*/*.jsonl")):
        run = Run(path, path.parent.name)
        for line in path.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                run.records.append(json.loads(line))
            except json.JSONDecodeError:
                run.records.append({"type": "corrupt_line"})
        runs.append(run)
    return runs


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    if len(ordered) == 1:
        return ordered[0]
    rank = (len(ordered) - 1) * p / 100.0
    low = int(rank)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (rank - low)


def fmt(value, unit: str = "", digits: int = 0) -> str:
    if value is None:
        return "—"
    if isinstance(value, float):
        return f"{value:.{digits}f}{unit}"
    return f"{value}{unit}"


def ms(value) -> str:
    return fmt(value, " ms", 0)


def mark(met) -> str:
    return {True: "atendido", False: "NÃO atendido", None: "sem dados"}[met]


def table(header: list[str], rows: list[list[str]]) -> list[str]:
    if not rows:
        return ["_sem dados_", ""]
    out = ["| " + " | ".join(header) + " |", "| " + " | ".join("---" for _ in header) + " |"]
    out += ["| " + " | ".join(str(c) for c in row) + " |" for row in rows]
    return out + [""]


def thermal_name(status) -> str:
    return THERMAL.get(int(status), str(status)) if status is not None else "—"


# ---------- seções ----------


def devices_section(runs: list[Run]) -> list[str]:
    latest: dict[str, dict] = {}
    for run in runs:
        for record in run.of("info"):
            latest[run.label] = record
    rows = []
    for label, record in sorted(latest.items()):
        d = record.get("device", {})
        rows.append([
            label,
            f"{d.get('manufacturer', '')} {d.get('model', '')} ({d.get('device', '')})",
            f"{d.get('socManufacturer', '')} {d.get('socModel', '')} · {d.get('cores', '?')} núcleos",
            f"{d.get('androidRelease', '?')} (SDK {d.get('sdk', '?')})",
            f"{d.get('totalMemMb', '?')} MB / {d.get('availMemMb', '?')} MB",
            f"{d.get('internalFreeMb', '?')} MB",
        ])
    out = ["## Aparelhos (história 1)", ""]
    out += table(["Etiqueta", "Aparelho", "Processador", "Android", "RAM total / livre na ficha", "Espaço livre"], rows)
    return out


def candidates_section(catalog: list[dict]) -> list[str]:
    rows = [[m["id"], m["label"], m["quant"], f"{m['bytes']:,}".replace(",", "."), "sim" if m["bytes"] <= DOWNLOAD_CAP else "NÃO (acima do teto)", m["license"]] for m in catalog]
    out = ["## Candidatos medidos e teto de download (critério 2)", ""]
    out += table(["Id", "Modelo", "Quant.", "Bytes", "≤ 2.000.000.000", "Licença (não conferida na fonte)"], rows)
    return out


def battery_runs(runs: list[Run]) -> list[Run]:
    return [r for r in runs if r.stage == "battery"]


def quality_section(runs: list[Run]) -> list[str]:
    out = ["## Qualidade (critérios mínimos de 24/10/2026)", ""]
    batteries = battery_runs(runs)
    for run in batteries:
        summaries = run.of("quality_summary")
        out.append(f"### {run.label} · {run.model} · `{run.start.get('tag', '')}` (threads {fmt(run.start.get('threads'))}, prompt v{fmt(run.start.get('promptVersion'))})")
        out.append("")
        if not summaries:
            out += ["Sem resumo de qualidade: a rodada não terminou.", ""]
        else:
            s = summaries[-1]["summary"]
            out += table(
                ["Critério", "Exigido", "Medido", "Situação"],
                [[c["name"], c["required"], c["actual"], mark(c["met"])] for c in s["criteria"]],
            )
            out.append(f"Casos: {s['total']} ({s['clearTotal']} claros). Recusas por formato inválido: {s['refusedFormat']}. "
                       f"Aceitas porém erradas fora das frases claras: {s['acceptedWrongOther']}. "
                       f"Correções certas: {s['correction']['ok']}/{s['correction']['total']}.")
            out.append("")
        out += failures(run)
    if not batteries:
        out += ["_sem dados_", ""]
    return out


def failures(run: Run) -> list[str]:
    first = [r for r in run.of("infer") if r.get("rep") == 1 and r.get("outcome") != "Match"]
    if not first:
        return ["Todos os casos bateram com o gabarito.", ""]
    rows = []
    for r in first[:60]:
        if r.get("sensitive"):
            rows.append([r["case"], r["group"], r["decision"], r["outcome"], "(frase real: texto não registrado)"])
        else:
            rows.append([r["case"], r["group"], r["decision"], r["outcome"], f"`{r.get('text', '')}` → `{r.get('output', '')}`"])
    out = [f"Casos fora do gabarito na 1ª repetição ({len(first)}):", ""]
    return out + table(["Caso", "Grupo", "Decisão", "Resultado", "Frase → saída do modelo"], rows)


def latency_section(runs: list[Run]) -> list[str]:
    out = ["## Latência com o modelo carregado (meta: até 5 s em frase curta, ≤ 60 caracteres)", "",
           "Mede interpretar + validar, depois da carga e do aquecimento. Banco local e desenho da tela ficam de fora "
           "e entram na integração (accord-q12).", ""]
    rows = []
    excess: list[list[str]] = []
    for run in battery_runs(runs):
        infer = [r for r in run.of("infer") if r.get("modelCalled") and r.get("short")]
        if not infer:
            continue
        times = [r["tPipelineMs"] for r in infer]
        over = [r for r in infer if r["tPipelineMs"] > LATENCY_GOAL_MS]
        prefill = [r["stats"]["tPrefillMs"] for r in infer if "stats" in r]
        gen = [r["stats"]["tGenMs"] for r in infer if "stats" in r]
        reused = [r["stats"]["prefixReused"] for r in infer if "stats" in r]
        modes = sorted({r["stats"].get("prefixMode", "?") for r in infer if "stats" in r})
        warm = run.of("warmup")
        rows.append([
            run.label, run.model, fmt(run.start.get("threads")), len(times), ms(statistics.fmean(times)), ms(percentile(times, 50)),
            ms(percentile(times, 90)), ms(percentile(times, 95)), ms(max(times)), f"{len(over)} ({100 * len(over) / len(times):.1f}%)",
            ms(statistics.median(prefill)) if prefill else "—", ms(statistics.median(gen)) if gen else "—",
            (f"{100 * sum(reused) / len(reused):.0f}% ({', '.join(modes)})") if reused else "—",
            ms(warm[0].get("firstAfterLoadMs")) if warm else "—",
        ])
        for r in over:
            excess.append([run.label, run.model, r["case"], r["chars"], ms(r["tPipelineMs"]), thermal_name(r.get("power", {}).get("thermalStatus"))])
    out += table(["Aparelho", "Modelo", "Threads", "n", "Média", "p50", "p90", "p95", "Máx", "> 5 s", "Prefill (mediana)", "Geração (mediana)", "Prefixo reaproveitado", "1ª após carga"], rows)
    out += ["### Todos os casos acima de 5 s (frase curta)", ""]
    out += table(["Aparelho", "Modelo", "Caso", "Caracteres", "Tempo", "Estado térmico"], excess)
    return out


def cold_section(runs: list[Run]) -> list[str]:
    out = ["## Carga a frio, memória e retorno", "",
           "Carga a frio = pedido de carga até apto a inferir, num processo recém-iniciado (fora da meta de 5 s). "
           "Repetições dentro do mesmo processo têm cache de página aquecido: só `loadIndexInProcess` = 1 é a primeira.", ""]
    rows = []
    for run in runs:
        for load in run.of("load"):
            peak = load.get("peak", {})
            unloads = [u for u in run.of("unload")]
            rows.append([
                run.label, load.get("model", run.model), run.stage, load.get("loadIndexInProcess"), ms(load.get("wallMs")),
                f"{load['result'].get('modelBytes', 0) / 1e6:.0f} MB" if load.get("result", {}).get("ok") else "FALHOU",
                {True: "remoção parcial", False: "snapshot do prefixo"}.get(load.get("result", {}).get("trimOk"), "—"),
                f"{load.get('memBefore', {}).get('availMemMb', '—')} MB", f"{peak.get('minAvailMemMb', '—')} MB",
                f"{(peak.get('peakRssKb') or 0) / 1024:.0f} MB",
                f"{(unloads[-1].get('memAfter', {}).get('rssKb') or 0) / 1024:.0f} MB" if unloads else "—",
            ])
    out += table(["Aparelho", "Modelo", "Estágio", "Nº no processo", "Carga", "Pesos", "Cache de contexto", "Livre antes", "Mínimo livre", "Pico RSS", "RSS após liberar"], rows)
    cold = []
    for run in runs:
        for c in run.of("cold_cycle"):
            cold.append([run.label, c["model"], c["index"], ms(c["loadMs"]), ms(c["firstInferenceMs"]), ms(c["loadPlusFirstMs"]), c["loadIndexInProcess"]])
    out += ["### Carga + primeira resposta", ""]
    out += table(["Aparelho", "Modelo", "Repetição", "Carga", "1ª inferência (prefixo inteiro)", "Total", "Nº no processo"], cold)
    return out


def session_section(runs: list[Run]) -> list[str]:
    out = ["## Bateria e calor (sessão fixa de 30 min)", ""]
    found = False
    for run in runs:
        if run.stage != "session":
            continue
        found = True
        ticks = run.of("tick")
        start = (run.of("session_start") or [{}])[0].get("power", {})
        end = (run.of("session_end") or [{}])[0].get("powerEnd", {})
        valid = (run.of("session_end") or [{}])[0].get("valid", {})
        ended = (run.of("run_end") or [{}])[0].get("reason", "sem run_end (interrompida?)")
        times = [t["pipelineMs"] for t in ticks]
        pct0, pct1 = start.get("batteryPct"), end.get("batteryPct")
        charge0, charge1 = start.get("chargeCounterUah"), end.get("chargeCounterUah")
        out.append(f"### {run.label} · {run.model} · `{run.start.get('tag', '')}` · fim: {ended}")
        out.append("")
        flags = {k: v for k, v in valid.items() if k != "ticks"}
        if not valid:
            verdict = "sem session_end: sessão não terminou"
        elif all(flags.values()):
            verdict = "VÁLIDA (cheia, sem carregador, avião, tela ligada)"
        else:
            verdict = "INVÁLIDA para o protocolo: " + ", ".join(k for k, v in flags.items() if not v)
        out.append(f"Validade: **{verdict}**.")
        out.append("")
        rows = [
            ["Bateria", fmt(pct0, "%", 0), fmt(pct1, "%", 0), fmt(None if pct0 is None or pct1 is None else pct0 - pct1, " p.p.", 0)],
            ["Contador de carga", fmt(charge0 and charge0 / 1000, " mAh", 0), fmt(charge1 and charge1 / 1000, " mAh", 0),
             fmt(None if not charge0 or not charge1 else (charge0 - charge1) / 1000, " mAh", 0)],
            ["Temperatura da bateria", fmt(start.get("batteryTempC"), " °C", 1), fmt(end.get("batteryTempC"), " °C", 1),
             fmt(max((t.get("power", {}).get("batteryTempC") or 0 for t in ticks), default=None), " °C (máx)", 1)],
            ["Estado térmico do sistema", thermal_name(start.get("thermalStatus")), thermal_name(end.get("thermalStatus")),
             thermal_name(max((t.get("power", {}).get("thermalStatus") or 0 for t in ticks), default=None)) + " (máx)"],
        ]
        out += table(["Medida", "Início", "Fim", "Variação"], rows)
        out.append(f"Inferências: {len(ticks)}; acima de 5 s: {sum(1 for t in ticks if t.get('over5s'))}; "
                   f"mediana {ms(percentile(times, 50))}; máx {ms(max(times) if times else None)}.")
        out.append("")
        windows: dict[int, list[dict]] = defaultdict(list)
        for t in ticks:
            windows[int(t["k"] * 30 // 300)].append(t)  # janelas de ~5 min (intervalo padrão 30 s)
        wrows = []
        for index in sorted(windows):
            w = windows[index]
            lat = [t["pipelineMs"] for t in w]
            last = w[-1].get("power", {})
            wrows.append([f"{index * 5}–{index * 5 + 5} min", len(w), ms(percentile(lat, 50)), ms(max(lat)), sum(1 for t in w if t.get("over5s")),
                          fmt(last.get("batteryPct"), "%", 0), fmt(last.get("batteryTempC"), " °C", 1), thermal_name(last.get("thermalStatus"))])
        out += table(["Janela", "n", "Latência p50", "Máx", "> 5 s", "Bateria", "Temp. bateria", "Térmico"], wrows)
    if not found:
        out += ["_sem dados_", ""]
    return out


def stability_section(runs: list[Run]) -> list[str]:
    out = ["## Estabilidade", ""]
    rows = []
    for run in runs:
        ended = run.of("run_end")
        reason = ended[-1].get("reason") if ended else "sem run_end"
        if run.stage in ("battery", "cold", "session") and reason != "done":
            rows.append([run.label, run.path.name, run.stage, reason])
    out += ["Rodadas que não terminaram normalmente:", ""]
    out += table(["Aparelho", "Arquivo", "Estágio", "Motivo"], rows) if rows else ["Nenhuma.", ""]
    exits = []
    for run in runs:
        for record in run.of("info") + run.of("run_start"):
            for e in record.get("exits", []):
                if e.get("reason") in ("LOW_MEMORY", "CRASH", "CRASH_NATIVE", "ANR", "SIGNALED"):
                    exits.append((run.label, e["timestamp"], e["reason"], e.get("rssKb")))
    out += ["Encerramentos pelo sistema (ApplicationExitInfo), sem repetição:", ""]
    unique = sorted(set(exits))
    out += table(["Aparelho", "Timestamp", "Motivo", "RSS (kB)"], [list(e) for e in unique]) if unique else ["Nenhum registrado.", ""]
    return out


def offline_section(runs: list[Run]) -> list[str]:
    out = ["## Modo avião (critério 6)", "",
           "A bancada só interpreta e valida (sem sync, sem banco): demonstra o caminho do modelo sem rede. "
           "O registro completo em modo avião depende da integração do motor ao Chat (accord-q12).", ""]
    rows = []
    for run in runs:
        env = run.start.get("environment", {})
        if run.stage in ("battery", "session") and env:
            rows.append([run.label, run.path.name, run.stage, "sim" if env.get("airplane") else "não", "não" if not env.get("activeNetwork") else "SIM"])
    return out + table(["Aparelho", "Arquivo", "Estágio", "Modo avião", "Rede ativa"], rows)


def exit_criteria(runs: list[Run], catalog: list[dict]) -> list[str]:
    labels = sorted({r.label for r in runs})
    out = ["## Critérios de saída da Etapa 0 (situação dos dados; não é aprovação)", ""]
    info_labels = sorted({r.label for r in runs if r.of("info")})
    rows = [["1", "Ficha dos dois aparelhos (variante, Android, espaço, RAM)", f"fichas: {', '.join(info_labels) or 'nenhuma'}; esperado: S20 FE e S23 FE"]]
    over = [m["id"] for m in catalog if m["bytes"] > DOWNLOAD_CAP]
    rows.append(["2", "Download ≤ 2.000.000.000 bytes", "candidatos acima do teto: " + (", ".join(over) or "nenhum")])
    by_model: dict[str, dict[str, bool]] = defaultdict(dict)
    for run in battery_runs(runs):
        for q in run.of("quality_summary"):
            by_model[run.model][run.label] = by_model[run.model].get(run.label, True) and q["summary"]["allMet"]
    rows.append(["5", "Qualidade mínima atendida nos dois aparelhos",
                 "; ".join(f"{m}: " + ", ".join(f"{label}={'sim' if ok else 'NÃO'}" for label, ok in sorted(d.items())) for m, d in sorted(by_model.items())) or "sem dados"])
    sessions = [r for r in runs if r.stage == "session" and r.of("session_end") and r.of("session_end")[0].get("valid") and all(
        v for k, v in r.of("session_end")[0]["valid"].items() if k != "ticks")]
    rows.append(["3", "Sessão de bateria completa e válida nos dois", f"sessões válidas: {len(sessions)} em {sorted({s.label for s in sessions}) or 'nenhum aparelho'} (esperado: 2 por aparelho)"])
    rows.append(["4", "Latência carregada nos dois, com lista de excedentes", f"rodadas de bateria: {len(battery_runs(runs))} em {sorted({r.label for r in battery_runs(runs)}) or 'nenhum aparelho'}"])
    rows.append(["6", "Modo avião demonstrado nos dois", "ver seção Modo avião"])
    rows.append(["7", "Licenças e versões fixadas", "manual: conferir licença na fonte; versões em poc/llama-cpp.pin e poc/assets/models.json"])
    rows.append(["8", "Decisão registrada (modelo, quantização, motor, hospedagem)", "PENDENTE: decisão do usuário, depois ADR"])
    out += table(["#", "Critério", "Dados"], rows)
    out.append(f"Etiquetas com resultados: {', '.join(labels) or 'nenhuma'}.")
    out.append("")
    return out


def build(runs: list[Run], catalog: list[dict]) -> str:
    lines = ["# Etapa 0 — relatório de medições (gerado)", "",
             "Gerado por `scripts/poc/report.py`. Resume medições da bancada; **não decide** modelo, motor, quantização "
             "nem hospedagem (S3 §2, critério 8). Frases reais do casal não aparecem: a bancada não as registra.", ""]
    lines += devices_section(runs) + candidates_section(catalog) + quality_section(runs) + latency_section(runs)
    lines += cold_section(runs) + session_section(runs) + stability_section(runs) + offline_section(runs) + exit_criteria(runs, catalog)
    return "\n".join(lines) + "\n"


def main(argv: list[str] | None = None) -> int:
    root = Path(__file__).resolve().parents[2]
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--results", type=Path, default=root / "poc" / "results")
    parser.add_argument("--catalog", type=Path, default=root / "poc" / "assets" / "models.json")
    parser.add_argument("-o", "--output", type=Path)
    args = parser.parse_args(argv)
    catalog = json.loads(args.catalog.read_text(encoding="utf-8"))["models"]
    runs = load_runs(args.results) if args.results.is_dir() else []
    text = build(runs, catalog)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(text, encoding="utf-8")
        print(f"Relatório em {args.output} ({len(runs)} arquivos de resultado)")
    else:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
