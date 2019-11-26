#!/usr/bin/env python
import logging
import re
import subprocess
import sys

import requests

from zdl.build import project_dir

_java_version_re = re.compile(r'"(\d+\.\d+).*"')


def java_version():
    version = subprocess.check_output(
        ['java', '-version'], stderr=subprocess.STDOUT
    )
    version = version.decode('utf-8')
    version, = _java_version_re.search(version).groups()
    return version


clj_install_file = 'linux-install-1.10.1.469.sh'
clj_url = 'https://download.clojure.org/install/' + clj_install_file

vendor_dir = project_dir / 'vendor'
clj_install = vendor_dir / clj_install_file
clj_prefix = vendor_dir / 'clojure'
clj_bin = clj_prefix / 'bin' / 'clojure'


def run(clj_args, *args, **kwargs):
    if not vendor_dir.is_dir():
        vendor_dir.mkdir()
    if not clj_install.is_file():
        r = requests.get(clj_url)
        r.raise_for_status()
        clj_prefix.mkdir()
        clj_install.write_bytes(r.content)
        clj_install.chmod(0o755)
    if not clj_bin.is_file():
        subprocess.check_call([
            clj_install.as_posix(), '--prefix', clj_prefix.as_posix()
        ])
    subprocess.run([clj_bin.as_posix()] + clj_args, *args, **kwargs)


def run_module(module_name, aliases):
    logging.info({'clj_run': module_name, 'aliases': aliases})
    run([':'.join(['-A'] + aliases)],
        cwd=(project_dir / module_name).as_posix(),
        check=True)


def main():
    run(sys.argv[1:])
