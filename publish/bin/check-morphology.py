#! /usr/bin/env python3
# encoding: utf-8

import argparse, re
import lxml.etree as et
from Wb import Wb

def get_constituents(analysis):
    constituents = []
    for _c in analysis:
        if _c.tag == wb.TAGS['Eigenname']:
            constituents.append( (_c.get('Typ'), wb.text(_c)) )
        elif _c.tag == wb.TAGS['Verweis']:
            constituents.append( (_c.get('Typ'), wb.text(_c.findall(wb.TAGS['Ziellemma'])[0])) )
    return constituents
    

wb = Wb(strip=True)

argument_parser = argparse.ArgumentParser(description='Morphology checks.')
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

status = ('Red-f',) if arguments.Red_f else ('Red-f', 'Red-f-blockiert', 'Red-f-Sammelbecken', 'Red-2', 'Red-1', 'Artikelrumpf', 'wird_gestrichen')

for entry, path in wb:

    if arguments.subset == 'recent' and not wb.recently_modified(path):
        continue

    if entry.get('Status') in status:

        for analysis in et.ETXPath('./%(Verweise)s' % wb.TAGS)(entry):
            
            analysis_type = analysis.get('Typ', '')
            constituents = get_constituents(analysis)
            constituent_types = [ _c[0] for _c in constituents ]

            if wb.get_wordclass(entry) == 'Mehrwortausdruck':
                if analysis_type not in ('', 'Simplex', 'Konversion', 'Derivation', 'Kurzwortbildung'):
                    wb.report(entry, path, f'bogus analysis type for MWE: {analysis_type}', not(arguments.path))
                if [True if _x.startswith('MWA-') else False for _x in constituent_types].count(False) != 0:
                    wb.report(entry, path, f'bogus MWA constituent(s) "{constituent_types}"', not(arguments.path))

            if analysis_type == '':
                # not a bug, @Typ is currently not enforced in the schema
                if not constituent_types in (
                        [], # no analysis
                        ['formal_verwandt'],
                        ['Erstglied'], # only partial analysis
                        ['Letztglied'], # only partial analysis
                        ['MWA-Zentralartikel'],
                        ['MWA-Konstituente'],
                        2 * ['MWA-Konstituente'],
                        3 * ['MWA-Konstituente'],
                        4 * ['MWA-Konstituente'],
                        5 * ['MWA-Konstituente'],
                        6 * ['MWA-Konstituente'],
                        7 * ['MWA-Konstituente'],
                ):
                    wb.report(entry, path,
                            '(multiple) constituents without formal process',
                            not(arguments.path))


                pass
            
            elif analysis_type == 'Simplex':

                # simplex: no constituents expected
                if constituents:
                    wb.report(entry, path,
                            'simplex but constituents provided',
                            not(arguments.path))

            elif analysis_type == 'Derivation':
                
                if not constituent_types in (
                        ['Grundform'], # underspecified
                        ['Erstglied', 'Letztglied'], # complete
                        ['Erstglied', 'Binnenglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                ):
                    wb.report(entry, path,
                            f'derivational constituents do not match: {constituent_types}',
                            not(arguments.path))

            elif analysis_type == 'Komposition':
                
                if not constituent_types in (
                        ['Erstglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                ):
                    wb.report(entry, path,
                            f'compositional constituents do not match: {constituent_types}',
                            not(arguments.path))

            elif analysis_type == 'neoklassische_Bildung':
                
                # (structurally comparable to composition and derivation)

                if not constituent_types in (
                        ['Erstglied', 'Letztglied'], # complete
                        ['Erstglied', 'Binnenglied', 'Letztglied'], # complete
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Letztglied'], # complete
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                        ['Letztglied'], # TODO: partial analysis
                ):
                    wb.report(entry, path,
                            f'unexpected structure for neoclassic formation: {constituent_types}',
                            not(arguments.path))

            elif analysis_type == 'Wortkreuzung':
                
                if not constituent_types in (
                        ['Erstglied', 'Letztglied'], # complete
                        ['Erstglied', 'Binnenglied', 'Letztglied'], # complete
                ):
                    wb.report(entry, path,
                            f'unexpected structure for Wortkreuzung: {constituent_types}',
                            not(arguments.path))
            
            elif analysis_type == 'Konversion':
                
                if not constituent_types in (
                        ['Grundform'],
                        ['MWA-Konstituente'],
                ):
                    wb.report(entry, path,
                            f'conversions can only have a single constituent: {constituent_types}',
                            not(arguments.path))
            
            elif analysis_type == 'Kurzwortbildung':
                
                if not constituent_types in (
                        ['Grundform'],
                        ['MWA-Konstituente'],
                        ['Erstglied', 'Letztglied'],
                ):
                    wb.report(entry, path,
                            f'short forms can only have a single constituent: {constituent_types}',
                            not(arguments.path))
            
            elif analysis_type == 'Rückbildung':
                
                if not constituent_types in (
                        ['Grundform'],
                ):
                    wb.report(entry, path,
                            f'Rückbildungen can only have a single constituent: {constituent_types}',
                            not(arguments.path))
            
            elif analysis_type == 'Zusammenrückung':
                
                if not constituent_types in (
                        ['Erstglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
                ):
                    wb.report(entry, path,
                            f'need more constituents for Zusammenrückung: {constituent_types}',
                            not(arguments.path))
            
            elif analysis_type == 'lexikalisierte_Flexionsform':
                
                if not constituent_types in (
                        ['Grundform'], # underspecified
                        ['Erstglied', 'Letztglied'], # complete set of morphemes
                ):
                    wb.report(entry, path,
                            f'constituents do not match: {constituent_types}',
                            not(arguments.path))
                
            else:
                
                wb.report(entry, path,
                        f'unknown analysis type: {analysis_type}',
                        not(arguments.path))


if arguments.path:
    print()
