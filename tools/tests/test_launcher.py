"""Test code-tool launchers and the actual portable ZIP."""
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'cli/target/atomic-api-0.7.0.jar'


@unittest.skipIf(os.name == 'nt', 'POSIX entry; Windows runs a PowerShell smoke test')
class LauncherTests(unittest.TestCase):
    def command(self, *args, cwd=None, env=None):
        launcher = ROOT / 'atomic-api.sh'
        self.assertTrue(launcher.is_file(), 'atomic-api command entry is missing')
        return subprocess.run(['sh', str(launcher), *args], cwd=cwd, env=env,
                              capture_output=True, text=True)

    def test_help_init_current_directory_and_preserve_config(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix='atomic cli ') as tmp:
            result = self.command('-h', cwd=tmp)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('atomic-api init', result.stdout)
            result = self.command('init', cwd=tmp)
            self.assertEqual(0, result.returncode, result.stderr)
            profile = Path(tmp) / '.atomic-api/profile.yaml'
            self.assertTrue(profile.is_file())
            profile.write_text('preserve user file', encoding='utf-8')
            self.assertEqual(0, self.command('init', cwd=tmp).returncode)
            self.assertEqual('preserve user file', profile.read_text(encoding='utf-8'))


    def test_arguments_and_exit_code_survive_launcher(self):
        with tempfile.TemporaryDirectory(prefix='atomic args ') as tmp:
            fake_java = Path(tmp) / 'java'
            fake_java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\nexit 7\n', encoding='utf-8')
            fake_java.chmod(0o755)
            dummy_jar = Path(tmp) / 'dummy.jar'
            dummy_jar.touch()
            env = dict(os.environ, JAVA_HOME=tmp, ATOMIC_API_JAR=str(dummy_jar),
                       PATH=tmp + os.pathsep + os.environ['PATH'])
            result = self.command('check', '--repo', 'project with spaces', env=env)
            self.assertEqual(7, result.returncode)
            self.assertEqual(['check', '--repo', 'project with spaces'], result.stdout.splitlines()[-3:])

    def test_demo_defaults_to_current_directory(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix='atomic demo ') as tmp:
            result = self.command('demo', cwd=tmp)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue((Path(tmp) / 'contracts/single-handler.yaml').is_file())


    def test_extracted_zip_runs_without_installation_and_preserves_cwd(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix="atomic portable '") as tmp:
            base = Path(tmp)
            archive = base / 'tool.zip'
            result = subprocess.run(['python', str(ROOT / 'package.py'), '--output', str(archive)],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            with zipfile.ZipFile(archive) as package:
                package.extractall(base / 'unpacked tools')
            with zipfile.ZipFile(JAR) as jar:
                names = {n for n in jar.namelist() if n.startswith('init/skills/') and not n.endswith('/')}
                expected = {'init/skills/api-dev/SKILL.md', 'init/skills/api-dev/references/api-atomic.md', 'init/skills/api-dev/references/app-api.md'}
                self.assertEqual(expected, names)
                for name in names:
                    self.assertEqual((ROOT.parent / 'skills' / name.removeprefix('init/skills/')).read_bytes(), jar.read(name))
            tool = base / 'unpacked tools/atomic-api'
            project = base / 'business project'
            project.mkdir()
            home = base / 'home'
            home.mkdir()
            # Only Java is on PATH; no installer or Maven is available.
            runtime = base / 'runtime'
            runtime.mkdir()
            (runtime / 'java').symlink_to(shutil.which('java'))
            (runtime / 'dirname').symlink_to(shutil.which('dirname'))
            (runtime / 'sh').symlink_to('/bin/sh')
            env = dict(os.environ, HOME=str(home), JAVA_HOME='', PATH=str(runtime))
            self.assertIsNone(shutil.which('mvn', path=env['PATH']))
            before = list(home.iterdir())
            # Standard repository-local layout; no global command registration.
            local_tool = project / '.atomic-api/tool'
            local_tool.parent.mkdir()
            shutil.move(str(tool), str(local_tool))
            tool = local_tool
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'init'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue((project / '.atomic-api/skills/api-dev/SKILL.md').is_file())
            self.assertTrue((project / '.atomic-api/skills/api-dev/references/app-api.md').is_file())
            self.assertFalse((tool / '.atomic-api').exists())
            for skill in ['api-dev']:
                self.assertEqual((ROOT.parent / 'skills' / skill / 'SKILL.md').read_bytes(),
                                 (project / '.atomic-api/skills' / skill / 'SKILL.md').read_bytes())
            for agent_dir in ['.agents', '.claude', '.cursor']:
                self.assertFalse((project / agent_dir).exists())
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'new', '--operation', 'queryCircuit', '--business', 'circuit'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue((project / '.atomic-api/contracts/queryCircuit.yaml').is_file())
            self.assertEqual(before, list(home.iterdir()))
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'check', '--repo', 'missing directory'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            (tool / 'lib/atomic-api/atomic-api.jar').unlink()
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), '-h'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('JAR', result.stderr)



if __name__ == '__main__':
    unittest.main()
