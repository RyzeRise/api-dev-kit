"""Test the command and installation using actual subprocesses."""
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAR = ROOT / 'cli/target/atomic-api-0.1.0.jar'


@unittest.skipIf(os.name == 'nt', 'POSIX entry; Windows runs a PowerShell smoke test')
class LauncherTests(unittest.TestCase):
    def command(self, *args, cwd=None, env=None):
        launcher = ROOT / 'bin/atomic-api'
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

    def test_installed_command_runs_outside_checkout(self):
        self.assertTrue((ROOT / 'install.sh').is_file(), 'installer is missing')
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix="atomic install '") as tmp:
            home = Path(tmp) / 'home'
            home.mkdir()
            prefix = Path(tmp) / 'tools with spaces'
            env = dict(os.environ, HOME=str(home), SHELL='/bin/sh')
            args = ['sh', str(ROOT / 'install.sh'), '--prefix', str(prefix), '--jar', str(JAR)]
            result = subprocess.run(args, env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            before = (home / '.profile').read_text()
            self.assertEqual(0, subprocess.run(args, env=env, capture_output=True).returncode)
            self.assertEqual(before, (home / '.profile').read_text())
            result = subprocess.run(['sh', '-c', '. "$HOME/.profile"; atomic-api -h'], cwd=tmp,
                                    env=env, capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn('atomic-api init', result.stdout)

    def test_arguments_and_exit_code_survive_launcher(self):
        with tempfile.TemporaryDirectory(prefix='atomic args ') as tmp:
            fake_java = Path(tmp) / 'java'
            fake_java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\nexit 7\n', encoding='utf-8')
            fake_java.chmod(0o755)
            env = dict(os.environ, JAVA_HOME=tmp, PATH=tmp + os.pathsep + os.environ['PATH'])
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

    def test_portable_package_installs_without_source_or_maven(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix='atomic portable ') as tmp:
            package = Path(tmp) / 'download'
            package.mkdir()
            shutil.copytree(ROOT / 'bin', package / 'bin')
            shutil.copy(ROOT / 'install.sh', package / 'install.sh')
            lib = package / 'lib/atomic-api'
            lib.mkdir(parents=True)
            shutil.copy(JAR, lib / 'atomic-api.jar')
            home = Path(tmp) / 'home'
            home.mkdir()
            env = dict(os.environ, HOME=str(home), SHELL='/bin/sh')
            result = subprocess.run(['sh', str(package / 'install.sh')], cwd=tmp, env=env,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run([str(home / '.local/bin/atomic-api'), '-h'], env=env,
                                    capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_installer_refuses_symlink_destination_before_writing(self):
        if not JAR.is_file():
            self.skipTest('Build CLI before command integration tests')
        with tempfile.TemporaryDirectory(prefix='atomic protected ') as tmp:
            prefix = Path(tmp) / 'prefix'
            lib = prefix / 'lib/atomic-api'
            lib.mkdir(parents=True)
            victim = Path(tmp) / 'user-file'
            victim.write_text('preserve', encoding='utf-8')
            (lib / 'atomic-api.jar').symlink_to(victim)
            env = dict(os.environ, HOME=tmp, SHELL='/bin/sh')
            result = subprocess.run(['sh', str(ROOT / 'install.sh'), '--prefix', str(prefix), '--jar', str(JAR)],
                                    env=env, capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            self.assertEqual('preserve', victim.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
