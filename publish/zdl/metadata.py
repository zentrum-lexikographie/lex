from pathlib import Path

import click
import csv

import zdl.article

_csv_columns = ('name', 'hidx', 'pos', 'gen', 'type', 'status', 'source', 'id', 'path')


@click.command()
@click.argument('articles',
                required=True,
                type=click.Path(exists=True, file_okay=False, resolve_path=True))
def main(articles):
    articles = Path(articles)
    csv_out = csv.writer(click.get_text_stream('stdout'))
    for p in zdl.article.files(articles):
        path = p.relative_to(articles).as_posix()
        for (document, article) in zdl.article.parse(p):
            for record in zdl.article.metadata(article):
                record['path'] = path
                csv_out.writerow([record.get(k, '') for k in _csv_columns])


if __name__ == '__main__':
    main()
