from collections import defaultdict
import csv
import re
import urllib.parse

import click
import requests

query_url = 'http://kaskade.dwds.de/dstar/%s/lexdb/export.perl'


def query(corpus,
          select='*', frm='lex', where=None,
          groupby=None, orderby=None,
          limit=10, offset=None):

    url = query_url % urllib.parse.quote(corpus)
    data = {
        'fmt': 'json',
        'select': select,
        'from': frm,
        'where': where,
        'groupby': groupby,
        'orderby': orderby,
        'limit': limit,
        'offset': offset
    }
    data = dict(filter(lambda kv: kv[1] is not None, data.items()))
    r = requests.post(url, data)
    r.raise_for_status()
    r = r.json()
    names = r['names']
    return {
        'query': r['sql'],
        'total': r['nrows'],
        'result': list(map(lambda r: dict(zip(names, r)), r['rows']))
    }


def _remove_quotes(s):
    return re.sub('"', '', s)


def frequencies(corpus, lemmata):
    lemmata = list(map(lambda l: '"%s"' % _remove_quotes(l), lemmata))
    r = query(
        corpus,
        select='l, SUM(f) as f',
        where=('l in (%s)' % ', '.join(lemmata)),
        groupby='l',
        limit=len(lemmata)
    )
    return dict(map(lambda r: (r['l'], int(r['f'])), r.get('result', [])))


@click.option(
    '-o', '--out', 'lemma_out_csv_file',
    default='-',
    help='destination file for CSV data of lemmata (stdout by default)',
    type=click.File('w')
)
@click.argument(
    'lemma_in_csv_file',
    default='-',
    type=click.File('r')
)
@click.command()
def cli(lemma_in_csv_file, lemma_out_csv_file):
    corpora = ['kernbasis']  # ['kernbasis', 'zeitungen', 'ibk_web_2016c']
    limit = 10

    lemma_in_csv_file = list(csv.reader(lemma_in_csv_file))
    lemmata = list(set([record[1] for record in lemma_in_csv_file]))
    lemma_freqs = defaultdict(dict)
    for offset in range(0, len(lemmata), limit):
        batch = lemmata[offset:(offset + limit)]
        for corpus in corpora:
            freqs = frequencies(corpus, batch)
            for lemma in batch:
                lemma_freqs[lemma][corpus] = freqs.get(lemma, 0)

    lemma_out_csv_file = csv.writer(lemma_out_csv_file)
    for record in lemma_in_csv_file:
        lemma = record[1]
        lemma_out_csv_file.writerow(
            record[0:2] + [
                lemma_freqs[lemma][c] for c in corpora
            ] + record[2:]
        )


if __name__ == '__main__':
    cli()
