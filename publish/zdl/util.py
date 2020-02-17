from collections.abc import Iterable
from pathlib import Path

import click
from icu import Collator, Locale

import zdl.article

collator = Collator.createInstance(Locale('de_DE.UTF-8'))


def icu_sortkey(v):
    if isinstance(v, str):
        return collator.getSortKey(v)
    elif isinstance(v, Iterable):
        return tuple(map(collator.getSortKey, v))
    else:
        return collator.getSortKey(v)


def article_files(article_dirs):
    for d in (article_dirs or []):
        d = Path(d)
        for f in zdl.article.files(d):
            yield (f, f.relative_to(d).as_posix())


def article_progress(article_dirs, label):
    num_files = sum([1 for f in article_files(article_dirs)])
    return click.progressbar(
        article_files(article_dirs),
        length=num_files,
        width=0, label=label
    )
