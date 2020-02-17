from collections import defaultdict
import csv

import click
import requests

import zdl.article
import zdl.util

cab_query_url = 'https://kaskade.dwds.de/dstar/cab/query'
# cab_query_url = 'http://data.dwds.de:9096/query'


def lemmatize(forms):
    data = {
        'fmt': 'json',
        'a': 'lemma1',  # 'default1' # 'norm1',
        'tokenize': '0',
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
    with zdl.util.article_progress(article_dirs, 'WB -> Lemma') as articles:
        for f, p in articles:
            for (document, article) in zdl.article.parse(f):
                for md in zdl.article.metadata(article):
                    lemma_index[md['name']].add(p)
    csv_file = csv.writer(csv_file)
    lemmata = sorted(lemma_index.keys(), key=zdl.util.icu_sortkey)
    for i in range(0, len(lemmata), 1000):
        batch = lemmata[i:i + 1000]
        cab_lemmata = lemmatize(batch)
        for lemma in batch:
            csv_file.writerow(
                [lemma, cab_lemmata.get(lemma, '')] + list(lemma_index.get(lemma))
            )


if __name__ == '__main__':
    cli()
