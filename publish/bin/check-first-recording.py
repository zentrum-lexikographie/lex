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
zeitstempel = collections.defaultdict(set)

for entry, path in wb:
    h = wb.get_headwords(entry)[0]
    erstfassung[h].add(entry.get('Erstfassung'))
    erstellungsdatum[h].add(entry.get('Erstellungsdatum'))
    zeitstempel[h].add(entry.get('Zeitstempel'))

for headword in filter(lambda x: len(erstfassung[x]) > 1, erstfassung.keys()):
    print(f'{headword}\tinconsistent @Erstfassung: {" vs. ".join(sorted(erstfassung[headword]))}')

for headword, dates in erstellungsdatum.items():
    if len(dates) > 1:
        print(f'{headword}\tinconsistent @Erstellungsdatum: {" vs. ".join(sorted(dates))}')
    #if 'DWDS' in erstfassung[headword] and not min(zeitstempel[headword]) == min(dates):
    #    print(f'{headword}\tno entry with @Zeitstempel == @Erstellungsdatum == {min(dates)}')
