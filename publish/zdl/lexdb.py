from collections import defaultdict
import csv
from random import shuffle
import re
import sqlite3
import urllib.parse

import click
import requests


from zdl.util import progress

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


def local_frequencies(corpus, lemmata):
    lemmata = list(map(lambda l: '"%s"' % _remove_quotes(l), lemmata))
    with sqlite3.connect('/home/middell/repositories/lex/data/lexdb/%s-lexdb.sqlite' % corpus) as con:
        c = con.execute(
            'select l, sum(f) as f from lex where l in (%s) group by l limit %s' %
            (', '.join(lemmata), str(len(lemmata)))
        )
        return dict(c.fetchall())


@click.option(
    '-i', '--in', 'lemma_in_csv_file',
    default='cab.csv',
    type=click.File('r')
)
@click.option(
    '-o', '--out', 'lemma_out_csv_file',
    default='lexdb.csv',
    help='destination file for CSV data of lemmata (stdout by default)',
    type=click.File('w')
)
@click.command()
def cli(lemma_in_csv_file, lemma_out_csv_file):
    corpora = ['kernbasis', 'zeitungen', 'ibk_web_2016c']
    limit = 1000

    lemma_in_csv_file = list(csv.reader(lemma_in_csv_file))
    lemmata = list(set([record[1] for record in lemma_in_csv_file]))

    # distribute LexDB query load, e.g. not all articles in one batch
    shuffle(lemmata)

    lemma_freqs = defaultdict(dict)
    with progress(range(0, len(lemmata), limit), 'Frequencies') as offsets:
        for offset in offsets:
            batch = lemmata[offset:(offset + limit)]
            for corpus in corpora:
                freqs = local_frequencies(corpus, batch)
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
