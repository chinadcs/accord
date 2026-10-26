"""Testes do relatório da Etapa 0: contas de latência, critérios e privacidade. Rodar com:
python -m unittest discover -s scripts/poc -p "test_*.py"
"""

import json
import tempfile
import unittest
from pathlib import Path

import report

CATALOG = [
    {"id": "m-small", "label": "Pequeno", "quant": "Q4_K_M", "bytes": 400_000_000, "license": "x"},
    {"id": "m-huge", "label": "Grande", "quant": "Q8_0", "bytes": 2_500_000_000, "license": "x"},
]


def write(root: Path, label: str, name: str, records: list[dict]) -> None:
    folder = root / label
    folder.mkdir(parents=True, exist_ok=True)
    (folder / name).write_text("\n".join(json.dumps(r, ensure_ascii=False) for r in records) + "\n", encoding="utf-8")


def infer(case: str, ms: float, rep: int = 1, outcome: str = "Match", short: bool = True, sensitive: bool = False, thermal: int = 0) -> dict:
    record = {
        "type": "infer", "case": case, "group": "Valores", "rep": rep, "sensitive": sensitive, "chars": 12, "short": short,
        "modelCalled": True, "tPipelineMs": ms, "outcome": outcome, "decision": "register",
        "stats": {"tPrefillMs": 100.0, "tGenMs": ms - 100, "prefixReused": True}, "power": {"thermalStatus": thermal},
    }
    if not sensitive:
        record["text"] = "café 5"
        record["output"] = "{}"
    return record


class ReportTest(unittest.TestCase):
    def test_percentil_interpolado(self):
        values = [1000.0, 2000.0, 3000.0, 4000.0, 5000.0]
        self.assertEqual(report.percentile(values, 50), 3000.0)
        self.assertEqual(report.percentile(values, 100), 5000.0)
        self.assertAlmostEqual(report.percentile(values, 90), 4600.0)
        self.assertIsNone(report.percentile([], 50))

    def test_lista_cada_caso_acima_de_5_s_e_nao_so_a_media(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "s20fe", "a.jsonl", [
                {"type": "run_start", "stage": "battery", "model": "m-small", "tag": "t", "threads": 4, "environment": {"airplane": True, "activeNetwork": False}},
                infer("valor-1", 1200), infer("valor-2", 6100, thermal=2), infer("valor-3", 900, short=False),
                {"type": "run_end", "reason": "done"},
            ])
            text = report.build(report.load_runs(root), CATALOG)
        self.assertIn("valor-2", text)
        self.assertIn("6100 ms", text)
        self.assertIn("moderado", text)
        self.assertNotIn("| valor-3 |", text.split("Todos os casos acima de 5 s")[1])

    def test_texto_de_caso_sensivel_nao_chega_ao_relatorio(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            rec = infer("real-7", 800, outcome="AcceptedWrong", sensitive=True)
            rec["text"] = "SEGREDO"  # se um dia a bancada gravar, o relatório ainda não pode repetir
            write(root, "s23fe", "b.jsonl", [{"type": "run_start", "stage": "battery", "model": "m-small"}, rec])
            text = report.build(report.load_runs(root), CATALOG)
        self.assertIn("real-7", text)
        self.assertNotIn("SEGREDO", text)

    def test_criterios_de_qualidade_aparecem_com_a_situacao(self):
        summary = {
            "total": 10, "clearTotal": 7, "refusedFormat": 0, "acceptedWrongOther": 1, "allMet": False,
            "correction": {"ok": 1, "total": 1},
            "criteria": [
                {"name": "Intenção", "required": "mínimo 90%", "actual": "95.0%", "met": True},
                {"name": "Aceitas pelo validador porém erradas (frases claras)", "required": "0", "actual": "1", "met": False},
            ],
        }
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "s20fe", "c.jsonl", [{"type": "run_start", "stage": "battery", "model": "m-small", "tag": "t"}, {"type": "quality_summary", "summary": summary}])
            text = report.build(report.load_runs(root), CATALOG)
        self.assertIn("| Intenção | mínimo 90% | 95.0% | atendido |", text)
        self.assertIn("NÃO atendido", text)
        self.assertIn("m-small: s20fe=NÃO", text)

    def test_candidato_acima_do_teto_e_sinalizado(self):
        text = report.build([], CATALOG)
        self.assertIn("NÃO (acima do teto)", text)
        self.assertIn("candidatos acima do teto: m-huge", text)
        self.assertIn("PENDENTE: decisão do usuário", text)

    def test_sessao_invalida_nao_e_apresentada_como_valida(self):
        ticks = [{"type": "tick", "k": k, "pipelineMs": 1500.0 + k, "over5s": False, "power": {"batteryPct": 100 - k, "batteryTempC": 30.0 + k / 2, "thermalStatus": 0}} for k in range(4)]
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "s20fe", "s.jsonl", [
                {"type": "run_start", "stage": "session", "model": "m-small", "tag": "x"},
                {"type": "session_start", "power": {"batteryPct": 100, "thermalStatus": 0}},
                *ticks,
                {"type": "session_end", "powerEnd": {"batteryPct": 96}, "valid": {"startedFull": True, "unpluggedAtStart": True, "unpluggedAllTicks": False, "airplaneAllTicks": True, "screenOnAllTicks": True, "ticks": 4}},
                {"type": "run_end", "reason": "done"},
            ])
            text = report.build(report.load_runs(root), CATALOG)
        self.assertIn("INVÁLIDA para o protocolo: unpluggedAllTicks", text)
        self.assertIn("sessões válidas: 0", text)

    def test_linha_corrompida_nao_derruba_o_relatorio(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            folder = root / "emu"
            folder.mkdir()
            (folder / "x.jsonl").write_text('{"type":"run_start","stage":"info"}\n{quebrada\n', encoding="utf-8")
            runs = report.load_runs(root)
        self.assertEqual(len(runs[0].records), 2)
        self.assertIn("Etapa 0", report.build(runs, CATALOG))


if __name__ == "__main__":
    unittest.main()
