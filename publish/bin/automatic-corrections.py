#!/usr/bin/env python3
# encoding: utf-8

import argparse, os, dotenv
import datetime, pytz, time
import random
import requests.exceptions
import lxml.etree as et
import regex as re
from LexSrvAPI import LexSrv
from Wb import Wb
from GoodEx import GoodEx

dotenv.load_dotenv()

lex = LexSrv('https://lex.dwds.de',
             (os.getenv('LEX_USERNAME'), os.getenv('LEX_PASSWORD')))
ge = GoodEx((os.getenv('GOODEX_USERNAME'), os.getenv('GOODEX_PASSWORD')))
wb = Wb()

argument_parser = argparse.ArgumentParser(description='Automatic amendments to lexical entries.')
argument_parser.add_argument('-s', '--subset',
        choices=('all', 'recent'),
        default='recent',
        help='only amend a subset of the entries (default: recent)')
arguments = argument_parser.parse_args()

TODAY = datetime.datetime.now(pytz.utc).strftime('%Y-%m-%d')

def hook_supply_good_examples(e, modify=False):
    '''
    '''
    
    found = False

    raw_data = list(e.findall('.//%(Rohdaten)s/%(Verwendungsbeispiele)s' % wb.TAGS))

    if len(raw_data) == 0:
        r = et.Element(wb.TAGS['Rohdaten'])
        v = et.SubElement(r, wb.TAGS['Verwendungsbeispiele'])
        e.append(r)
        raw_data = v
    else:
        raw_data = raw_data[0]

    if raw_data.get('Autor') is None and len(raw_data) == 0:
            
            found = True

            if modify == True:
                
                c = 0
                
                try:
                    for hw in set(wb.get_headwords(e, only_main_lemmas=True)):
                        _c = 0
                        for _c, (ex, bibl, src) in enumerate(ge.get_examples(hw, 10), start=1):
                            # mixing is nice, especially with multiple forms
                            _position = random.randint(0, len(raw_data))
                            raw_data.insert(_position, ge.goodex2dwdswb(ex, bibl, src))
                        c += _c
                
                    raw_data.set('Autor', 'DWDS')
                    raw_data.set('Quelle', 'DWDS')
                    raw_data.set('Zeitstempel', TODAY)                        
                        
                    wb.report(e, None, f'Adding {c} good example{"" if c == 1 else "s"}')
                except ConnectionError as _e:
                    wp.report(e, None, _e)

    return found


def hook_orth_metadaten(e, modify=False):
    found = False

    for o in e.findall('.//%(Schreibung)s' % wb.TAGS):
        f = o.getparent()
        z_f = f.get('Zeitstempel')
        z_o = o.get('Zeitstempel')
        if z_f is not None and z_o is not None and z_f >= z_o:
            found = True

            if modify == True:
                o.attrib.pop('Zeitstempel')
                o.attrib.pop('Autor', '')
                o.attrib.pop('Quelle', '')
                wb.report(e, None, f'//Schreibung/{z_o} superceded by //Form/{z_f}')

    return found


def hook_trim_attribute_values(e, modify=False):
    found = False
    for x in e.findall('.//*'):
        for k, v in x.attrib.items():
            if v != v.strip():
                found = True

                if modify == True:
                    wb.report(e, None, f'Trimming attribute value @{k}="{v}"')
                    x.set(k, v.strip())
    return found


def hook_add_anonymous_author(e, modify=False):
    found = False
    
    for x in et.ETXPath('.//*[@Quelle]')(e.getparent()):
        
        if x.get('Quelle') not in ('Duden_1999', 'WDG') and x.get('Autor') is None:
            found = True
            
            if modify == True:
                x.set('Autor', 'DWDS')
                wb.report(e, None, f'Setting @Autor="DWDS" for anonymous entry')
    
    return found


def hook_schreibung_metadata(e, modify=False):
    found = False

    for s in et.ETXPath('.//%(Schreibung)s[@Zeitstempel]' % wb.TAGS)(e):

        parent = s.getparent()
        z_p = parent.get('Zeitstempel')
        z_s = s.get('Zeitstempel')
        
        if z_p is None or z_p < z_s:
            
            found = True

            if modify == True:
                parent.set('Autor', s.attrib.pop('Autor', 'DWDS'))
                parent.set('Quelle', s.attrib.pop('Quelle', 'DWDS'))
                parent.set('Zeitstempel', s.attrib.pop('Zeitstempel'))
                wb.report(e, None, f'Promoting metadata //Schreibung → //Formangabe: {z_p} → {z_s}')

    return found


def hook_originaltext(e, modify=False):
    found = False
    for g in et.ETXPath('.//%(Grammatik)s[@Originaltext]' % wb.TAGS)(e):
        t = wb.text(g).split()
        if len(t) > 1 or e.get('Status') == 'wird_gestrichen' or (t and t[0] in ('Adjektiv', 'Adverb', 'Interjektion', )):
            found = True
            if modify == True:
                o = g.attrib.pop('Originaltext')
                wb.report(e, None, f'Removing @Originaltext="{o}" → {t} (@Status={e.get("Status")})')
    return found


def hook_promote_gram_metadata(e, modify=False):
    found = False
    for f in et.ETXPath('./%(Formangabe)s' % wb.TAGS)(e):
        for g in et.ETXPath('./%(Grammatik)s[@Zeitstempel]' % wb.TAGS)(f):
            if g.get('Zeitstempel') < f.get('Zeitstempel', '0000-00-00'):
                found = True
                if modify == True:
                    q = g.attrib.pop('Quelle', '')
                    z = g.attrib.pop('Zeitstempel', '')
                    a = g.attrib.pop('Autor', '')
                    wb.report(e, None, f'Removing old metadata from //Grammatik: {q}, {z}, {a}')
            else:
                found = True
                if modify == True:
                    q = g.attrib.pop('Quelle', '')
                    z = g.attrib.pop('Zeitstempel', '')
                    a = g.attrib.pop('Autor', '')
                    f.set('Quelle', q)
                    f.set('Zeitstempel', z)
                    f.set('Autor', a)
                    wb.report(e, None, f'Promoting new metadata //Grammatik → //Formangabe: {q}, {z}, {a}')
    return found
            

TYPOGRAPHIC_SUBSTITUTIONS = (
    # only 100% blindly substitutable occurrences (!)
    # or at least substitutions that are easily revertable
    ('o. ä.', u'o.\u202fä.'),
    ('o.ä.', u'o.\u202fä.'),
    ('o. Ä.', u'o.\u202fÄ.'),
    ('o.Ä.', u'o.\u202fÄ.'),
    ('z. B.', u'z.\u202fB.'),
    ('z.B.', u'z.\u202fB.'),
    ('v. H.', u'v.\u202fH.'),
    ('v.H.', u'v.\u202fH.'),
    ('e. V.', u'e.\u202fV.'),
    ('e.V.', u'e.\u202fV.'),
    ('d. h.', u'd.\u202fh.'),
    ('d.h.', u'd.\u202fh.'),
    ('z. T.', u'z.\u202fT.'),
    ('z.T.', u'z.\u202fT.'),
    ('v. Chr.', u'v.\u202fChr.'),
    ('v.Chr.', u'v.\u202fChr.'),
    ('C\'t,', 'C’t,'),
    ('...', '…'),
    ('. . .', '…'),
    (' . ', '. '),
    (' .\n', '. '),
    (' , ', ', '),
    (' ,\n', ', '),
    (' ; ', '; '),
    (' ;\n', '; '),
    (' - ', ' – '),
    (' -\n', ' – '),
    ('\n- ', ' – '),
    (' -- ', ' – '),
    ('—', '–'), # EM DASH → EN DASH
    ('‒', '–'), # FIGURE DASH → EN DASH
    (' -,', ' –,'),
    (' | ', ' – '), # VERTICAL LINE → EN DASH (online page titles)
    (' "', ' »'), # note: real inch sign → ″ (U+2033 DOUBLE PRIME)
    ('" ', '« '), # note: real inch sign → ″ (U+2033 DOUBLE PRIME)
    ('",', '«,'),
    ('".', '«.'),
    (',"', '«,'),
    ('."', '«.'),
    ('„', '»'),
    ('“', '«'),
)

IPA_SUBSTITUTIONS = (
    (':', 'ː'),  # U+02D0 MODIFIER LETTER TRIANGULAR COLON
    ('\'', 'ˈ'), # U+02C8 MODIFIER LETTER VERTICAL LINE
    ('?', 'ʔ'),  # U+0294 LATIN LETTER GLOTTAL STOP
    ('Ɂ', 'ʔ'),  # U+0241 LATIN CAPITAL LETTER GLOTTAL STOP
    (',', 'ˌ'),  # U+02CC MODIFIER LETTER LOW VERTICAL LINE
    ('[', ''),
    (']', ''),
)


def _replace(t, p, s):
    pos = t.index(p)
    p1 = pos - 10 if pos > 10 else 0
    p2 = pos + 10 if pos < len(t) + 10 else len(t)
    return t.replace(p, s)


def hook_ipa(e, modify=False):
    
    found = False
    count = 0

    for i in et.ETXPath('.//%(IPA)s' % wb.TAGS)(e):
        ipa = wb.text(i)
        if len(i) != 0:
            wb.report(entry, None, 'Complex content in //IPA, check manually')
        elif ipa != '':
            for pattern, substitution in IPA_SUBSTITUTIONS:
                if pattern in ipa:
                    found = True
                    count += 1
                    if modify:
                        i.text = ipa.replace(pattern, substitution)

    if found and modify:
        wb.report(e, None, f'Substituting strings in //IPA ({count})')
    return found

def hook_erstfassung(e, modify=False):
    found = False

    if e.get('Erstfassung') is None and e.get('Quelle') == 'DWDS' and e.get('Zeitstempel') == e.get('Erstellungsdatum'):
        found = True
        if modify:
            wb.report(e, None, f'Setting @Erstfassung=DWDS')
            e.set('Erstfassung', 'DWDS')
    return found

def hook_future_timestamp(e, modify=False):
    found = False

    for element in et.ETXPath('./*[@Zeitstempel]')(e.getparent()):
        z = element.get('Zeitstempel')
        if element.get('Zeitstempel') > TODAY:
            found = True
            if modify:
                element.set('Zeitstempel', TODAY)
                wb.report(e, None, f'Changing future date: {z} → {TODAY}')

    return found


def hook_trim_streichung(e, modify=False):
    found = False

    for s in e.findall('.//%(Streichung)s' % wb.TAGS) + e.findall('.//%(Loeschung)s' % wb.TAGS):
        t = wb.text(s, normalize=False)
        if len(t) != len(t.strip()) and len(s) == 0:
            found = True
            if modify:
                p = s.getparent()
                # left
                t_l = t.lstrip()
                if len(t) != len(t_l):
                    wb.report(e, None, f'Trimming //{wb.tagname(s.tag)}')
                    i = p.index(s)
                    s.text = t_l
                    if i == 0:
                        p.text = (p.text or '') + ' '
                    else:
                        p[i-1].tail = (p[i-1].tail or '') + ' '
                # right
                t_r = t.rstrip()
                if len(t) != len(t_r):
                    wb.report(e, None, f'Trimming //{wb.tagname(s.tag)}')
                    s.text = t_r
                    s.tail = ' ' + (s.tail or '')

    return found


def hook_directmedia1(e, modify=False):
    found = False
    for f in e.findall('.//%(Lesart)s//%(Fundstelle)s' % wb.TAGS):
        t = wb.text(f)
        if re.search('Directmedia\s+Publ\.', t) is not None:
            if len(f) == 0:
                found = True
                if modify:
                    f.text = re.sub('Directmedia\s+Publ\.', 'Directmedia', t).strip()
                    wb.report(e, None, f'Directmedia trimming: {f.text}')
            else:
                wb.report(e, None, 'Directmedia trimming: check manually')

    return found


def hook_directmedia2(e, modify=False):
    found = False
    for f in e.findall('.//%(Lesart)s//%(Fundstelle)s' % wb.TAGS):
        t = wb.text(f)
        m = re.match('^(.*Berlin:\s+Directmedia.*)(,\s+S\.\s+\d+\s*)$', t)
        if m is not None:
            if len(f) == 0:
                found = True
                if modify:
                    f.text = m.group(1).strip()
                    wb.report(e, None, f'Directmedia trimming pages: {f.text}')
            else:
                wb.report(e, None, 'Directmedia trimming pages: check manually')
    return found


def hook_minimalartikel_tranche(e, modify=False):
    found = False

    if e.get('Tranche', '') == ('45k-Minimalartikel') and e.get('Typ') != 'Minimalartikel':
        found = True
        if modify:
            e.attrib.pop('Tranche')
            wb.report(e, None, f'Removing @Tranche="45k-Minimalartikel (@Status="{e.get("Typ")}")"')

    return found


def hook_nr(e, modify=False):
    found = False
    
    for f in e.findall('.//%(Lesart)s//%(Fundstelle)s' % wb.TAGS):
        if len(f) == 0:
            t = wb.text(f)
            m = re.match('(?P<head>.*),\s+(?P<date>[0123]\d\.[01]\d\.[12]\d\d\d)\s*(?P<tail>.*)', t)
            if m is not None and m.group('head') in wb.PERIODICALS and m.group('tail'):
                if re.match(', Nr\. \d+$', m.group('tail')):
                    found = True

                    if modify:
                        f.text = m.group('head') + ', ' + m.group('date')
                        wb.report(e, None, f'Trimming metadata for periodical:  → {m.group("head")}, {m.group("date")}')
    return found


def hook_typography(e, modify=False):    

    found = False
    count = 0

    for q in e.iter(): # elements, PIs, comments
        

        for pattern, substitution in TYPOGRAPHIC_SUBSTITUTIONS:
            
            text = q.text or ''
            # reduced auto-correction for //Schreibung, //Ziellemma, //Stichwort
            if q.tag in (wb.TAGS['Schreibung'], wb.TAGS['Ziellemma'], wb.TAGS['Stichwort']):
                if '..' in (q.text or ''):
                    wb.report(e, None, f'Check {wb.tagname(q.tag)} for ".." manually!')
                    continue
            # auto-correct <>.text only for elements, not for PIs or comments
            elif isinstance(q, et._ProcessingInstruction):
                continue
            elif isinstance(q, et._Comment):
                continue            
            elif pattern in text:
                found = True
                count += 1
                if modify:
                    q.text = _replace(text, pattern, substitution)
            
            # auto-correct <>.tail for all elements, PIs and comments
            text = q.tail or ''
            if pattern in text:
                # do this for elements, PIs and comments
                found = True
                count += 1
                if modify:
                    q.tail = _replace(text, pattern, substitution)

    if found and modify:
        wb.report(e, None, f'Substituting strings ({count})')
    return found


def scrub(path, function):
    root, token = lex.fetch_entry(path)
    if root is None:
        print(path, '[LOCKED]', sep=' ', flush=True)
    else:
        if function(root.findall(wb.TAGS['Artikel'])[0], modify=True) is True:
            lex.store_entry(path, root, token)
            return True
        else:
            lex._release_lock(path, token)
    return False


HOOKS = (
        hook_originaltext,
        hook_supply_good_examples,
        hook_trim_streichung,
        hook_schreibung_metadata,
        hook_minimalartikel_tranche,
        hook_typography,
        hook_ipa,
        hook_promote_gram_metadata,
        hook_add_anonymous_author,
        hook_trim_attribute_values,
        hook_orth_metadaten,
        hook_erstfassung,
        hook_future_timestamp,
        hook_nr,
        hook_directmedia1,
        hook_directmedia2,
)

counter = 0
for entry, path in wb:

    if arguments.subset == 'recent' and not wb.recently_modified(path):
        continue

    for hook in HOOKS:
        if hook(entry):
            try:
                if scrub(path, hook) is True:
                    counter += 1
            except (ConnectionError, requests.exceptions.HTTPError):
                print('Network error encountered')
                print('Pushing changes, please `git pull`')
                lex.commit_changes()
                exit(-2)
            if counter >= 100:
                print('100 modifications committed, pushing changes')
                counter = 0
                lex.commit_changes()
                time.sleep(5)

if counter != 0:
    print('Pushing changes, please `git pull`')
    lex.commit_changes()
else:
    print('No changes')
