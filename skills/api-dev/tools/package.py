"""Build a self-contained api-dev Skill ZIP; users only need Java 17+."""
import argparse
import hashlib
import zipfile
from pathlib import Path


def main() -> None:
    """Package the Skill's instructions, launchers, templates and compiled CLI."""
    tool_root = Path(__file__).resolve().parent
    skill_root = tool_root.parent
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=tool_root / 'dist/api-dev.zip')
    args = parser.parse_args()
    jar = tool_root / 'cli/target/atomic-api-0.8.1.jar'
    if not jar.is_file():
        pom = tool_root / 'cli/pom.xml'
        parser.error(f'Build CLI first: mvn -f "{pom}" package')
    assets = [skill_root / 'SKILL.md']
    for directory in ['references', 'scripts', 'templates']:
        assets.extend(sorted(p for p in (skill_root / directory).rglob('*')
                             if p.is_file() and not any(part.startswith('.')
                                                       for part in p.relative_to(skill_root).parts)))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for source in assets:
            archive.write(source, 'api-dev/' + source.relative_to(skill_root).as_posix())
        archive.write(jar, 'api-dev/lib/atomic-api.jar')
    print(str(args.output.resolve()))
    print('sha256=' + hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
