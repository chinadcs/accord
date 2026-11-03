"""Garante que os exemplos de comando do roteiro e dos scripts da Etapa 0 citam só ids do catálogo ativo."""
import json
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "poc" / "assets" / "models.json"
ACTIVE = [
    "bonsai-1-7b-q1-0",
    "bonsai-4b-q1-0",
    "expense-extraction-0-5b-q4-k-m",
    "lfm2-5-1-2b-instruct-q4-k-m",
    "lfm2-5-350m-q4-k-m",
    "pulse-qwen3-0.6b-q4km",
    "qwen3.5-2b-q5km",
    "tucano2-qwen-0.5b-finbert-f16",
]
# Arquivos com exemplos que o usuário copia e cola. Relatórios de resultado ficam de fora: citam o histórico.
FILES = [
    ROOT / "docs" / "poc" / "etapa-0.md",
    ROOT / "scripts" / "poc" / "poc-run.ps1",
    ROOT / "scripts" / "poc" / "poc-push-models.ps1",
    ROOT / "scripts" / "poc" / "poc-prepare.ps1",
]
# Valor de -Model/-Models/--models: lista de ids separados por vírgula (placeholder <id>, all e $variáveis ficam fora).
REFERENCE = re.compile(r"(?:(?<=\s)-Models?|--models)[ =]\"?([a-z0-9][a-z0-9.,\-]*)")


def catalog_ids():
    models = json.loads(CATALOG.read_text(encoding="utf-8"))["models"]
    return [m["id"] for m in models]


class CatalogRefsTest(unittest.TestCase):
    def test_catalogo_e_a_selecao_ativa_da_raiz_na_ordem(self):
        self.assertEqual(ACTIVE, catalog_ids())

    def test_cada_candidato_tem_hash_tamanho_e_licenca_e_cabe_em_2_gb(self):
        for m in json.loads(CATALOG.read_text(encoding="utf-8"))["models"]:
            with self.subTest(model=m["id"]):
                self.assertRegex(m["sha256"], r"^[0-9a-f]{64}$")
                self.assertTrue(0 < m["bytes"] <= 2_000_000_000)
                self.assertTrue(m["file"].endswith(".gguf"))
                self.assertEqual(Path(m['file']).name, m['file'])
                self.assertTrue(m["license"])

    def test_exemplos_do_roteiro_e_dos_scripts_citam_so_ids_do_catalogo(self):
        known = set(catalog_ids())
        found = 0
        for path in FILES:
            for number, line in enumerate(path.read_text(encoding="utf-8-sig").splitlines(), 1):
                for match in REFERENCE.finditer(line):
                    for model_id in match.group(1).split(","):
                        if not model_id or model_id == "all":
                            continue
                        found += 1
                        with self.subTest(file=path.name, line=number, model=model_id):
                            self.assertIn(model_id, known)
        self.assertGreater(found, 5, "o padrão não achou os exemplos: o teste perdeu a função")

    def test_o_padrao_reconhece_as_formas_usadas_nos_exemplos(self):
        casos = {
            r".\scripts\poc\poc-run.ps1 -Stage battery -Model a-b.c -Label x": "a-b.c",
            r".\scripts\poc\poc-push-models.ps1 -Models a1,b2 [-Serial X]": "a1,b2",
            r'.\gradlew.bat :pocpc:run --args="--models pulse-qwen3-0.6b-q4km"': "pulse-qwen3-0.6b-q4km",
        }
        for linha, esperado in casos.items():
            with self.subTest(linha=linha):
                self.assertEqual(esperado, REFERENCE.search(linha).group(1))


if __name__ == "__main__":
    unittest.main()
