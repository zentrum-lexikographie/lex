from pathlib import Path

import click

import zdl.lex
import zdl.metadata
import zdl.mysql
import zdl.qa

from zdl import logger


def home(ctx):
    return ctx.obj['home']


def server(ctx):
    return ctx.obj['server']


def article_files(ctx):
    return zdl.article.files(home(ctx))


def article_progress(ctx, label):
    num_articles = sum([1 for f in article_files(ctx)])
    return click.progressbar(
        article_files(ctx),
        length=num_articles,
        width=0, label=label
    )


@click.group()
@click.option('--server-base',
              envvar='ZDL_LEX_SERVER_BASE',
              default='https://lex.dwds.de/')
@click.option('--server-user',
              envvar='ZDL_LEX_SERVER_USER')
@click.option('--server-password',
              envvar='ZDL_LEX_SERVER_PASSWORD')
@click.option('--git-origin',
              envvar='ZDL_LEX_GIT_ORIGIN',
              default='git@lex.dwds.de:lex.git')
@click.option('--git-branch',
              envvar='ZDL_LEX_GIT_BRANCH',
              default='zdl-lex-server')
@click.option('--home',
              envvar='ZDL_LEX_HOME',
              required=True,
              type=click.Path(exists=True, file_okay=False, resolve_path=True))
@click.pass_context
def cli(ctx,
        server_base, server_user, server_password,
        git_origin, git_branch,
        home):
    ctx.ensure_object(dict)
    ctx.obj['server'] = zdl.lex.Server(
        server_base,
        (server_user, server_password) if server_user else None
    )
    ctx.obj['home'] = Path(home)


@cli.command()
@click.pass_context
def metadata(ctx):
    zdl.metadata.read(home(ctx), article_files(ctx))


@cli.command()
@click.pass_context
def qa(ctx):
    with article_progress(ctx, 'Red-1 -> Red-2') as articles:
        zdl.qa.perform(articles)


@cli.command()
@click.pass_context
def lock(ctx):
    try:
        logger.info(server(ctx).acquire_lock("", 90))
        import time
        time.sleep(60)
    except Exception:
        logger.exception('Error while acquiring lock')
    else:
        logger.info(server(ctx).release_lock(""))


if __name__ == '__main__':
    cli()
