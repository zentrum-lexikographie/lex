#! /usr/bin/env python3
# encoding: utf-8

import argparse
import lxml.etree as et
from Wb import Wb

argument_parser = argparse.ArgumentParser(description='Hyphenation checks.')
argument_parser.add_argument('-p', '--path',
        action='store_true',
        default=False,
        help='show article URIs instead of headword and snippet')
argument_parser.add_argument('-s', '--subset',
        choices=('all', 'recent'),
        default='recent',
        help='only check a subset of the entries (default: recent)')
arguments = argument_parser.parse_args()

wb = Wb()

for entry, path in wb:
    for orth in entry.findall('.//%(Schreibung)s' % wb.TAGS):
        hyphenation = orth.get('Trennung')
        text = wb.text(orth)
        if orth.get('Typ') not in (None, 'U_CH', 'U_AT') and hyphenation is not None:
            wb.report(entry, path, 'Hyphenation on marked headword', verbose=not(arguments.path))
        if hyphenation is None:
            pass
        elif text.replace('-', '') != hyphenation.replace('-', ''):
            wb.report(entry, path, f'Mismatched string after hyphenation: "{text}" vs. "{hyphenation}"', verbose=not(arguments.path))
        elif len(text) > 1 and hyphenation[1] == '-' and not text[1] == '-' and text[0].isalpha():
            wb.report(entry, path, f'Bogus first syllable in "{hyphenation}"')


if arguments.path:
    print()
