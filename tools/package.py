"""Package built CLI and launchers; end users do not need Python."""
import argparse
import hashlib
import zipfile
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=Path(__file__).resolve().parent / 'dist/atomic-api-cli.zip')
    args = parser.parse_args()
    root = Path(__file__).resolve().parent
    jar = root / 'cli/target/atomic-api-0.7.0.jar'
    if not jar.is_file():
        parser.error('Build CLI first: mvn -f tools/cli/pom.xml package')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'w', zipfile.ZIP_DEFLATED) as archive:
        for relative in ['atomic-api.sh', 'atomic-api.cmd', 'atomic-api.ps1']:
            archive.write(root / relative, 'atomic-api/' + relative)
        archive.write(jar, 'atomic-api/lib/atomic-api/atomic-api.jar')
    print(str(args.output.resolve()))
    print('sha256=' + hashlib.sha256(args.output.read_bytes()).hexdigest())


if __name__ == '__main__':
    main()
