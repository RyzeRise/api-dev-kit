"""Test code-tool launchers and the actual portable ZIP."""
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'cli/target/atomic-api-0.8.0.jar'
SKILL = ROOT.parent / 'skills/api-dev'


@unittest.skipIf(os.name == 'nt', 'POSIX entry; Windows runs a PowerShell smoke test')
class LauncherTests(unittest.TestCase):
    def command(self, *args, cwd=None, env=None):
        launcher = SKILL / 'scripts/atomic-api.sh'
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


    def test_self_contained_skill_runs_in_arbitrary_locations_and_after_move(self):
        self.assertTrue(JAR.is_file(), 'Build CLI before portable integration tests')
        with tempfile.TemporaryDirectory(prefix="api skill 中文 '") as tmp:
            base = Path(tmp)
            archive = base / 'skill.zip'
            result = subprocess.run([sys.executable, str(ROOT / 'package.py'), '--output', str(archive)],
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            with zipfile.ZipFile(archive) as package:
                self.assertEqual({'api-dev'}, {Path(n).parts[0] for n in package.namelist()})
                package.extractall(base / 'unpacked')
            bundle = base / 'unpacked/api-dev'
            for source in [SKILL / 'SKILL.md', *SKILL.glob('references/*.md'),
                           *SKILL.glob('scripts/*'), *SKILL.glob('templates/**/*')]:
                if source.is_file():
                    self.assertEqual(source.read_bytes(), (bundle / source.relative_to(SKILL)).read_bytes())
            self.assertEqual(JAR.read_bytes(), (bundle / 'lib/atomic-api.jar').read_bytes())
            with zipfile.ZipFile(JAR) as jar:
                self.assertFalse(any(n.startswith('init/skills/') for n in jar.namelist()))
                for source in (SKILL / 'templates').rglob('*'):
                    if source.is_file():
                        self.assertEqual(source.read_bytes(), jar.read('templates/' + source.relative_to(SKILL / 'templates').as_posix()))
            home = base / 'home'
            home.mkdir()
            runtime = base / 'runtime'
            runtime.mkdir()
            for name, source in [('java', shutil.which('java')), ('dirname', shutil.which('dirname')), ('sh', '/bin/sh')]:
                (runtime / name).symlink_to(source)
            env = dict(os.environ, HOME=str(home), JAVA_HOME='', PATH=str(runtime))
            env.pop('ATOMIC_API_JAR', None)
            for layout in ['inside-project', 'outside-project']:
                with self.subTest(layout=layout):
                    project = base / ('business ' + str(len(list(base.glob('business *')))))
                    project.mkdir()
                    installed = project / '.skills/api-dev' if layout == 'inside-project' else base / 'outside 任意位置/api-dev'
                    installed.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copytree(bundle, installed)

                    def run(*args):
                        return subprocess.run(['/bin/sh', str(installed / 'scripts/atomic-api.sh'), *args],
                                              cwd=project, env=env, capture_output=True, text=True)

                    self.assertEqual(0, run('-h').returncode)
                    result = run('init')
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertEqual({'profile.yaml', 'atomic-profile.yaml', 'contracts', 'schemas', 'work', '.gitignore'},
                                     {p.name for p in (project / '.atomic-api').iterdir()})
                    self.assertFalse((installed / '.atomic-api').exists())
                    for path in ['.agents', '.claude', '.cursor']:
                        self.assertFalse((project / path).exists())
                    result = run('new', '--layer', 'app', '--operation', 'queryL2evpnEvplNodes', '--business', 'l2evpn')
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertTrue((project / '.atomic-api/contracts/queryL2evpnEvplNodes.yaml').is_file())
                    result = run('new', '--layer', 'atomic', '--operation', 'getEvplService', '--business', 'evpl', '--api-version', 'R26C10')
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertTrue((project / '.atomic-api/contracts/atomic-getEvplService.yaml').is_file())
                    # Explicit --repo must work without changing the caller's working directory.
                    demo = base / ('demo ' + project.name)
                    self.assertEqual(0, run('demo', '--repo', str(demo)).returncode)
                    contract = demo / 'contracts/strict-handler.yaml'
                    generation = ['generate', '--layer', 'app', '--repo', str(demo), '--contract', str(contract), '--apply']
                    result = run(*generation)
                    self.assertEqual(0, result.returncode, result.stderr)
                    result = run(*generation)
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertIn('NOOP', result.stdout)
                    self.assertEqual(1, run('verify', '--layer', 'app', '--repo', str(demo), '--contract', str(contract)).returncode)
                    legacy = {
                        '.atomic-api/profile.yaml': 'user configuration',
                        '.atomic-api/contracts/user.yaml': 'user contract',
                        '.atomic-api/skills/api-dev/SKILL.md': 'old customized skill',
                        '.atomic-api/templates/app/handler.java.tpl': 'old customized template',
                        '.atomic-api/tool/user-file': 'old runtime',
                    }
                    for name, text in legacy.items():
                        path = project / name
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_text(text, encoding='utf-8')
                    custom = installed / 'references/l2evpn-example.md'
                    custom.write_text('installed customization', encoding='utf-8')
                    result = run('init')
                    self.assertEqual(0, result.returncode, result.stderr)
                    self.assertIn('MIGRATE:', result.stdout)
                    for name, text in legacy.items():
                        self.assertEqual(text, (project / name).read_text(encoding='utf-8'))
                    self.assertEqual('installed customization', custom.read_text(encoding='utf-8'))
                    moved = base / ('moved ' + project.name) / 'api-dev'
                    moved.parent.mkdir()
                    shutil.move(str(installed), str(moved))
                    installed = moved
                    self.assertEqual(0, run('-h').returncode)
                    self.assertEqual(0, run('init').returncode)
                    self.assertEqual(2, run('check', '--repo', 'missing directory').returncode)
                    (installed / 'lib/atomic-api.jar').unlink()
                    result = run('-h')
                    self.assertEqual(2, result.returncode)
                    self.assertIn('JAR', result.stderr)
            self.assertEqual([], list(home.iterdir()))

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
