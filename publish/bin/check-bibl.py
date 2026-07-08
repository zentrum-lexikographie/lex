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
argument_parser.add_argument('-g', '--good-examples',
        action='store_true',
        default=False,
        help='do not skip automatically added good examples')
arguments = argument_parser.parse_args()

status = ('Red-f',) if arguments.Red_f else ('Red-f', 'Red-f-blockiert', 'Red-f-Sammelbecken', 'Red-2')

_FULL_DATE =          r', [0123]\d\.[01]\d\.[12]\d{3}'          # , 12.03.1967
_YEAR =               r', [12]\d{3}( \[[12]\d{3}\])?'           # , 1975 [1901]
_YEAR_NUMBER =        r', [12]\d\d\d, Nr. [1-9]\d*(–[1-9]\d*)?' # , 1993, Nr. 7
_VOLUME_NUMBER_YEAR = r', \d+(–\d+)?/\d+(–\d+)? \([1]\d{3}\)'   # , 23/18 (1978)
_OPT_ONLINE =         r'( \(online\))?'
_OPT_PAGE =           r'(, S\. \d+)?'

ILLEGAL_SEQUENCES = (
        # common illegal character sequences
        r'&(amp|quot|apos|lt|gt|#)',
        r'[<>]',
        #r'\d(-|--|—)\d', # URLS!
        r'["\'“”„‚‘´]',
        r'[^\s]\[',
        r'\[\d\d\d\d\]\s\d\d\d\d', # [1930] 2001 → 2001 [1930]
        #r'http',
        r'Ztg',
        r'VEB',
        r'E-Book\.',
        r'\s\(c\)\s',
        r'd\.i\.',
        r'o\.A\.',
        r'o\.O\.',
        r'o\.J\.',
        r'a\.M\.',
        r'u\.a\.',
        r'et\s+al\.',
        r'\s(von|vom|zu|zum|zur):', # → DIN 1505 (Teil 2): von|… Nachname, Vorname
        r'\s(de|den):',             # → DIN 1505 (Teil 2): de|… Nachname, Vorname
        r'Hrsg',
        #r'Verl\.',
        r'Hg[^\.]',
        r'Hg\.[^\)]',
        r'Hg\.\)[^:]',
        r'[zZ]itiert nach',       # → ∅
        r'IDS-Archiv',            # → ∅
        r'Aktuelles Lexikon 1974-',
        r'Aufbau[\s\-]+Verl',     # → Aufbau
        r'Aufbau[\s\-]+Taschenb.+[vV]erl', # → Aufbau Taschenbuch
        r'Berlin\s+Verl.+d.+Nation', # → Verlag der Nation
        r'Bergstadtverl\.'        # → Bergstadtverlag
        r'Bermann\s.*Fischer',    # → Bermann-Fischer
        r'Buchverl\.',            # → UFA-Buchverlag
        r'Buntbuch[\s\-]+Verl',   # → Buntbuch
        r'Columbus[\s\-]+Verl',   # → Columbus
        r'Desotron[\s\-]+Verl',   # → Desotron
        r'Dietz[\s\-]+Verl',      # → Dietz
        r'Drei\s+Masken\s+Verl',  # → Drei Masken
        r'Dressler[\s\-]+Verl',   # → Cecilie Dressler
        r'Droste[\s\-]Verl',      # → Droste
        r'Druck\.-',              # → Schweizer Druck- und Verlagshaus
        r'Dt\.[-\s]Taschenb.+[Vv]erl', # → dtv
        r'(Dt\.|Deut).+Verl.+[aA]nst', # → DVA
        r'(Dt\.|Deut).+Verl.+(der|d\.).+Wiss', # → DVW
        r'\sDTV',                 # → dtv
        r'Econ[\s\-]+Verl',       # → Econ
        r'Eichborn[\s\-]+Verl',   # → Eichborn
        r'Elektronische\s+Ressource', # → ∅
        r'Enke[\s\-]Verl',        # → Enke
        r'Europ.+Verl\.',         # → Europäische Verlagsanstalt
        r'Europ.+Verl.+Anst',     # → Europäische Verlagsanstalt
        r'Fachbuchverl[^a\.]',    # → Fachbuchverlag
        r'Falken[\s\-]+Verl',     # → Falken
        r'Fischer[\s\-]+Taschenb.+[vV]erl', # → Fischer Taschenbuch
        r'FN[\s\-]+Verl',         # → FN (FN-Verlag der dt. Reiterl. Vereinigung)
        r'Frauenbuchverl\.',      # → Frauenbuchverlag
        r'Gegenstandpunkt[\s\-]+Verl', # → Gegenstandpunkt
        r'Greifenverl\.',         # → Greifenverlag
        r'Hanser[\s\-]+Verl',     # → Hanser
        r'Hans-Klaus[\s\-]+Verl', # → Hans Klaus
        r'Hanseat\.',             # → Hanseatische Verlagsanstalt # HAVA
        r'Hippokrates[\s\-]+Verl',# → Hippokrates
        r'Insel[\s\-]+Verl',      # → Insel
        r'Kinderbuchverl\.',      # → Kinderbuchverlag
        r'König[\s\-]+Verl',      # → König
        r'Landwirtschaftsverl[^a]', # → Landwirtschaftsverlag
        r'Lebensweiser[\s\-]+Verl', # → Lebensweiser
        r'Militärverl\.',         # → Militärverlag
        r'Mitteld.+Verl\.',       # → Mitteldeutscher Verlag
        r'Orania[\s\-]+Verl',     # → Orania
        r'P\.\s?M\.',             # → Peter Moosleitners …
        r'Propyläen[\s\-]+Verl',  # → Propyläen
        r'Ratgeberverl\.',        # → Bertelsmann Ratgeber
        r'Roland[\s\-]+Verl',     # → Roland
        r'Rotbuch[\s\-]+Verl',    # → Rotbuch
        r'Ruhrkrimi[\s\-]?[vV]erl', # → Ruhrkrimi
        r'Safari[\s\-]+Verl',     # → Safari
        r'Schweiz.+Verl\.',       # → Schweizer Verlagshaus / Schweizer Spiegel
        r'Siedler[\s\-]+Verl'     # → Siedler
        r'Silberburg[\s\-]+Verl', # → Silberburg
        r'Sportverl\.',           # → Sortverlag
        r'Standesamtwesen',       # → Verlag für Standesamtswesen
        r'Suhrkamp[\s\-]+Verl',   # → Suhrkamp
        r'Südwest[\s\-]+Verl',    # → Südwest
        r'-Taschenbuch',          # → Taschenbuch
        r'Tcetum[\s\-]+Verl',     # → Tectum
        r'Tourist[\s\-]+Verl',    # → Tourist
        r'Trotzdem[\s\-]+Verl',   # → Trotzdem
        r'Wegweiser[\s\-]+Verl',  # → Wegweiser
        r'Würfel[\s\-]+Verl',     # → Würfel
        r'Ullstein[\s\-]+Taschenb.+[vV]erl', # → Ullstein Taschenbuch
        r'Union[\s\-]+Verl',      # → Union
        r'Urania[\s\-]+Verl',     # → Urania
        r'Verl.+Das\s+Neue\s+Berlin', # → Das Neue Berlin
        r'Verl.+Die\s+Wirt',      # → Die Wirtschaft
        r'Verl\.+(f\.|für)\s.+Frau', # → Verlag für die Frau
        r'Verl.+(f\.|für)\s+Sozialwiss', # → VS
        r'Verl.+f\..+Handel',     # → Verlag für Handel und Industrie
        r'Verl.+Gabler',          # → Gabler
        r'Verl.+Haus\s+zum\s+Falken', # → → Haus zum Falken
        r'Verl.+Neuer Weg',       # → Neuer Weg
        r'Verl.+Neues Leben',     # → Neues Leben
        r'Verl.+Tribüne',         # → Tribüne
        r'(Verl\.|Verlag)\s+Volk\s+u', # → Volk und (Gesundheit|Welt)
        r'Weltbild[\s\-]+Verl.+\d{4}', # → Weltbild
        r'Wiss.+Verl.+[gG]esell', # → WVG
        r'Wunderlich[\s\-]+Verl', # → Wunderlich
        r'Zeitgeschichte[\s\-]+Verl', # → Zeitgeschichte
        r'Zsolnay[\s\-]*[vV]erl', # → Paul Zsolnay
        r'Frankfurt am Main',     # → Frankfurt a. M.
        r';\s',                   # exception: URLs, therefore \s
        r'[…!?\.,:][…\.,]',
        r'\s[:]',
        r'[^\p{L}]ders\.[^\]]',   # → [ders.]
        r'[^\p{L}]dies\.[^\]]',   # → [dies.]
        r'u\.\s*a\.[^\]]',        # → [u. a.]
        r'[^\[]u\.\s*a\.',        # → [u. a.]
        r'o\.\s*A\.[^\]][^:]',    # → [o. A.]:
        r'o\.\s*O\.[^\]]',        # → [o. O.]
        r'o\.\s*J\.[^\]]',        # → [o. J.]
        r'o\.\s*S\.',             # o. S. → ∅
        r'N\.\s*N\.',             # N. N. → [o. A.]
        r'S\.\s*\d+.*S\.',
        r'S\.\d',
        r'Seite\s*\d+\s*$',
        r'S\.\s+\d\d\d\d\d',
        r'\.$',
        r'\[\d\d\d\d\]\s+\d\d\d\d', # [1988] 2007 → 2007 [1988]
        r'(\d\d\d\d)\s+\[\1]',    # 1988 [1988] → 1988
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

        citations = './/%(Beleg)s/%(Fundstelle)s' if arguments.good_examples else './/%(Lesart)s//%(Beleg)s/%(Fundstelle)s'
        for i in entry.findall(citations % wb.TAGS):
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
