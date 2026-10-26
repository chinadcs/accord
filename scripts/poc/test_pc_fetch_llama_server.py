"""Regressão do fetch no Windows PowerShell, sem rede ou modelo real."""

import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


@unittest.skipUnless(os.name == "nt" and shutil.which("powershell.exe"), "exige Windows PowerShell")
class FetchLlamaServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory(prefix="accord-fetch-test-")
        cls.addClassCleanup(cls.tmp.cleanup)
        root = Path(cls.tmp.name)
        repo = Path(__file__).resolve().parents[2]
        scripts = root / "scripts" / "poc"
        scripts.mkdir(parents=True)
        cls.script = scripts / "pc-fetch-llama-server.ps1"
        shutil.copyfile(repo / "scripts/poc/pc-fetch-llama-server.ps1", cls.script)
        dest = root / "poc/third_party/llama-server-pc"
        dest.mkdir(parents=True)
        shutil.copyfile(repo / "poc/llama-cpp.pin", root / "poc/llama-cpp.pin")
        # Um processo nativo reproduz NativeCommandError do PowerShell 5.1;
        # Write-Error ou mocks de funções PowerShell não exercitam essa fronteira.
        source = root / "FakeServer.cs"
        source.write_text('''
using System;
public class FakeServer {
    public static int Main(string[] args) {
        if (args.Length != 1 || args[0] != "--version") return 99;
        var output = Environment.GetEnvironmentVariable("ACCORD_TEST_VERSION");
        var stream = Environment.GetEnvironmentVariable("ACCORD_TEST_STREAM") == "stdout"
            ? Console.Out : Console.Error;
        stream.WriteLine(output);
        return int.Parse(Environment.GetEnvironmentVariable("ACCORD_TEST_EXIT"));
    }
}
''', encoding="utf-8")
        env = dict(os.environ, ACCORD_TEST_SOURCE=str(source),
                   ACCORD_TEST_EXE=str(dest / "llama-server.exe"))
        compiled = subprocess.run([
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "$ErrorActionPreference = 'Stop'; Add-Type -Path $env:ACCORD_TEST_SOURCE "
            "-OutputAssembly $env:ACCORD_TEST_EXE -OutputType ConsoleApplication",
        ], env=env, capture_output=True, timeout=30)
        if compiled.returncode:
            raise AssertionError(compiled.stderr.decode(errors="replace"))

    def run_fetch(self, version, stream="stderr", exit_code=0):
        env = dict(os.environ, ACCORD_TEST_VERSION=version, ACCORD_TEST_STREAM=stream,
                   ACCORD_TEST_EXIT=str(exit_code), ACCORD_TEST_SCRIPT=str(self.script))
        # Falha imediatamente se o teste tentar sair do caminho local para a rede.
        return subprocess.run([
            "powershell.exe", "-NoProfile", "-NonInteractive", "-Command",
            "function Invoke-RestMethod { throw 'NETWORK_FORBIDDEN' }; "
            "try { & $env:ACCORD_TEST_SCRIPT } catch { "
            "[Console]::Error.WriteLine($_.Exception.Message); exit 1 }",
        ], env=env, capture_output=True, timeout=15)

    def test_modern_version_on_stderr_is_accepted(self):
        result = self.run_fetch("version: 0.7.0-dev (build 11869, commit d327bf1f0)")
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))
        self.assertNotIn(b"NativeCommandError", result.stderr)
        self.assertIn(b"b11869", result.stdout)

    def test_modern_version_on_stdout_is_accepted(self):
        result = self.run_fetch("version: 0.7.0-dev (build 11869, commit d327bf1f0)", "stdout")
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))

    def test_legacy_version_is_accepted(self):
        result = self.run_fetch("version: 11869 (d327bf1)")
        self.assertEqual(0, result.returncode, result.stderr.decode(errors="replace"))

    def test_incompatible_versions_are_rejected(self):
        for version in [
            "version: 0.7.0-dev (build 11870, commit d327bf1f0)",
            "version: 0.7.0-dev (build 11869, commit abcdef012)",
            "version: 11870 (d327bf1)",
            "version: 11869 (abcdef0)",
            "not a version",
            "",
        ]:
            with self.subTest(version=version):
                result = self.run_fetch(version)
                self.assertNotEqual(0, result.returncode)
                self.assertIn(b"-Force", result.stderr)
                self.assertNotIn(b"NETWORK_FORBIDDEN", result.stderr)

    def test_nonzero_exit_is_rejected_even_with_matching_version(self):
        result = self.run_fetch("version: 0.7.0-dev (build 11869, commit d327bf1f0)", exit_code=1)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(b"-Force", result.stderr)


if __name__ == "__main__":
    unittest.main()
