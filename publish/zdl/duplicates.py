from collections import defaultdict

import csv
import click

import zdl.article
from zdl.util import article_progress, icu_sortkey


@click.option(
    '-o', '--out', 'csv_file',
    default='-',
    help='destination file for CSV data of duplicates (stdout by default)',
    type=click.File('w')
)
@click.argument(
    'article_dirs',
    nargs=-1,
    type=click.Path(exists=True, file_okay=False, resolve_path=True)
)
@click.command()
def cli(article_dirs, csv_file):
    """Detects duplicate headwords defined in XML documents in the given
    ARTICLE_DIRS."""

    # index all XML documents by lemma and hidx
    lemma_index = defaultdict(lambda: defaultdict(set))
    with article_progress(article_dirs, 'Red-f -> Duplicates') as articles:
        for (f, p) in articles:
            for (document, article) in zdl.article.parse(f):
                for md in zdl.article.metadata(article):
                    if md['status'] == "Red-f":
                        lemma_index[md['name']][md.get('hidx') or ''].add(p)

    # Homographs match their equivalent without a hidx
    for lemma, homographs in lemma_index.items():
        if '' in homographs:
            for hidx, files in homographs.items():
                if hidx != '':
                    homographs[''].update(files)

    # invert lemma index, unifying duplicates with multiple surface forms
    duplicates = defaultdict(set)
    for lemma, homographs in lemma_index.items():
        for hidx, files in homographs.items():
            if len(files) > 1:
                # tuples can be hashed and thus function as keys
                files = tuple(sorted(files))
                duplicates[files].add(lemma)

    if len(duplicates) == 0:
        return

    # output sorted, padded CSV report of duplicate headwords
    max_files = max([len(files) for files in duplicates.keys()])
    max_lemmata = max([len(lemmata) for lemmata in duplicates.values()])

    def pad(l, length, pad=''):
        return tuple(l) + ((pad, ) * (length - len(l)))

    duplicates = sorted([
        (pad(sorted(lemmata, key=icu_sortkey), max_lemmata) + pad(sorted(dup), max_files))
        for dup, lemmata in duplicates.items()
    ], key=icu_sortkey)

    csv_file = csv.writer(csv_file)
    csv_file.writerow(
        pad([], max_lemmata, 'Lemma') + pad([], max_files, 'ID')
    )
    for dup in duplicates:
        csv_file.writerow(dup)


if __name__ == '__main__':
    cli()
