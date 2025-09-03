#! /usr/bin/env python3
# encoding: utf-8

import argparse
import lxml.etree as et
from Wb import Wb


argument_parser = argparse.ArgumentParser(description='Constraints checks.')
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

    if arguments.subset == 'recent' and not wb.recently_modified(path):
        continue

    if not entry.get('Status', '').startswith('Red-'):
        continue
    # TODO: remove after RSR-Einspielung
#    if entry.get('Status') == 'Red-f' and entry.get('Tranche', '').startswith('RSR-'):
#        wb.report(entry, path, 'Published RSR-... entry')

    # Wortklassenangaben
    pos = set( [wb.text(_p) for _p in entry.findall('.//%(Wortklasse)s' % wb.TAGS)] )
    pos.discard('') # discard unspecified parts-of-speech
    if len(pos) > 1:
        wb.report(entry, path, 'multiple parts-of-speech')

    # Linkanpassungen
    for z in entry.findall('.//%(Ziellemma)s' % wb.TAGS):
        if z.get('Anzeigeform') is not None and z.getparent().getparent().tag == wb.TAGS['Verweise'] and z.getparent().get('Typ') != 'MWA-Zentralartikel':
            wb.report(entry, path, 'rewritten link in link group')

    # falsche Linktypen
    for v in entry.findall('.//%(Lesart)s/%(Verweise)s/%(Verweis)s' % wb.TAGS):
        t = v.get('Typ')
        if t in ('vgl', 'mehr_sv'):
            wb.report(entry, path, f'illegal //Verweis@Typ="{t}"')

    # Bildunterschriften
    for b in entry.findall('.//%(Bildunterschrift)s' % wb.TAGS):
        text = wb.text(b)
        if text == '':
            wb.report(entry, path, 'empty //Bildunterschrift')
        elif text[0] != text[0].upper() and text[0] != '(':
            wb.report(entry, path, '//Bildunterschrift not capitalized:'+text[0])
        elif not text[-1].isalnum() and not text[-1] in (')', '«'):
            wb.report(entry, path, 'trailing punctuation in //Bildunterschrift')

    # Formangaben
    t = [ _f.get('Typ')
            for _f in entry.findall('./%(Formangabe)s' % wb.TAGS) ]
    
    if not t[0] == 'Hauptform':
        wb.report(entry, path, 'first //Formangabe/@Type != "Hauptform"', not(arguments.path))

    # Definitionen
    for d in entry.findall('.//%(Definition)s' % wb.TAGS):
        t = ''.join(et.ETXPath('./text()')(d))
        t_all = wb.text(d, normalize=False)
        p = d.getprevious()
        n = d.getnext()
        if t_all.strip() != '':
            if t.strip(' \n\r,') == '':
                wb.report(entry, path, 'link-only def', not(arguments.path))
        if t_all.strip() == '' and p is not None and p.tag == wb.TAGS['Definition']:
            wb.report(entry, path, 'unneeded empty def', not(arguments.path))

    # Autorenzusätze
    for a in entry.findall('.//%(Autorenzusatz)s' % wb.TAGS):
        parent = a.getparent()
        if wb.text(a).endswith(':') and parent.index(a) == 0 and (parent.text or '').strip() == '':
            wb.report(entry, path, 'colon in citation initial //Autorenzusatz', not(arguments.path))

        if wb.text(a) == 'Titel':
            wb.report(entry, path, '"Titel" → "Überschrift" in //Autorenzusatz', not(arguments.path))


if arguments.path:
    print()
