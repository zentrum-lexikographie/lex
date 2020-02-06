import logging
import docker

from zdl_lex_build import project_dir
import zdl_lex_build.version

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
    current_version = zdl_lex_build.version.current_version()
    for module in docker_modules():
        name = module.name
        tag = docker_tag(name, current_version)
        logging.info({'tag': tag})
        _docker_client.images.build(
            path=module.as_posix(), tag=tag, rm=True, forcerm=True
        )


def run_container(name, *args, **kwargs):
    try:
        c = _docker_client.containers.get(name)
        if c.status != "running":
            c.start()
    except docker.errors.NotFound:
        kwargs["name"] = name
        _docker_client.containers.run(*args, **kwargs)


def run_server():
    image = docker_tag('server', zdl_lex_build.version.current_version())
    git_volume = (project_dir / 'data' / 'git').as_posix()
    data_volume = (project_dir / 'data' / 'volume').as_posix()
    run_container(
        'zdl_lex_server',
        image,
        detach=True,
        environment={
            'ZDL_LEX_GIT_BRANCH': 'zdl-lex-server/docker',
            'ZDL_LEX_GIT_ORIGIN': 'file:///git'
        },
        network_mode='host',
        volumes={
            data_volume: {'bind': '/data', 'mode': 'rw'},
            git_volume: {'bind': '/git', 'mode': 'ro'}
        }
    )


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


if __name__ == '__main__':
    run_server()
