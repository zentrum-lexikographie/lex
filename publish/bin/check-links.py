#! /usr/bin/env python3
# encoding: utf-8

import collections, re, copy, argparse
import lxml.etree as et
from Wb import Wb

wb = Wb()

lemmas = {}
ziele = collections.defaultdict(list)
zerlegungen = collections.defaultdict(set)

VALID_CLASSES = (
    'a', 'as', 'ass',   # 1, 1 *, 1 * *
    'al', 'als',        # 1 a, 1 a *
    'alg',              # 1 a α
    'algs',             # 1 a α *
    'asg',              # 1 * α
    'g',                # α
    'l', 'ls',          # a
    'lg',               # a α
    'r',                # I
    'ra',               # I 1
    'ral',              # I 1 a
    'ras',              # I 1 *
    'ralg',             # I 1 a α
    'rs',               # I *
    'L',                # A
    'Lr',               # A I
    'Lra',              # A I 1
    'Lral',             # A I 1 a
    's',                # *
    'ss',               # * *
    'sg',               # * α
)

def get_entry_source(element):
    if element.tag == wb.TAGS['Artikel']:
        return element.get('Quelle')
    else:
        return get_entry_source(element.getparent())

def check_ziellesart(root):
    
    def parse(z):
        seq = []
        parts = z.split(' ')
        for p in parts:
            if re.match('^\d\d?$', p):
                seq.append('a')
            elif re.match('^[a-z]$', p):
                seq.append('l')
            elif re.match('^[αβγδεζηθικλμνξοπρστυφχψω]$', p):
                seq.append('g')
            elif re.match('^I|II|III|IV|V|VI$', p):
                seq.append('r')
            elif re.match('^[A-K]$', p): # clashes with class 'r' after 'K'
                seq.append('L')
            elif p == '*':
                seq.append('s')
            else:
                seq.append('?')
        
        if ''.join(seq) in VALID_CLASSES:
            return True
        else:
            return False
    
    for z in et.ETXPath('.//%(Ziellesart)s' % wb.TAGS)(root):
        t = wb.text(z)
        if t == '':
            continue
        for part in t.split(', '):
            if not parse(part):
                wb.report(root.getparent(), '', 'Syntax error: '+t, verbose=not(args.path))
                return False
    return True


def check_structuring(root):
    
    allowed_ranges = (
        '#', # default for senses with no siblings and no headmark
        '*****************', # TODO: enumerate
        '1.2.3.4.5.6.7.8.9.10.11.12.13.14.15.16.17.18.19.20.21.22.23.24.25.26',
        'a)b)c)d)e)f)g)h)i)j)k)l)m)n)o)p)q)r)s)t)u)v)w)x)y)z)',
        'α)β)γ)δ)ε)ζ)η)θ)ι)κ)λ)μ)ν)ξ)ο)π)ρ)σ)τ)υ)φ)χ)ψ)ω)',
        'I.II.III.IV.V.VI.VII.VIII.XI.X.XI.XII.',
        'A.B.C.D.E.F.G.H.I.J.K.L.M.N.O.P.Q.R.S.T.U.V.W.X.Y.Z.',
    )
    
    n = ''.join([l.get('n', '#')
            for l in et.ETXPath('./%(Lesart)s[not(@class="invisible")]' % wb.TAGS)(root)
    ])

    if n == '#' and root.getparent().tag == wb.TAGS['Lesart']: # and entry.get('Quelle') != 'WDG': # TODO: -WDG
       wb.report(entry, path, 'Unmarked anonymous subsense', verbose=not(args.path))
       return False
    
    for allowed in allowed_ranges:
        if allowed.startswith(n):
            break
    else:
        if get_entry_source(root) not in ('WDG'): # TODO
            wb.report(entry, path, f'Illegal @n sequence: {n}', verbose=not(args.path))
            return False
    
    for l in root.findall(wb.TAGS['Lesart']):
        r = check_structuring(l)
        if r is False:
            return r
    
    return True


# main

ap = argparse.ArgumentParser(description='Check link resolution')
ap.add_argument('-p', '--path',
        action='store_true',
        default=False,
        help='show article URIs instead of headword and snippet')
ap.add_argument('-m', '--merge-suggestions', action='store_true',
        help='issue merge suggestions for entries with identical morphology')
args = ap.parse_args()

for entry, path in wb:
    
    if entry.get('Status') == 'Red-f':

        if check_structuring(entry) == False:
            wb.report(entry, path, 'Formally broken sense numbering', verbose=not(args.path))

        for s in entry.findall(wb.TAGS['Lesart']):
            if check_ziellesart(s) == False:
                wb.report(entry, path, 'Syntax error in //Ziellesart', verbose=not(args.path))
        
        for lemma in wb.get_headwords(entry):
            lemmas[lemma] = path
        
        # collect morphology
        for zerlegung in entry.findall('./%(Verweise)s' % wb.TAGS):
            konstituenten = []
            for ziel in et.ETXPath('./%(Verweis)s[not(@class="invisible")]/%(Ziellemma)s' % wb.TAGS)(zerlegung):
                z = ' '.join(wb.text(ziel).split())
                z = (z + '#' + ziel.get('hidx', '')).rstrip('#')
                e = et.Element(wb.TAGS['Artikel'])
                for f in entry.findall('./%(Formangabe)s' % wb.TAGS):
                    e.append(copy.copy(f))
                ziele[z].append( (e, path) )
                if not ziel.getparent().get('Typ').startswith('MWA-'):
                    konstituenten.append(z)
            zerlegungen['+'.join(konstituenten)].add(wb.get_headwords(entry)[0])
        
        # collect lexical-semantic links
        for ziel in et.ETXPath('.//%(Lesart)s//%(Verweis)s[not(@class="invisible")]/%(Ziellemma)s' % wb.TAGS)(entry):
            z = ' '.join(wb.text(ziel).split())
            z = (z + '#' + ziel.get('hidx', '')).rstrip('#')
            e = et.Element(wb.TAGS['Artikel'], Status=entry.get('Status'))
            for f in entry.findall('./%(Formangabe)s' % wb.TAGS):
                e.append(copy.copy(f))
            ziele[z].append( (e, path) )

for ziel, sources in ziele.items():
    if not ziel in lemmas:
        for entry, path in sources:
            wb.report(entry, path, 'Broken link: '+ziel, verbose=not(args.path))

if args.merge_suggestions == True:
    for zerlegung, artikel in zerlegungen.items():
        if len(artikel) > 1 and '+' in zerlegung:
            print('Merge entries for',
                    zerlegung, '-->',
                    ', '.join(artikel),
                    flush=True
            )
