#! /usr/bin/env python3
# encoding: utf-8

import argparse
import lxml.etree as et
import regex as re
from Wb import Wb

wb = Wb(strip=True)

argument_parser = argparse.ArgumentParser(description='Bibliography checks.')
argument_parser.add_argument('-p', '--path',
        action='store_true',
        default=False,
        help='show article URIs instead of headword and snippet')
argument_parser.add_argument('-s', '--subset',
        choices=('all', 'recent'),
        default='recent',
        help='only check a subset of the entries (default: recent)')
argument_parser.add_argument('-f', '--Red-f',
        action='store_true',
        default=False,
        help='only check finally released entries (Red-f)')
arguments = argument_parser.parse_args()

status = ('Red-f',) if arguments.Red_f else ('Red-f', 'Red-f-blockiert', 'Red-f-Sammelbecken', 'Red-2')

_FULL_DATE =          ', [0123]\d\.[01]\d\.[12]\d{3}'          # , 12.03.1967
_YEAR =               ', [12]\d{3}( \[[12]\d{3}\])?'           # , 1975 [1901]
_YEAR_NUMBER =        ', [12]\d\d\d, Nr. [1-9]\d*(–[1-9]\d*)?' # , 1993, Nr. 7
_VOLUME_NUMBER_YEAR = ', \d+(–\d+)?/\d+(–\d+)? \([1]\d{3}\)'   # , 23/18 (1978)
_OPT_ONLINE =         '( \(online\))?'
_OPT_PAGE =           '(, S\. \d+)?'

ILLEGAL_SEQUENCES = (
        # common illegal character sequence
        '&(amp|quot|apos|lt|gt|#)',
        '[<>]',
        #'\d(-|--|—)\d', # URLS!
        '["\']',
        '[^\s]\[',
        #'http',
        'Ztg',
        'VEB',
        'd\.i\.',
        'o\.A\.',
        'o\.O\.',
        'o\.J\.',
        'a\.M\.',
        'u\.a\.',
        'et al\.',
        ' von:',                 # → DIN 1505 (Teil 2): von Nachname, Vorname
        ' de:',                  # → DIN 1505 (Teil 2): von Nachname, Vorname
        'Hrsg',
        'Hg[^\.][^\)][^:]',
        '[zZ]itiert nach',       # → ∅
        'IDS-Archiv',            # → ∅
        'Aufbau[\s\-]+Verl',     # → Aufbau
        'Bermann\s.*Fischer'     # → Bermann-Fischer
        'Buntbuch[\s\-]+Verl',   # → Buntbuch
        'Columbus[\s\-]+Verl',   # → Columbus
        'Drei\s+Masken\s+Verl',  # → Drei Masken
        'Dressler[\s\-]+Verl',   # → Cecilie Dressler
        'Econ[\s\-]+Verl',       # → Econ
        'Eichborn[\s\-]+Verl',   # → Eichborn
        'Elektronische\s+Ressource', # → ∅
        'Fachbuchverl[^a\.]',    # → Fachbuchverlag
        'Falken[\s\-]+Verl',     # → Falken
        'Gegenstandpunkt[\s\-]+Verl' # → Gegenstandpunkt
        'Hanser[\s\-]+Verl',     # → Hanser
        'Hans-Klaus[\s\-]+Verl', # → Hans Klaus
        'Insel[\s\-]+Verl',      # → Insel
        'Kinderbuchverl\.',      # → Kinderbuchverlag
        'König[\s\-]+Verl',      # → König
        'Landwirtschaftsverl[^a]', # → Landwirtschaftsverlag
        'Lebensweiser[\s\-]+Verl', # → Lebensweiser
        'P\.\s?M\.',             # → Peter Moosleitners …
        'Propyläen[\s\-]+Verl',  # → Propyläen
        'Roland[\s\-]+Verl',     # → Roland
        'Suhrkamp[\s\-]+Verl',   # → Suhrkamp
        'Südwest[\s\-]+Verl',    # → Südwest
        'Trotzdem[\s\-]+Verl',   # → Trotzdem
        'Tourist[\s\-]+Verl',    # → Tourist
        'Wegweiser[\s\-]+Verl',  # → Wegweiser
        'Würfel[\s\-]+Verl',     # → Würfel
        'Union[\s\-]+Verl',      # → Union
        'Urania[\s\-]+Verl',     # → Urania
        'Verlag\sTribüne',       # → Tribüne
        'Verlag Volk u',         # → Volk und Gesundheit
        'Verl.+Das Neue Berlin', # → Das Neue Berlin
        'Verl.+Neues Leben',     # → Neues Leben
        'Zeitgeschichte[\s\-]+Verl', # → Zeitgeschichte
        'Zsolnay[\s\-]*[vV]erl'  # → Paul Zsolnay
        'Frankfurt am Main',     # → Frankfurt a. M.
        ';\s',                   # exception: URLs, therefore \s
        '[…!?\.,:][…\.,]',
        '\s[:]',
        '[^\p{L}]ders\.[^\]]',   # → [ders.]
        '[^\p{L}]dies\.[^\]]',   # → [dies.]
        'u\.\s*a\.[^\]]',        # → [u. a.]
        '[^\[]u\.\s*a\.',        # → [u. a.]
        'o\.\s*A\.[^\]][^:]',    # → [o. A.]:
        'o\.\s*O\.[^\]]',        # → [o. O.]
        'o\.\s*J\.[^\]]',        # → [o. J.]
        'o\.\s*S\.',             # o. S. → ∅
        'N\.\s*N\.',             # N. N. → [o. A.]
        'S\.\s*\d+.*S\.',
        'S\.\d',
        'Seite\s*\d+\s*$',
        'S\.\s+\d\d\d\d\d',
        '\.$',
)

for entry, path in wb:

    if entry.get('Status') in status:

        if arguments.subset == 'recent' and not wb.recently_modified(path):
            continue

        for i in entry.findall('.//%(Beleg)s//%(Titel)s' % wb.TAGS):
            t = wb.text(i)
            if len(t.split()) == 1 and '_' in t:
                wb.report(entry, path, f'bogus //Titel: "{t}"', not(arguments.path))
        
        for i in entry.findall('.//%(Kurztitel)s' % wb.TAGS):
            t = wb.text(i)
            if t != '':
                titel = i.getparent().findall('.//%(Titel)s' % wb.TAGS)
                if not titel:
                    wb.report(entry, path, 'no title', not(arguments.path))
                elif wb.text(titel[0]).startswith(t):
                    pass
                elif wb.text(titel[0]).endswith(t):
                    pass
                else:
                    wb.report(entry, path, 'mismatch: '+t, not(arguments.path))

        for i in entry.findall('.//%(Lesart)s//%(Beleg)s/%(Fundstelle)s' % wb.TAGS):
            for u in i.findall('.//%(URL)s' % wb.TAGS):
                u.text = 'URL'
                for _u in u:
                    _u.text = URL
                    _u.tail = URL
            
            t = wb.text(i)

            if len(t.split()) < 2:
                wb.report(entry, path, '(too?) short bibl: '+t, not(arguments.path))

            elif len(i) == 0:
                
                # periodicals with day resolution dates
                match = re.search('(?P<head>.*),\s+(?P<date>[0123]\d\.[01]\d\.[12]\d\d\d)\s*(?P<tail>.*)', t)
                if match is not None and not match.group('head') in wb.PERIODICALS:
                    wb.report(entry, path,
                            'unknown periodical: '+match.group('head'),
                            not(arguments.path))
                if match is not None and match.group('tail') not in ('', '(online)'):
                    wb.report(entry, path,
                            'trailing data: '+match.group('tail'),
                            not(arguments.path))

            for m in ILLEGAL_SEQUENCES:
                match = re.search(m, t, re.UNICODE)
                if match is not None:
                    wb.report(entry, path,
                            'illegal sequence: '+t+' --> '+str(match.re),
                            not(arguments.path))

if arguments.path:
    print()
