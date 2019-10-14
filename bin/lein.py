from pathlib import Path
import subprocess
import sys
import requests

lein_url = 'https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein'

bin_dir = (Path(__file__) / '..').resolve()
lein = bin_dir / 'lein'


def run(lein_args, *args, **kwargs):
    if not lein.is_file():
        r = requests.get(lein_url)
        r.raise_for_status()
        lein.write_bytes(r.content)
        lein.chmod(0o755)
    subprocess.run([lein.as_posix()] + lein_args, *args, **kwargs)


if __name__ == '__main__':
    run(sys.argv[1:])
