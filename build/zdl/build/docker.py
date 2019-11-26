import logging

import docker

from zdl.build import project_dir
import zdl.build.version

_docker_registry = 'lex.dwds.de'


def docker_tag(name, version=None):
    tag = '/'.join((_docker_registry, 'zdl-lex', name))
    if version is not None:
        tag = tag + ":" + version
    return tag


_docker_dir = project_dir / 'docker'
_docker_client = docker.from_env()


def docker_modules():
    return [d for d in _docker_dir.glob('*') if d.is_dir()]


def build():
    current_version = zdl.build.version.current_version()
    for module in docker_modules():
        name = module.name
        tag = docker_tag(name, current_version)
        logging.info({'tag': tag})
        _docker_client.images.build(path=module.as_posix(), tag=tag)


def run_container(name, *args, **kwargs):
    try:
        c = _docker_client.containers.get(name)
        if c.status != "running":
            c.start()
    except docker.errors.NotFound:
        kwargs["name"] = name
        _docker_client.containers.run(*args, **kwargs)


def run_solr(image):
    run_container(
        'zdl_lex_solr',
        image,
        detach=True,
        network_mode='host'
    )


def run_dwdswb_db():
    run_container(
        'zdl_lex_dwdswb',
        'mysql:5',
        'mysqld',
        detach=True,
        tty=True,
        environment={
            'MYSQL_DATABASE': 'dwdswb',
            'MYSQL_USER': 'dwdswb',
            'MYSQL_PASSWORD': 'dwdswb',
            'MYSQL_RANDOM_ROOT_PASSWORD': 'yes'
        },
        network_mode='host'
    )
