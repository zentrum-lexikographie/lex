from collections import defaultdict
import csv
from pathlib import Path

import click
import requests

import zdl.article
import zdl.util

cab_query_url = 'https://kaskade.dwds.de/dstar/cab/query'
# cab_query_url = 'http://data.dwds.de:9096/query'


def lemma(forms):
    data = {
        'fmt': 'json', 'a': 'norm1', 'tokenize': '0',
        'q': ' '.join(forms)
    }
    r = requests.post(cab_query_url, data=data)
    r.raise_for_status()
    r = r.json()
    lemmata = dict()
    for result in r.get('body', []):
        for token in result.get('tokens', []):
            surface_form = token.get('text', '')
            lemma = token.get('moot', {}).get('lemma', '')
            if len(surface_form) > 0 and len(lemma) > 0:
                lemmata[surface_form] = lemma
    return lemmata


@click.argument(
    'article_dirs',
    nargs=-1,
    type=click.Path(exists=True, file_okay=False, resolve_path=True)
)
@click.option(
    '-o', '--out', 'csv_file',
    default='-',
    help='destination file for CSV data of lemmata (stdout by default)',
    type=click.File('w')
)
@click.command()
def cli(article_dirs, csv_file):
    lemma_index = defaultdict(set)

    for article_dir in (article_dirs or []):
        article_dir = Path(article_dir)
        for f in zdl.article.files(article_dir):
            p = f.relative_to(article_dir).as_posix()
            for (document, article) in zdl.article.parse(f):
                for md in zdl.article.metadata(article):
                    lemma_index[md['name']].add(p)
    csv_file = csv.writer(csv_file)
    lemmata = sorted(
        lemma_index.keys(), key=zdl.util.collator.getSortKey
    )
    for lemma in lemmata:
        csv_file.writerow([lemma] + list(lemma_index.get(lemma)))


if __name__ == '__main__':
    cli()
