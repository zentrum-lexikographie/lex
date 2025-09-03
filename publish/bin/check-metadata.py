#! /usr/bin/env python3
# encoding: utf-8

import argparse
import lxml.etree as et
from Wb import Wb

argument_parser = argparse.ArgumentParser(description='Metadata checks.')
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
wdg = set( [l.strip() for l in open('share/WDG-lemmas.txt', encoding='utf-8') ] )
duden = set( [l.strip() for l in open('share/Duden-lemmas.txt', encoding='utf-8') ] )

def is_polysemous(e):
    visible = [ wb.is_visible(l) for l in e.findall('.//%(Lesart)s' % wb.TAGS) ]
    return visible.count(True) > 1

def get_cits(e):
    cits = [_c for _c in et.ETXPath('.//%(Lesart)s/%(Verwendungsbeispiele)s/%(Beleg)s' % wb.TAGS)(e) if wb.is_visible(_c) ]
    return cits

def get_max_cits(e):
    # for crazy MWA-Basisartikel
    n_cits = [0,]
    for _l in e.findall('.//%(Lesart)s' % wb.TAGS):
        _n = [ 1 if wb.is_visible(_c) else 0 for _c in et.ETXPath('./%(Verwendungsbeispiele)s/%(Beleg)s' % wb.TAGS)(_l) ]
        n_cits.append(sum(_n))
    return max(n_cits)

for entry, path in wb:

    if arguments.subset == 'recent' and not wb.recently_modified(path):
        continue

    headwords = wb.get_headwords(entry)

    # Wahrig … special

    if entry.get('Autor') == 'wahrig' and not 'Wahrig' in entry.get('Quelle'):
        wb.report(entry, path, 'unmatched @Quelle for @Autor=wahrig', not(arguments.path))
    
    # Duden (1999)
    
    if entry.get('Zeitstempel') == '1999-01-01' and not entry.get('Quelle') == 'Duden_1999':
        wb.report(entry, path, '@Zeitstempel vs. @Quelle (Duden_1999)', not(arguments.path))
    if not entry.get('Zeitstempel') == '1999-01-01' and entry.get('Quelle') == 'Duden_1999':
        wb.report(entry, path, '@Zeitstempel vs. @Quelle (Duden_1999)', not(arguments.path))
    
    if entry.get('Erstfassung') == 'Duden_1999' and not entry.get('Erstellungsdatum') == '1999-01-01':
        wb.report(entry, path, '@Erstfassung vs. @Erstellungsdatum (Duden_1999)', not(arguments.path))
    if not entry.get('Erstfassung') == 'Duden_1999' and entry.get('Erstellungsdatum') == '1999-01-01':
        wb.report(entry, path, '@Erstfassung vs. @Erstellungsdatum (Duden_1999)', not(arguments.path))
    if not entry.get('Erstfassung') == 'Duden_1999' and entry.get(wb.TAGS['xml:id']).startswith('D99'):
       wb.report(entry, path, '@xml:id mismatch (Duden_1999)', not(arguments.path))
    if True in [ True if l in duden else False for l in headwords ] and not entry.get('Erstfassung') in ('Duden_1999', 'WDG', 'WDW_2011'):
        wb.report(entry, path, '@Erstfassung!=Duden_1999 but in Duden-Kauf', not(arguments.path))

#    if entry.get('Quelle') == 'Duden_1999':
#        for g in entry.findall('.//%(Grammatik)s' % wb.TAGS):
#            if len(wb.text(g).split()) > 3 and g.getparent().get('Quelle') is None:
#                wb.report(entry, path, '//Grammatik w/o @Quelle in Duden entry')
    # WDG

    if entry.get('Erstfassung') == 'WDG' and not entry.get('Erstellungsdatum') in ('1967-01-01', '1969-01-01', '1974-01-01', '1976-01-01', '1977-01-01'):
        wb.report(entry, path, '@Erstfassung vs. @Erstellungsdatum (WDG)', not(arguments.path))
    if not entry.get('Erstfassung') == 'WDG' and entry.get('Erstellungsdatum') in ('1967-01-01', '1969-01-01', '1974-01-01', '1976-01-01', '1977-01-01'):
        wb.report(entry, path, '@Erstfassung vs. @Erstellungsdatum (WDG)', not(arguments.path))
    #if entry.get('Erstfassung') == 'WDG' and not True in [ True if l in wdg else False for l in headwords ]:
    #    wb.report(entry, path, '@Erstfassung=WDG but not in WDG', not(arguments.path))
    if True in [ True if l in wdg else False for l in headwords ] and not entry.get('Erstfassung') in ('WDG', 'Duden_1999'):
        wb.report(entry, path, 'WDG entry but @Erstfassung!="WDG"', not(arguments.path))
    
    # DWDS
    
    # first entries in 48k where added on 2015-08-07
    if entry.get('Erstfassung') == 'DWDS' and entry.get('Erstellungsdatum') < '2010-03-31':
        wb.report(entry, path, '@Zeitstempel too early for @Erstfassung (DWDS)', not(arguments.path))
    
    # WDW

    if entry.get('Erstfassung') == 'WDW_2011' and not entry.get('Erstellungsdatum') == '2019-12-31':
        wb.report(entry, path, '@Erstfassung WDW_2011, check @Erstellungsdatum', not(arguments.path))
    if entry.get('Erstfassung') == 'WDW_2011' and not entry.get('Quelle') in ('WDW_2011/DWDS', 'DWDS'):
        wb.report(entry, path, '@Erstfassung WDW_2011 but not a (WDW_2011/)DWDS entry', not(arguments.path))
    #if '-WDW_' in path and not (entry.get('Erstfassung') == 'WDW_2011' and entry.get('Erstellungsdatum') == '2019-12-31'):
    #    wb.report(entry, path, 'WDW entry, check @Erstfassung and @Erstellungsdatum', not(arguments.path))

    # hybrid entries
    
    if '/' in entry.get('Quelle') and not entry.get('Erstfassung'):
        wb.report(entry, path, 'hybrid w/o @Erstfassung', not(arguments.path))
    
    # other checks

    if entry.get('Erstellungsdatum') and entry.get('Erstellungsdatum') > entry.get('Zeitstempel'):
        wb.report(entry, path, '@Zeitstempel < @Erstellungsdatum', not(arguments.path))


    # check entry types (only in published entries)

    if entry.get('Status') == 'Red-f':

        d_text = ''.join( wb.text(x)
                for x in et.ETXPath('.//%(Lesart)s[not(@class="invisible")]/%(Definition)s' % wb.TAGS)(entry) )
        k_count = len(et.ETXPath('.//%(Lesart)s[not(@class="invisible")]/*/%(Konstruktionsmuster)s[not(@class="invisible")]' % wb.TAGS)(entry))
        a_count = len(et.ETXPath('.//%(Lesart)s[not(@class="invisible")]/*/%(Kompetenzbeispiel)s[not(@class="invisible")]' % wb.TAGS)(entry))
        v_count = len(et.ETXPath('.//%(Lesart)s[not(@class="invisible")]/%(Verweise)s[not(@class="invisible")]/%(Verweis)s[not(@class="invisble")]' % wb.TAGS)(entry))

        if entry.get('Typ') == 'Vollartikel':
            if d_text == '':
                wb.report(entry, path, 'no def but Vollartikel', not(arguments.path))
            if len(get_cits(entry)) == 0:
                wb.report(entry, path, 'no cit but Vollartikel', not(arguments.path))
            #if wb.get_wordclass(entry) == 'Mehrwortausdruck':
            #    if get_max_cits(entry) < 4:
            #        wb.report(entry, path, f'max. {get_max_cits(entry)} cits in //Lesart but Vollartikel (should be Basisartikel-MWA)', not(arguments.path))

        elif entry.get('Typ') == 'Basisartikel':
            if d_text != '':
                wb.report(entry, path, 'def but Basisartikel', not(arguments.path))
            if len(get_cits(entry)) + k_count + a_count == 0:
                wb.report(entry, path, 'non cit but Basisartikel', not(arguments.path))

        elif entry.get('Typ') == 'Basisartikel-D':
            if d_text == '':
                wb.report(entry, path, 'no def but Basisartikel-D', not(arguments.path))
            if len(get_cits(entry)) != 0:
                wb.report(entry, path, 'cit(s) but Basisartikel-D', not(arguments.path))

        elif entry.get('Typ') == 'Basisartikel-MWA':
            if get_max_cits(entry) > 3:
                wb.report(entry, path, f'{get_max_cits(entry)} cits in //Lesart but MWA-Basisartikel (max. 3, should be Vollartikel)', not(arguments.path))

        elif entry.get('Typ') == 'Verweisartikel':
            if d_text != '':
                wb.report(entry, path, 'def but Verweisartikel', not(arguments.path))
            if v_count == 0:
                wb.report(entry, path, 'non ref but Verweisartikel', not(arguments.path))

        elif entry.get('Typ') == 'Minimalartikel':
            if d_text != '':
                wb.report(entry, path, 'def but Minimalartikel', not(arguments.path))
            if v_count != 0:
                wb.report(entry, path, 'ref but Minimalartikelartikel', not(arguments.path))
            if len(get_cits(entry)) + k_count + a_count != 0:
                wb.report(entry, path, 'cit but Minimalartikel', not(arguments.path))

if arguments.path:
    print()
