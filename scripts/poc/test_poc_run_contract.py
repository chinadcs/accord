"""O poc-run.ps1 e a bancada Android combinam os mesmos extras de Intent; um nome trocado desliga o perfil em silêncio."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = (ROOT / "scripts" / "poc" / "poc-run.ps1").read_text(encoding="utf-8-sig")
CONTROLLER = (ROOT / "poc" / "src" / "main" / "kotlin" / "app" / "accord" / "poc" / "PocController.kt").read_text(encoding="utf-8")
ETAPA0 = (ROOT / "docs" / "poc" / "etapa-0.md").read_text(encoding="utf-8")


def script_extras():
    """`--es/--ei/--ez nome` do `am start`."""
    return set(re.findall(r"'--e[siz]', '([a-z]+)'", SCRIPT))


def controller_extras():
    return set(re.findall(r'intent\.get(?:String|Int|Boolean)Extra\("([a-z]+)"', CONTROLLER))


class PocRunContractTest(unittest.TestCase):
    def test_todo_extra_enviado_pelo_script_e_lido_pela_bancada(self):
        sent, read = script_extras(), controller_extras()
        self.assertIn("routed", sent)
        self.assertIn("rv", sent)
        self.assertEqual(set(), sent - read, "o script envia extras que a bancada ignora")

    def test_contexto_padrao_comporta_o_prompt_do_produto(self):
        self.assertRegex(SCRIPT, r"\[int\]\$Ctx = 4096")

    def test_rodada_com_janela_pequena_e_recusada_antes_de_ir_ao_aparelho(self):
        self.assertRegex(SCRIPT, r"\$Ctx -lt 4096 -and -not \$SmallContext -and -not \$Force")

    def test_o_roteiro_manda_usar_o_perfil_em_etapas_nos_comandos_de_bateria(self):
        battery = [line for line in ETAPA0.splitlines() if "poc-run.ps1 -Stage battery" in line]
        self.assertTrue(battery)
        for line in battery:
            with self.subTest(line=line):
                self.assertIn("-Routed", line)


if __name__ == "__main__":
    unittest.main()
