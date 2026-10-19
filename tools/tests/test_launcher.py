"""Test code-tool launchers and the actual portable ZIP."""
import json
import os
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'cli/target/atomic-api-0.7.1.jar'


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
                expected = {'init/skills/' + str(p.relative_to(ROOT.parent / 'skills'))
                            for p in (ROOT.parent / 'skills').rglob('*.md') if p.is_file()}
                self.assertEqual(expected, names)
                for name in names:
                    self.assertEqual((ROOT.parent / 'skills' / name.removeprefix('init/skills/')).read_bytes(), jar.read(name))
                templates = {n for n in jar.namelist() if n.startswith('templates/') and not n.endswith('/')}
                self.assertEqual({'templates/' + str(p.relative_to(ROOT / 'templates'))
                                  for p in (ROOT / 'templates').rglob('*') if p.is_file()}, templates)
                for name in templates:
                    self.assertEqual((ROOT / name).read_bytes(), jar.read(name))
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
            for original in (ROOT.parent / 'skills').rglob('*.md'):
                relative = original.relative_to(ROOT.parent / 'skills')
                self.assertEqual(original.read_bytes(), (project / '.atomic-api/skills' / relative).read_bytes())
            for original in (ROOT / 'templates').rglob('*'):
                if original.is_file():
                    relative = original.relative_to(ROOT / 'templates')
                    self.assertEqual(original.read_bytes(), (project / '.atomic-api/templates' / relative).read_bytes())
            custom = project / '.atomic-api/templates/app/handler.java.tpl'
            custom.write_text('user template customization', encoding='utf-8')
            custom_ref = project / '.atomic-api/skills/api-dev/references/l2evpn-example.md'
            custom_ref.write_text('user case customization', encoding='utf-8')
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'init'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual('user template customization', custom.read_text(encoding='utf-8'))
            self.assertEqual('user case customization', custom_ref.read_text(encoding='utf-8'))
            for agent_dir in ['.agents', '.claude', '.cursor']:
                self.assertFalse((project / agent_dir).exists())
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'new', '--operation', 'queryL2evpnEvplNodes', '--business', 'l2evpn'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertTrue((project / '.atomic-api/contracts/queryL2evpnEvplNodes.yaml').is_file())
            self.assertEqual(before, list(home.iterdir()))
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), 'check', '--repo', 'missing directory'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            (tool / 'lib/atomic-api/atomic-api.jar').unlink()
            result = subprocess.run(['/bin/sh', str(tool / 'atomic-api.sh'), '-h'],
                                    cwd=project, env=env, capture_output=True, text=True)
            self.assertEqual(2, result.returncode)
            self.assertIn('JAR', result.stderr)

    def test_packaged_generators_read_templates_instead_of_hardcoded_structure(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix='template consumption ') as tmp:
            project = Path(tmp) / 'project'
            project.mkdir()
            patched = Path(tmp) / 'patched.jar'
            with zipfile.ZipFile(JAR) as source, zipfile.ZipFile(patched, 'w') as target:
                for entry in source.infolist():
                    data = source.read(entry)
                    if entry.filename in ('templates/app/handler.java.tpl', 'templates/atomic/implementation.java.tpl'):
                        data = data.replace(b'public class', b'/* TEMPLATE_READ_PROOF */ public class', 1)
                    target.writestr(entry, data)
            env = dict(os.environ, ATOMIC_API_JAR=str(patched))
            self.assertEqual(0, self.command('demo', cwd=project, env=env).returncode)
            result = self.command('generate', '--layer', 'app', '--contract',
                                  str(project / 'contracts/strict-handler.yaml'), '--apply',
                                  cwd=project, env=env)
            self.assertEqual(0, result.returncode, result.stderr)
            handlers = [p for p in project.rglob('*ApiHandler.java') if p.name != 'ApiHandler.java']
            self.assertEqual(1, len(handlers))
            self.assertIn('TEMPLATE_READ_PROOF', handlers[0].read_text(encoding='utf-8'))
            # Synthetic fixture only: a JDK annotation avoids inventing a private registration API.
            self.assertEqual(0, self.command('init', cwd=project, env=env).returncode)
            common = 'com.example.atomic.common.'
            model = lambda side: {
                'class': common + 'dto.evpl.GetEvplService' + ('ReqDto' if side == 'request' else 'RspDto'),
                'sourceRoot': 'api-atomic-common/src/main/java',
                'fields': [{'name': 'neId' if side == 'request' else 'name', 'type': 'java.lang.String',
                            'description': '合成测试字段', 'required': side == 'request', 'diagnostic': False}]}
            contract = project / '.atomic-api/contracts/atomic-getEvplService.json'
            contract.write_text(json.dumps({
                'version': 1, 'layer': 'atomic', 'operation': 'getEvplService', 'business': 'evpl',
                'description': '合成工具测试，不代表业务注册',
                'apiInterface': common + 'apiinterface.evpl.AtomicApiGetEvplServiceInterface',
                'latest': common + 'latest.evpl.AtomicApiGetEvplServiceLatest',
                'models': {side: model(side) for side in ('request', 'response')},
                'releases': [{'id': 'R26C10',
                              'implementation': 'com.example.atomic.impl.api.evpl.AtomicApiGetEvplServiceR26C10',
                              'annotations': ['@java.lang.Deprecated']}],
                'latestAnnotations': []}), encoding='utf-8')
            result = self.command('generate', '--layer', 'atomic', '--contract', str(contract), '--apply',
                                  cwd=project, env=env)
            self.assertEqual(0, result.returncode, result.stderr)
            atomic = list(project.rglob('AtomicApiGetEvplServiceR26C10.java'))
            self.assertEqual(1, len(atomic))
            self.assertIn('TEMPLATE_READ_PROOF', atomic[0].read_text(encoding='utf-8'))




if __name__ == '__main__':
    unittest.main()
