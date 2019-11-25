#!/usr/bin/env python
import logging

from pathlib import Path
import re
import subprocess
import sys
import requests

_java_version_re = re.compile(r'"(\d+\.\d+).*"')


def java_version():
    version = subprocess.check_output(
        ['java', '-version'], stderr=subprocess.STDOUT
    )
    version = version.decode('utf-8')
    version, = _java_version_re.search(version).groups()
    return version


bin_dir = (Path(__file__) / '..').resolve()
project_dir = (bin_dir / '..').resolve()

clj_install_file = 'linux-install-1.10.1.469.sh'
clj_url = 'https://download.clojure.org/install/' + clj_install_file

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


def run_module(module_name, aliases):
    logging.info({'clj_run': module_name, 'aliases': aliases})
    run([':'.join(['-A'] + aliases)],
        cwd=(project_dir / module_name).as_posix(),
        check=True)


if __name__ == '__main__':
    run(sys.argv[1:])
