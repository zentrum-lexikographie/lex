#! /usr/bin/env python3
# encoding: utf-8

import collections, functools
import click
from Wb import Wb

def _hidx_sort(x1, x2):
    if x1 is None:
        return -1
    elif x2 is None:
        return 1
    else:
        return -1 if x1 < x2 else 1

@click.command('duplicates')
@click.version_option('0.0.1', prog_name='duplicates')
def find_duplicates(wb=Wb(strip=True)):

    lemma_index = collections.defaultdict(list)

    for entry, path in wb:
        if entry.get('Status') == 'Red-f':
            for headword in wb.get_headwords(entry):
                lemma, hidx = headword.split('#', 1) \
                        if '#' in headword else (headword, None)

                lemma_index[lemma].append(hidx if hidx is None else int(hidx))

    for lemma, hidx_list in lemma_index.items():
    
        if hidx_list == [None]:
            # default case
            pass
        elif len(hidx_list) > 1 and sorted(hidx_list, key=functools.cmp_to_key(_hidx_sort)) == list(range(1, len(hidx_list)+1)):
            # homopgraphs, neatly enumerated (1..n) where n > 1
            pass
        else:
            click.echo(f'{wb.COLORS["red"]}{lemma}{wb.COLORS["reset"]}\t{sorted(hidx_list, key=functools.cmp_to_key(_hidx_sort))}')


if __name__ == '__main__':
    find_duplicates()
