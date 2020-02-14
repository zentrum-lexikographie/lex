from pathlib import Path
from collections import defaultdict

import csv
import click

import zdl.article


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
def cli(csv_file, article_dirs):
    """Detects duplicate headwords defined in XML documents in the given
    ARTICLE_DIRS."""

    # index all XML documents by lemma/headword
    lemma_index = defaultdict(set)
    for article_dir in (article_dirs or []):
        article_dir = Path(article_dir)
        for f in zdl.article.files(article_dir):
            p = f.relative_to(article_dir).as_posix()
            for (document, article) in zdl.article.parse(f):
                for md in zdl.article.metadata(article):
                    if md['status'] == "Red-f":
                        lemma_index[md['headword']].add(p)

    # invert lemma index, unifying duplicates with multiple surface forms
    duplicates = defaultdict(list)
    for lemma, files in lemma_index.items():
        if len(files) > 1:
            # tuples can be hashed and thus function as keys
            files = tuple(sorted(files))
            duplicates[files].append(lemma)

        # check marked homographs against identical forms *without* index mark
        if '#' in lemma:
            baseform = lemma.split('#', 1)[0]
            if baseform in lemma_index:
                files = tuple(sorted(lemma_index[baseform]))
                duplicates[files].append(lemma)

    if len(duplicates) == 0:
        return

    # output sorted, padded CSV report of duplicate headwords
    max_files = max([len(files) for files in duplicates.keys()])
    max_lemmata = max([len(lemmata) for lemmata in duplicates.values()])

    def pad(l, length, pad=''):
        return tuple(l) + ((pad, ) * (length - len(l)))

    duplicates = sorted([
        (pad(lemmata, max_lemmata) + pad(dup, max_files))
        for dup, lemmata in duplicates.items()
    ])
    csv_file = csv.writer(csv_file)
    csv_file.writerow(
        pad([], max_lemmata, 'Lemma') + pad([], max_files, 'ID')
    )
    for dup in duplicates:
        csv_file.writerow(dup)


if __name__ == '__main__':
    cli()
