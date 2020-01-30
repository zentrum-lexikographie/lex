#!/usr/bin/env python

from pathlib import Path
import os
import subprocess

from zdl_lex_build import project_dir


oxygen_dir = project_dir / 'oxygen'


def get_home():
    oxygen_home = os.getenv('OXYGEN_HOME')
    if oxygen_home is None:
        candidate_dirs = [Path.home() / 'oxygen']
        candidate_dirs.extend(
            reversed(sorted(Path('/usr/local').glob('Oxygen XML Editor*')))
        )
        for candidate in candidate_dirs:
            if candidate.is_dir():
                oxygen_home = candidate
                break
    if oxygen_home is None:
        raise Exception(
            'No Oxygen XML Editor installation ($OXYGEN_HOME) found'
        )
    return oxygen_home


def run():
    oxygen_home = get_home()
    oxygen_env = dict(os.environ, OXYGEN_HOME=oxygen_home.as_posix())
    oxygen_classpath = ':'.join([
        (oxygen_home / 'lib' / 'oxygen.jar').as_posix(),
        (oxygen_home / 'lib' / 'oxygen-basic-utilities.jar').as_posix(),
        (oxygen_home / 'classes').as_posix(),
        oxygen_home.as_posix()
    ])

    subprocess.run([
        (oxygen_home / 'jre' / 'bin' / 'java').as_posix(),
        '-Dcom.oxygenxml.editor.plugins.dir=' + oxygen_dir.as_posix(),
        '-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor',
        '-cp', oxygen_classpath,
        'ro.sync.exml.Oxygen', 'test-project.xpr'
    ], cwd=oxygen_dir.as_posix(), env=oxygen_env)
