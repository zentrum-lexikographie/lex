from collections import defaultdict
from statistics import mean
import csv
import click

import zdl.article
from zdl.util import article_files, icu_sortkey

status = [
    'Lex-Wiedervorlage',  # x
    'Red-2',  # x
    'Red-f',  # x
    'Artikelrumpf',
    'Lex-in_Arbeit',
    'Lex-zur_Abgabe',
    'Lex-kommentiert',
    'Lex-zurückgestellt',
    'Red-0',
    'Red-1',
    'Red-2-zurückgewiesen',
    'Red-ex',
    'Red-f-zurückgewiesen',
    'wird_Vollartikel',
    'wird_gestrichen'
]
status = dict([(s, i) for i, s in enumerate(status)])
num_status = len(status)

corpus_sizes = (315189235, 6336726411, 3103432379)


def candidate_sortkey(index_entry):
    status_minimal = num_status
    status_other = -1
    has_minimal = False
    has_other = False

    headword, articles = index_entry
    freq = 0
    for md, p, f, freq in articles:
        freq = mean(map(lambda f: -f[0] / f[1], zip(freq, corpus_sizes)))
        md_status = status.get(md['status'])
        if md_status is None:
            continue
        if md['type'] == 'Minimalartikel':
            status_minimal = min(md_status, status_minimal)
            has_minimal = True
        else:
            status_other = max(md_status, status_other)
            has_other = True
    duplicate = 0
    if has_minimal and not has_other:
        duplicate = -2
    elif has_minimal:
        duplicate = -1

    return (
        duplicate, status_minimal, freq, status_other, icu_sortkey(headword)
    )


@click.option(
    '-f', '--frequencies', 'freq_csv_file',
    default='lexdb.csv',
    help='CSV data file with corpus frequencies',
    type=click.File('r')
)
@click.option(
    '-o', '--out', 'csv_file',
    default='-',
    help='destination file for CSV data of edit candidates (stdout by default)',
    type=click.File('w')
)
@click.argument(
    'article_dirs',
    nargs=-1,
    type=click.Path(exists=True, file_okay=False, resolve_path=True)
)
@click.command()
def cli(csv_file, freq_csv_file, article_dirs):
    frequencies = dict([
        (record[0], tuple(map(int, record[2:5])))
        for record in csv.reader(freq_csv_file)
    ])
    lemma_index = defaultdict(list)
    for f, p in article_files(article_dirs):
        for (document, article) in zdl.article.parse(f):
            for md in zdl.article.metadata(article):
                lemma_index[md['headword']].append(
                    (md, p, f, frequencies.get(md['name'], (0, 0, 0)))
                )

    lemma_index_items = sorted(
        lemma_index.items(),
        key=candidate_sortkey
    )
    csv_file = csv.writer(csv_file)
    csv_file.writerow(['Lemma', 'Kern-Basis', 'Zeitungen', 'Web', 'Typ#Status*'])
    for headword, articles in lemma_index_items:
        freq, *_ = [freq for _, _, _, freq in articles]
        articles = sorted(set([(md['type'], md['status']) for md, *_ in articles]))
        articles = ['#'.join([t, s]) for t, s in articles]
        csv_file.writerow([headword] + list(freq) + list(articles))


if __name__ == '__main__':
    cli()
