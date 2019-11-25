import logging
from pathlib import Path

import docker
import version

project_dir = (Path(__file__) / '..' / '..').resolve()

_docker_registry = 'lex.dwds.de'
_docker_dir = project_dir / 'docker'
_docker_client = docker.from_env()


def docker_modules():
    return [d for d in _docker_dir.glob('*') if d.is_dir()]


def build_docker():
    current_version = version.current_version()
    for module in docker_modules():
        name = module.name
        tag = ':'.join((
            '/'.join((_docker_registry, 'zdl-lex', name)),
            current_version
        ))
        logging.info({'tag': tag})
        _docker_client.images.build(path=module.as_posix(), tag=tag)
