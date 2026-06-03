#! /usr/bin/env python3
# encoding: utf-8

import collections, argparse, copy
import lxml.etree as et
from Wb import Wb

wb = Wb()

ap = argparse.ArgumentParser(description='Check first recording of entries')
args = ap.parse_args()

erstfassung = collections.defaultdict(set)
erstellungsdatum = collections.defaultdict(set)

for entry, path in wb:
    h = wb.get_headwords(entry)[0]
    erstfassung[h].add(entry.get('Erstfassung'))
    erstellungsdatum[h].add(entry.get('Erstellungsdatum'))

for headword in filter(lambda x: len(erstfassung[x]) > 1, erstfassung.keys()):
    print(f'{headword}\tinconsistent @Erstfassung: {" vs. ".join(sorted(erstfassung[headword]))}')

for headword in filter(lambda x: len(erstellungsdatum[x]) > 1, erstellungsdatum.keys()):
    print(f'{headword}\tinconsistent @Erstellungsdatum: {" vs. ".join(sorted(erstellungsdatum[headword]))}')
