from pathlib import Path
import click

import zdl.metadata
import zdl.mysql
import zdl.qa


def article_files(articles_dir):
    return zdl.article.files(Path(articles_dir))


def article_progress(articles_dir, label):
    num_articles = sum([1 for f in article_files(articles_dir)])
    return click.progressbar(
        article_files(articles_dir),
        length=num_articles,
        width=0, label=label
    )


@click.group()
@click.argument('articles-dir',
                required=True,
                type=click.Path(exists=True, file_okay=False, resolve_path=True))
@click.pass_context
def cli(ctx, articles_dir):
    ctx.ensure_object(dict)
    ctx.obj['articles_dir'] = articles_dir


@cli.command()
@click.pass_obj
def metadata(obj):
    articles_dir = obj['articles_dir']
    zdl.metadata.read(Path(articles_dir), article_files(articles_dir))


@cli.command()
@click.pass_obj
def qa(obj):
    with article_progress(obj['articles_dir'], 'Red-1 -> Red-2') as articles:
        zdl.qa.perform(articles)


@cli.command()
@click.pass_obj
def wb2db(obj):
    with article_progress(obj['articles_dir'], 'Red-f -> MySQL') as articles:
        zdl.mysql.wb2db(articles)


if __name__ == '__main__':
    cli()
