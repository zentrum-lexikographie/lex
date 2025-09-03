#! /usr/bin/env python3
# encoding: utf-8

import argparse
import regex as re
import lxml.etree as et
from Wb import Wb

argument_parser = argparse.ArgumentParser(description='Typography checks.')
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

wb = Wb(strip=True)

def is_space_separated(element, wortklasse):
    
    parent = element.getparent()
    pos = parent.index(element)
    
    t = (parent.text or '') if pos == 0 else (parent[pos-1].tail or '')
    if t == '' and pos == 0:
        pass
    elif t == '':
        pass # TODO
    elif not t[-1].isalnum():
        pass
    elif t[-1].isalnum() and wb.text(element)[0] == '’':
        pass # clitics
    elif t[-1].isalpha() and wortklasse == 'Affix':
        pass
    else:
        return False

    return True

illegal1 = (
        # unparsed XML/HTML entities
        re.compile('(&#x?\d+;)'),
        re.compile('(&(quot|gt|lt|amp|nbsp);)'),
)

illegal2 = (
        # Anführungszeichen, Apostrophe
        re.compile('([ʼ\'"“”„‘‚<>])'),
        re.compile('(.[\u0264\u0140])'),
        re.compile('([`´ˋˊ])'),
        re.compile('(\s«|»[\s\.,;:\?!])'),
        re.compile('(\s‹|›\s)'),
        
        # Striche
        re.compile('(\-\-|—|‒|\s\-\s)'),
        re.compile('(\s\-[\.,;:\?!])'),

        # Bindestrichschreibungen
        re.compile('(\(.+\-\)\s)'),
        re.compile('([^\-]\)\p{Lu})'),

        # Satzzeichen
        re.compile('(\s[\.,;:?!])'),
        re.compile('([,;:!?]\p{L})'),
        re.compile('(\.\.)'),

        # Klammern
        re.compile('(\s\)|\(\s)'),
        
        # Zahlen
        re.compile('(_\d+|\d+_)'), # corpus artefacts
        re.compile('(\d[^\S\u00a0]+\d)'),
        re.compile('(\d+\-\d+)'),
        #re.compile('(\d{9})'),
)

legal = (
        # emoticons
        ':-)',
        ';-)',
        ';-)',
        ':-(',
        ';)',
        ':P',
        ':)',
        ':D',
        '<3',
        # gender orthography
        'ein:e',
        'Abonnent:innen',
        'Absolvent:innen',
        'Akademiker:innen',
        'Anleger:innen',
        'Anwohner:innen',
        'Arbeitnehmer:innen',
        'Ärzt:innen',
        'Außenminister:innen',
        'Autofahrer:innen',
        'Autohalter:innen',
        'Autor:innen',
        'Ballkleid-Träger:innen',
        'Beamt:innen',
        'Besucher:innen',
        'Betreiber:innen',
        'Betriebsrät:innen',
        'Betrüger:innen',
        'Bürger:innen',
        'Doktorand:innen',
        'Einzelspieler:innen',
        '-Expert:in',
        'Expert:innen',
        'Fahrzeughalter:innen',
        'Festivalbesucher:innen',
        'Forscher:innen',
        'Führer:innen',
        'Fußgänger:innen',
        'Fussgänger:innen',
        'Gagenempfänger:innen',
        'Gründer:innen',
        'Hausmeister:in',
        'Helfer:innen',
        'Hirnforscher:innen',
        'Informatiker:innen',
        'Ingenieur:innen',
        'Innenminister:innen',
        'jede:r',
        'Journalist:innen',
        'Klimaaktivist:innen',
        'Kolleg:innen',
        'Komiker:innen',
        'Kreditnehmer:innen',
        'Kund:innen',
        'Künstler:innen',
        'Labour-Wähler:innen',
        'Länderkolleg:innen',
        'Lehramtsanwärter:innen',
        'Lehrer:innen',
        'Makler:innen',
        'Maler:in',
        'Mathematiker:innen',
        'Migrant:innenpartei',
        'Mitarbeiter:in',
        'Mitarbeiter:innen',
        'Musiker:innen',
        'Nachwuchsforscher:innen',
        'Nutzer:innen',
        'Oberstufenschüler:innen',
        'Ökonom:innen',
        'Partner:innen',
        'Patient:innen',
        'Physiker:innen',
        'Politker:innen',
        'Polizist:innen',
        'Produzent:innenverband',
        'Protagonist:in',
        'Psycholog:innen',
        'Radfahrer:innen',
        'Regierungschef:innen',
        'Rentner:innen',
        'Schüler:innen',
        'Schwimmtrainer:innen',
        'Selbstgerecht:Innen',
        'Senior:innenpartei',
        'Sexarbeiter:innen,',
        'Spaziergänger:innen',
        'Spitzensportler:innen',
        'Sportler:innen',
        'Staatsbürger:innen',
        'Student:innen',
        'Täter:innen',
        'Ureinwohner:innen',
        'Verbraucher:innen',
        'Verfassungsrichter:innen',
        'Vertreter:innen',
        'Vorarlberger:innen',
        'Warentester:innen',
        'Wissenschaftler:innen',
        'Wohnpartner:innen',
        'Ukrainer:innen',
        'Unterstützer:innen',
        'User:innen',
        'Zuschauer:innen',
        # stuff
        'verni’sa:z(e)',
        '(re’ve:r)',
        '»50-50«-Chance',
        'Airbus-A300-600-Flotte',
        'CR20-36',
        '08-15te',
        '0-8-15-Komik',
        '1:n-Vergleich',
        '1-2-Fly',
        '2-1-4-3-Struktur',
        '2-2-2-Layout',
        '2-2-6- und 3-1-6-Staffelungen',
        '3-1-5-1',
        '3-2-5-Anordnung',
        '3-4-2-1-System',
        '3-4-3',
        '3-5-2',
        '3P0-1S0-Übergang',
        '4-1-4-1,',
        '4-2-3-1',
        '4-2-3-1-Grundordnung',
        '4-3-1-2',
        '4-3-3',
        '4-3-3-System',
        '4-4-2',
        '4-4-2-Formation',
        '4-4-2-System',
        '4-4-Interaktionen',
        '4-5jährigen', # ?
        '4-3-2-1-artige',
        '5-2-3-System',
        '5-7-5-Abfolge',
        '6-3-3-Schema',
        '90-9-1-Regel',
        'x-1-1-1-Burst',
        'DC-9-30',
        # scientific notations
        '< 80 %',
        '<-Relation',
        '>2 GB', # U+00A0 NO-BREAK SPACE (!)
        '(II<o1, o2>I)',
        '(<10 MW),',
        '(Wertzuweisung durch :=)',
        '.bin-Dateien',
        # private orthography
        'J.Lo',
        'ver.di',
        'Ver.di',
        'drei !!!',
        'drei ???',
        'xX----Xx',
        # parenthesis exceptions
        'Ankle (Knöchel-) Boot',
        'Stadt- (Landes-) teil',
        'rechtshändigen (D-) und',
        '(oder Funktions-) und Kontextdisziplinen',
        '(Adels-) oder',
        # URLs
        '(http://www.heise.de/newsticker/data/nl-16.04.98-000/)',
)

for entry, path in wb:

    if arguments.subset == 'recent' and not wb.recently_modified(path):
        continue

    text = wb.text(entry)
    for i in illegal1:
        m = i.search(text)
        if m is not None:
            wb.report(entry, path, f'Illegal text "{m.group(1)}"', not(arguments.path))

    #if entry.get('Status') in status and entry.get('Quelle') != 'WDG':
    if entry.get('Status') in status:
        
        # we don't want to check weired URLs and bibliographies and so on
        for _x in entry.iter(str(wb.TAGS['URL']), str(wb.TAGS['Hervorhebung'])):
            _x.text = 'dieser Text wird ignoriert'
        for _x in entry.iter(str(wb.TAGS['Fundstelle'])):
            if len(_x) == 0 and 'http' in wb.text(_x):
                _x.text = 'URL'
        
        for s in entry.findall('.//%(Stichwort)s' % wb.TAGS) + entry.findall('.//%(erwaehntes_Zeichen)s' % wb.TAGS):
            w = wb.get_wordclass(entry)
            if not is_space_separated(s, w):
                wb.report(entry, path, f'Missing space around //{wb.tagname(s.tag)}',
                        not(arguments.path))
            _t = wb.text(s, normalize=False)
            if len(_t) == 0:
                wb.report(entry, path, 'Empty element //{wb.tagname(s.tag)}', not(arguments.path))
            elif _t[0].isspace() or _t[-1].isspace():
                wb.report(entry, path, f'Unneeded space in //{wb.tagname(s.tag)}',
                          not(arguments.path))
        
        for d in entry.findall('.//%(Autorenzusatz)s' % wb.TAGS):
            text = wb.text(d)
            match = re.search('(sic![^,]+)', text)
            if match is not None:
                wb.report(entry, path, f'Malformed "sic!" in //Autorenzusatz: {match.group(1)}, comma missing?', not(arguments.path))

        for d in entry.findall('.//%(Definition)s' % wb.TAGS) + entry.findall('.//%(Paraphrase)s' % wb.TAGS) + entry.findall('.//%(Kommentar)s' % wb.TAGS) + entry.findall('.//%(Autorenzusatz)s' % wb.TAGS) + entry.findall('.//%(Kollokation)s' % wb.TAGS):
            
            text = wb.text(d)

            # parenthesized words
            match = re.search('\((?P<optional>[^\s]+)\)(?P<sep>\u200d)?(?P<mandatory>[^\s,\.]+)', text)
            if match is not None:
                # target: (Optional-)Mandatory
                if (match.group('optional')[0].isupper() or match.group('mandatory')[0].isupper()) and not match.group('optional').endswith('-') and not match.group('optional')[-1].isdecimal():
                    wb.report(entry, path, f'Unorthographic parenthesis: {match.group("optional")}+{match.group("mandatory")}', not(arguments.path))
                # target: (optional)mandatory
                if match.group('optional').endswith('-') and not (match.group('optional')[0].isupper() and match.group('mandatory')[0].isupper()):
                    wb.report(entry, path, f'Unorthographic parenthesis: {match.group("optional")}+{match.group("mandatory")}', not(arguments.path))


            # abbreviations (and unwanted words)
            
            for abbreviation in ('..', 'Jh.', 'Jhd.', 'Jhds.', 'Jhs.', 'bes.', 'sog.', 'z. T.', 'd. h.', 'v. a.', 'etc.', 'ca.', 'circa', 'ggf.' 'u. a.', 'u. A.', 'u. ä', 'u. Ä.', 'o. a.', 'o. A.', 'bspw.', 'dgl.', 'vgl.', 'Akk.', 'Dat.', 'Gen.', 'Nom.', 'u.', ): # +u.
                if len(abbreviation.split()) == 1 and abbreviation in text.split():
                    # one-token abbreviations
                    wb.report(entry, path, f'Illegal abbreviation: {abbreviation}', not(arguments.path))
                elif len(abbreviation.split()) > 1 and abbreviation in text:
                    # multi-token abbreviations
                    wb.report(entry, path, f'Illegal abbreviation: {abbreviation}', not(arguments.path))
            
            m = re.compile('(\w\.\p{L}+)').search(text)
            if m is not None:
                wb.report(entry, path, m.group(1), not(arguments.path))
        
            m = re.compile('(\p{Lu}\p{L}*\)\p{Ll}+)').search(text)
            if m is not None:
                wb.report(entry, path, m.group(1), not(arguments.path))
        
            m = re.compile('([\s\(]\p{L}+\-\)\p{Ll}+)').search(text)
            if m is not None:
                wb.report(entry, path, m.group(1), not(arguments.path))
        
            m = re.compile('(\p{L}+\(-\p{Ll}+)').search(text)
            if m is not None:
                wb.report(entry, path, m.group(1), not(arguments.path))
        
        for w in entry.findall('.//%(Belegtext)s' % wb.TAGS):
            
            # explicitly split certain whitespace so we can use &#160;
            # and &#8239; as markers for legal patterns
            text = ' '.join(''.join(et.ETXPath('.//text()')(w)).split(' \n\r\t'))
            
            for l in legal:
                text = text.replace(l, 'Ausnahmeschreibung')
            
            for i in illegal2:
                m = i.search(text)
                if m is not None:
                    wb.report(entry, path, f'Illegal text "{m.group(1)}"', not(arguments.path))
        
        for w in entry.findall('.//%(Titel)s' % wb.TAGS):
            if wb.text(w).endswith(':'):
                wb.report(entry, path, '//Titel ends with ":"', not(arguments.path))

        if d.tag == wb.TAGS['Paraphrase']:
            p = d.getprevious()
            t = ''
            if p is not None:
                t = p.tail or ''
            else:
                t = d.getparent().text or ''
            if len(t) > 0 and not t[-1].isspace():
                wb.report(entry, path, 'Found "glued" //Paraphrase', not(arguments.path))
    
        if d.tag in (wb.TAGS['Paraphrase'], wb.TAGS['Autorenzusatz']):
            t = wb.text(d, normalize=False)
            
            if len(t) == 0:
                wb.report(entry, path, f'Empty element //{wb.tagname(d.tag)}', not(arguments.path))
            elif t[0].isspace() or t[-1].isspace():
                wb.report(entry, path, f'Leading/trailing space in //{wb.tagname(d.tag)}', not(arguments.path))
            elif len(t) > 1 and ',' in t[0]+t[-1]:
                wb.report(entry, path, f'Leading/trailing punctuation in //{wb.tagname(d.tag)}', not(arguments.path))
            
            p = d.getprevious()
            t2 = d.getparent().text or '' if p is None else p.tail or ''
            
            if t2 and not t2[-1].isspace() and not t[0] in '.,-':
                #wb.report(entry, path, 'Missing space before Paraphrase or Autorenzusatz', not(arguments.path))
                pass

        for e in entry.findall('.//%(Loeschung)s' % wb.TAGS) + entry.findall('.//%(Streichung)s' % wb.TAGS):
            t = wb.text(e, normalize=False)
            if len(t) == 0:
                wb.report(entry, path, f'Empty element {wb.tagname(e.tag)}')
            elif t[0].isspace() or t[-1].isspace():
                wb.report(entry, path, f'Leading/trailing space in //{wb.tagname(e.tag)}', not(arguments.path))

if arguments.path:
    print()
