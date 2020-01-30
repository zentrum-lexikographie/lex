import datetime

import click

import zdl_lex_build.clean
import zdl_lex_build.clj
import zdl_lex_build.docker
import zdl_lex_build.oxygen
import zdl_lex_build.version


def check_java_version():
    java_version = zdl_lex_build.clj.java_version()
    if java_version != '1.8':
        raise click.ClickException(
            'Java v1.8 is required for building the Oxygen Plugin '
            '("%s" detected)' % (java_version, )
        )


def banner(msg):
    click.secho(' '.join((str(datetime.datetime.now()), '[', msg, ']')), bold=True)


@click.group(chain=True)
def main():
    'Scripts for building, compiling, packaging ZDL-Lex.'
    pass


@main.command('init')
def cli_init():
    'Init build.'
    banner('Init')
    check_java_version()
    zdl_lex_build.clean.all_classes()
    zdl_lex_build.version.write_version_edn()


@main.command('clean')
def cli_clean():
    'Clean compiler output.'
    banner('Clean')
    zdl_lex_build.clean.all_classes()


@main.command('schema')
def cli_schema():
    'Compile RelaxNG/Schematron rules.'
    banner('Schema')
    zdl_lex_build.clj.run_module('common', ['schema'])


def build_modules(modules=['client', 'server', 'validator']):
    for module in modules:
        banner('"%s" module' % (module,))
        zdl_lex_build.clj.run_module(module, ['dev', 'prod', 'compile'])
        zdl_lex_build.clj.run_module(module, ['dev', 'prod', 'package'])


@main.command('build')
@click.pass_context
def cli_build(ctx):
    'Compile and package modules.'
    ctx.invoke(cli_init)
    ctx.invoke(cli_schema)
    build_modules()
    ctx.invoke(cli_clean)


@main.command('version')
def cli_version():
    'Print current version'
    click.secho(zdl_lex_build.version.current_version())


@main.command('release')
def cli_release():
    'Tag and push next version.'
    click.secho(zdl_lex_build.version.set_next_version())


@main.command('docker')
def cli_deploy():
    'Build Docker images.'
    banner('Docker')
    zdl_lex_build.docker.build()


@main.command('client')
@click.pass_context
def cli_client(ctx):
    'Runs the ZDL-Lex client (in Oxygen XML Editor).'
    ctx.invoke(cli_init)
    ctx.invoke(cli_schema)
    build_modules(['client'])
    zdl_lex_build.oxygen.run()


@main.command('server')
@click.pass_context
def cli_server(ctx):
    'Runs the ZDL-Lex server.'
    ctx.invoke(cli_init)
    zdl_lex_build.clj.run_module('server', ['prod', 'server'])


@main.command('solr')
@click.argument('image')
def cli_solr(image):
    'Runs Solr as a local Docker container for testing.'
    zdl_lex_build.docker.run_solr(image)


@main.command('dwdswb')
def cli_dwdswb():
    'Runs a MySQL Docker container for testing.'
    zdl_lex_build.docker.run_dwdswb_db()
