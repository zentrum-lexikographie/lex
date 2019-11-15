import click
import csv

import zdl.article

_csv_columns = ('name', 'hidx', 'pos', 'gen', 'type', 'status', 'source', 'id', 'path')


def read(articles_dir, articles):
    csv_out = csv.writer(click.get_text_stream('stdout'))
    for p in articles:
        path = p.relative_to(articles_dir).as_posix()
        for (document, article) in zdl.article.parse(p):
            for record in zdl.article.metadata(article):
                record['path'] = path
                csv_out.writerow([record.get(k, '') for k in _csv_columns])
