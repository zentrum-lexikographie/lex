#!/usr/bin/env python
from pathlib import Path
import subprocess
import sys
import requests

clj_install_file = 'linux-install-1.10.1.469.sh'
clj_url = 'https://download.clojure.org/install/' + clj_install_file

bin_dir = (Path(__file__) / '..').resolve()
clj_install = bin_dir / clj_install_file
clj_prefix = bin_dir / 'clojure'
clj_bin = clj_prefix / 'bin' / 'clojure'


def run(clj_args, *args, **kwargs):
    if not clj_prefix.is_dir():
        r = requests.get(clj_url)
        r.raise_for_status()
        clj_install.write_bytes(r.content)
        clj_install.chmod(0o755)
        subprocess.check_call([
            clj_install.as_posix(), '--prefix', clj_prefix.as_posix()
        ])
        clj_install.unlink()
    subprocess.run([clj_bin.as_posix()] + clj_args, *args, **kwargs)


if __name__ == '__main__':
    run(sys.argv[1:])
