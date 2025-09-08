# encoding: utf-8

import os, logging, collections
import urllib.parse, pathlib
import time, datetime, pytz
import unicodedata
import lxml.etree as et

class Wb(object):

    COLORS = {
        'purple': '\033[1;35;48m',
        'cyan': '\033[1;36;48m',
        'blue': '\033[1;34;48m',
        'green': '\033[1;32;48m',
        'yellow': '\033[1;33;48m',
        'red': '\033[1;31;48m',
        'black': '\033[1;30;48m',
        'bold': '\033[1;37;48m',
        'underline': '\033[4;37;48m',
        'reset': '\033[1;37;0m',
    }

    __NOW = time.time()
    NOW = 0
    MAX_FILE_AGE = 2 * 7 * 24 * 60 * 60 # two weeks

    TAGS = { tag: et.QName('http://www.dwds.de/ns/1.0', tag)
            for tag in ('DWDS', 'Artikel', 'Phrasem', 'Kollokation', 'Verweise',
                'Schreibung', 'Lesart', 'Ziellemma', 'Verweis', 'Aussprache',
                'Konstruktionsmuster', 'Einschraenkung', 'Sprachraum', 'Beleg',
                'Grammatik', 'Artikelpraeferenz', 'Stilebene', 'Illustration',
                'Definition', 'Formangabe', 'Wortklasse', 'URL', 'Titel',
                'Fundstelle', 'Bildunterschrift', 'Kurztitel', 'Ziellesart',
                'Kompetenzbeispiel', 'Stichwort', 'Ueberschrift', 'reflexiv',
                'Verwendungsbeispiele', 'Rohdaten', 'Eigenname', 'Hervorhebung',
                'Diachronie', 'Etymologie', 'Bedeutungsgeschichte', 'IPA',
                'Formgeschichte', 'Belegtext', 'Zusatz', 'Autorenzusatz',
                'Paraphrase', 'Genus', 'Kommentar', 'Plural', 'indeklinabel',
                'Genitiv', 'Sprachareal', 'Fachgebiet', 'Diasystematik',
                'erwaehntes_Zeichen', 'Numeruspraeferenz', 'Kollokationen',
                'Orthografie', 'Orthografieregel', 'Frequenzangabe',
                'Bedeutungsebene', 'Syntagmatik', 'Verweis_extern',
                'Auxiliar', 'Stilfaerbung', 'Gruppensprache',
                'Komparativ', 'Superlativ', 'Wert', 'Positivvariante',
                'Streichung', 'Loeschung', 'Praesens', 'Praeteritum',
                'Partizip_II', 'Komparationspraeferenz', 'Gebrauchszeitraum',)
    }
    TAGS['xml:id'] = et.QName('http://www.w3.org/XML/1998/namespace', 'id')
    _REVERSED_TAGS = { v: k for k, v in TAGS.items() } # should be 1:1

    STATUS = (
            'wird_gestrichen',
            'Artikelrumpf',
            'Lex-zurückgestellt',
            'Lex-in_Arbeit',
            'Lex-zur_Abgabe',
            'Lex-Wiedervorlage',
            'Lex-kommentiert',
            'Red-0',
            'Red-1',
            'Red-2-zurückgewiesen',
            'Red-2',
            'Red-f-zurückgewiesen',
            'Red-ex',
            'Red-f',
    )

    PERIODICALS = (
        'Aachener Zeitung',
        'Academia',
        'Allgemeine Zeitung',
        'APA-Meldungen digital',
        'Apotheken Umschau', # sic!
        'Arbeit und Wirtschaft',
        'Aschaffenburger Zeitung',
        'ATV',
        'Augustin',
        'Badener Zeitung',
        'Badische Zeitung',
        'Basler Zeitung',
        'Bauernzeitung',
        'Berliner Illustrirte Zeitung',
        'Berliner Morgenpost',
        'Berliner Tageblatt',
        'Berliner Tageblatt (Abend-Ausgabe)',
        'Berliner Tageblatt (Montags-Ausgabe)',
        'Berliner Tageblatt (Morgen-Ausgabe)',
        'Berliner Tageblatt (Sonntags-Ausgabe)',
        'Berliner Zeitung',
        'Berner Zeitung',
        'Bild',
        'Bild am Sonntag',
        'Bild der Wissenschaft',
        'Börsenblatt für den deutschen Buchhandel',
        'Bote der Urschweiz',
        'Braunschweiger Tages-Zeitung',
        'Braunschweiger Zeitung',
        'Bravo',
        'Burgenländische Volkszeitung',
        'BZ am Abend',
        'Computer Zeitung',
        'C’t',
        'Czernowitzer Allgemeine Zeitung',
        'Das Andere Deutschland',
        'Das Pfennig-Magazin der Gesellschaft zur Verbreitung gemeinnütziger Kenntnisse',
        'Das Reich',
        'Das Schwarze Korps',
        'Das Wespennest',
        'Datum', # https://de.wikipedia.org/wiki/Datum_(Zeitschrift)
        'Der Angriff',
        'Der Angriff (Abendausgabe)',
        'Der Arbeitgeber',
        'Der Bazar',
        'Der Bund',
        'Der Erft-Bote',
        'Der Grazer',
        'Der Konsument',
        'Der oberschlesische Wanderer',
        'Der Prignitzer',
        'Der Spiegel',
        'Der Standard',
        'Der Stürmer',
        'Der Tag',
        'Der Tagesspiegel',
        'Deutsche Volkszeitung',
        'Die Bayerische Presse',
        'Die Fackel',
        'Die Furche',
        'Die Neue Gesellschaft',
        'Die Landfrau',
        'Die Literarische Welt',
        'Die Presse',
        'Die Schaubühne',
        'die tageszeitung',
        'Die Welt',
        'Die Weltbühne',
        'Die Wirtschaft',
        'Die Zeit',
        'DIGICLIP', # ??
        'Döbelner Allgemeine Zeitung',
        'Dresdner Neueste Nachrichten',
        'Dresdner Volkszeitung',
        'E-Media',
        'Echo',
        'Elle',
        'Evangelischer Arbeiterbote',
        'Falter',
        'Focus',
        'Format',
        'Forum',
        'Frankfurter Allgemeine Zeitung',
        'Frankfurter Allgemeine Sonntagszeitung',
        'Frankfurter Presse',
        'Frankfurter Rundschau',
        'Frankfurter Zeitung (1. Morgenblatt)',
        'Frankfurter Zeitung (2. Morgenblatt)',
        'Frankfurter Zeitung (3. Morgenblatt)',
        'Frankfurter Zeitung (4. Morgenblatt)',
        'Frankfurter Zeitung (Abend-Ausgabe)',
        'Frankfurter Zeitung (Morgen-Ausgabe)',
        'Fränkischer Tag',
        'Freie deutsche Presse',
        'Freie Presse',
        'Freisinnige Zeitung',
        'Gelbe Post',
        'Gewinn',
        'Hamburger Abendblatt',
        'Hamburger Echo',
        'Hamburger Morgenpost',
        'Hamburger Nachrichten',
        'Handelsblatt',
        'Hannoversche Allgemeine Zeitung',
        'Heute',
        'Horizont',
        'Industriemagazin',
        'Innsbrucker Nachrichten',
        'jetzt-Magazin (SZ)',
        'Junge Welt',
        'Kärntner Tageszeitung',
        'Kärntner Wirtschaft',
        'Katholische Arbeiterzeitung',
        'Kieler Nachrichten',
        'Kleine Zeitung',
        'Kölnische Zeitung',
        'Kölnische Zeitung (1. Morgenblatt)',
        'Kölnische Zeitung (2. Morgenblatt)',
        'Kölnische Zeitung (Abend)',
        'Kölnische Zeitung (Mittagsblatt)',
        'Kölnische Zeitung (Morgenblatt)',
        'Kölnische Volkszeitung und Handelsblatt',
        'Kölnische Volkszeitung und Handelsblatt (Morgenausgabe)',
        'Kölnische Volkszeitung und Handelsblatt (Abendausgabe)',
        'konkret',
        'Koralle',
        'Kosmos',
        'Kreuz-Zeitung',
        'Kronen Zeitung',
        'Kurier',
        'Landshuter Zeitung',
        'Leipziger Volkszeitung',
        'Liller Kriegszeitung',
        'Luzerner Zeitung',
        'Magazin der Hausfrau',
        'Mährisches Tagblatt',
        'Mainzer Journal',
        'Mannheimer Morgen',
        'Marburger Zeitung',
        'Märkische Blätter',
        'Medianet',
        'Mittelbayerische',
        'Mode und Handarbeit',
        'Morgenblatt für gebildete Leser',
        'Mücke',
        'Münchner Merkur',
        'Münchner Neueste Nachrichten',
        'Münchner Neueste Nachrichten (Vormittags-Ausgabe)',
        'Münchner Neueste Nachrichten (Morgen-Ausgabe)',
        'National-Zeitung (Abend-Ausgabe)',
        'National-Zeitung (Morgen-Ausgabe)',
        'Naumburger Kreisblatt', # … für Stadt und Kreis Naumburg
        'NBI',
        'Neue badische Landeszeitung (Morgen)',
        'Neue Hamburger Presse',
        'Neue Kronen-Zeitung',
        'Neue Mode',
        'Neue Osnabrücker Zeitung',
        'Neue preussische Kreuz-Zeitung (Abend)',
        'Neue Rheinische Zeitung',
        'Neue Vorarlberger Tageszeitung',
        'Neue Westfälische',
        'Neue Zeit',
        'Neue Zeitung',
        'Neue Zürcher Zeitung',
        'Neue Zürcher Zeitung (Fernausgabe)',
        'Neue Zürcher Zeitung am Sonntag',
        'Neuer Kärntner Monat',
        'Neuer Vorwärts',
        'Neuer Weg',
        'Neues Deutschland',
        'Neues Leben',
        'Neues Volksblatt',
        'News',
        'Niederösterreichische Nachrichten',
        'Norddeutsche Neueste Nachrichten',
        'Nordkurier',
        'Nürnberger Nachrichten',
        'Nürnberger Zeitung',
        'Oberländer Rundschau',
        'Oberösterreichische Nachrichten',
        'Original Text Service',
        'ORF',
        'Österreich',
        'Pariser Tageblatt',
        'Pariser Tageszeitung',
        'Pharmazeutische Zeitung',
        'Potsdamer Neueste Nachrichten',
        'Prager Post',
        'Prager Tagblatt',
        'Praktischer Wegweiser',
        'Profil',
        'Reichspost',
        'Reichspost Wien',
        'Reklame-Praxis',
        'Rhein- und Ruhrzeitung',
        'Rhein-Zeitung',
        'Rheinische Post',
        'Rote Fahne',
        'Rote Fahne (Morgen-Ausgabe)',
        'Reutlinger General-Anzeiger',
        'Saarbrücker Zeitung',
        'Salzburger Nachrichten',
        'Salzburger Woche',
        'Salzburger Volkszeitung',
        'SAT.1',
        'Schweriner Volkszeitung',
        'Simplicissimus',
        'Solidarität',
        'Solothurner Zeitung',
        'Sonntags-Blatt',
        'SonntagsBlick',
        'Sonntagszeitung',
        'Spektrum',
        'spektrumdirekt',
        'Sportzeitung',
        'St. Galler Tagblatt',
        'St. Galler Volksblatt',
        'Stern',
        'Stuttgarter Nachrichten',
        'Süddeutsche Zeitung',
        'Südkurier',
        'Südostschweiz',
        'SZ Magazin',
        'Tages-Anzeiger',
        'Tägliche Rundschau',
        'Thurgauer Zeitung',
        'Thüringer Allgemeine',
        'Tierfreund',
        'Tiroler Tageszeitung',
        'Trend',
        'Trierische Landeszeitung',
        'TT Kompakt',
        'TV-Media',
        'Urania',
        'VDI nachrichten',
        'Völkischer Beobachter',
        'Völkischer Beobachter (Bayernausgabe)',
        'Völkischer Beobachter (Berliner Ausgabe)',
        'Völkischer Beobachter (Norddeutsche Ausgabe)',
        'Völkischer Beobachter (Reichsausgabe)',
        'Volksstimme',
        'Volkszeitung für den Weichselgau',
        'Vorarlberger Nachrichten',
        'Vorwärts',
        'Vorwärts (Abend)',
        'Vossische Zeitung',
        'Vossische Zeitung (Abend-Ausgabe)',
        'Vossische Zeitung (Montags-Ausgabe)',
        'Vossische Zeitung (Morgen-Ausgabe)',
        'Vossische Zeitung (Sonntags-Ausgabe)',
        'Welt am Sonntag',
        'Welt und Wissen',
        'Weltwoche',
        'Weser-Kurier',
        'Westdeutsche Zeitung',
        'Westfälische Neueste Nachrichten',
        'Wettiner Zeitung',
        'Wiener',
        'Wienerin',
        'Wiener Bezirksblatt',
        'Wiener Zeitung',
        'Wirtschaftsblatt',
        'Wochenpost',
        'Wohnen im Grünen',
        'Woman',
        'Zeit Campus',
        'Zeit Geschichte',
        'Zeit Magazin',
        'Zeit Wissen',
        'Zürcher Tagesanzeiger',
    )

    # don't strip PIs as they are the foundation of Oxygen's comment function
    _stripping_parser = et.XMLParser(remove_comments=True)
    _stripping_parser2 = et.XMLParser(remove_comments=True, remove_pis=True)

    BASE_URL = 'lex://lex.dwds.de/'

    def __init__(self, start='.', strip=False):
        self.start = start
        self.strip = strip

    
    def __iter__(self):
        
        for root, _, files in sorted(os.walk(self.start, followlinks=True)):
            
            if root in ('./.git', './sandbox', './scripts', './share', './stuff'):
                continue
            
            for f in sorted(files):
                if f.endswith('.xml'):
                    try:
                        docroot = et.parse(
                                os.path.join(root, f),
                                parser=self._stripping_parser2 if self.strip else self._stripping_parser
                        ).getroot()
                        c = -1
                        for c, entry in enumerate(docroot.findall(self.TAGS['Artikel'])):
                            yield entry, os.path.join(root, f)
                        else:
                            if c == -1:
                                logging.warning(os.path.join(root, f), 'contains no entry')
                    except et.XMLSyntaxError as error:
                        logging.critical('While parsing %s:', os.path.join(root, f))
                        logging.critical(error.args)
                        exit(-1)
    
    def url(self, path):
        return self.BASE_URL.rstrip('/')+'/'+urllib.parse.quote(path.strip().lstrip('./'))

    def get_wordclass(self, entry):
        wc = set([ self.text(w) for w in entry.findall('.//%(Wortklasse)s' % self.TAGS) ])
        if '' in wc:
            wc.remove('')
        if len(wc) == 0:
            return None
        elif len(wc) == 1:
            return wc.pop()
        else:
            logging.error('In: %s:', self.get_headwords(entry)[0])
            raise ValueError(self.get_headwords(entry)[0], wc)


    def get_headwords(self, entry, only_main_lemmas=False):
        _xpath = './%(Formangabe)s/%(Schreibung)s' % self.TAGS
        if only_main_lemmas:
            _xpath = './%(Formangabe)s[@Typ="Hauptform" or @Typ="Nebenform"]/%(Schreibung)s[not(@Typ)]' % self.TAGS

        return list(dict.fromkeys( [
                (' '.join(self.text(h).split())+'#'+h.get('hidx', '')).rstrip('#')
                for h in et.ETXPath(_xpath)(entry)
        ] ) ) # as of python 3.7 the key sequence in dicts is fixed so this returns unique lemmas in their precise order

    def text(self, element, shallow=False, normalize=True):
        t = ''.join(et.ETXPath('./text()')(element)) if shallow else ''.join(et.ETXPath('.//text()')(element))
        return ' '.join(t.split()).strip() if normalize else t

    def report(self, entry, path, message='', verbose=True):
        if verbose == True:
            print(f'{self.COLORS["red"]}{self.get_headwords(entry)[0]}{self.COLORS["reset"]} ({entry.get("Status", "")}) -- {message}' if entry is not None else f'[anonymous message] -- {message}')
        else:
            if path is not None:
                print(self.url(path), end=' ', flush=True)
            else:
                print('No path.')
                exit(-1)

    def asciify(self, s):
        s = unicodedata.normalize('NFC', s.strip())
        for c, subst in (
            ('ä', 'ae'),
            ('Ä', 'Ae'),
            ('À', 'A'),
            ('á', 'a'),
            ('à', 'a'),
            ('â', 'a'),
            ('ã', 'a'),
            ('å', 'a'),
            ('Å', 'A'),
            ('č', 'c'),
            ('ç', 'c'),
            ('ć', 'c'),
            ('é', 'ee'),
            ('É', 'Ee'),
            ('è', 'e'),
            ('ê', 'e'),
            ('Ê', 'e'),
            ('ë', 'e'),
            ('î', 'i'),
            ('í', 'i'),
            ('ñ', 'n'),
            ('ö', 'oe'),
            ('Ö', 'Oe'),
            ('ó', 'o'),
            ('ô', 'o'),
            ('ø', 'oe'),
            ('œ', 'oe'),
            ('Œ', 'Oe'),
            ('ř', 'r'),
            ('ş', 's'),
            ('ß', 'ss'),
            ('ẞ', 'SS'), # LATIN CAPITAL LETTER SHARP S
            ('ü', 'ue'),
            ('Ü', 'Ue'),
            ('ú', 'u'),
            ('ù', 'u'),
            ('û', 'u'),
            ('ž', 'z'),
            ('²', '2'),
            ('³', '3'),
            ('₂', '2'),
            ('₀', '0'),
            ('α', 'ALPHA'),
            ('β', 'BETA'),
            ('γ', 'GAMMA'),
            ('µ', 'MIKRO'),
            (' ', '_'),
            ('’', '_'),
            (',', '_'),
            ('!', ''),
            ('…', ''),
            ('×', 'x'),
            ('€', 'EURO'),
        ):
            s = s.replace(c, subst)

        try:
            s.encode('ascii')
        except UnicodeEncodeError:
            print(f'asciify() cannot handle {s}')
            exit(-1)
        if s != '':
            return s
        else:
            raise ValueError('Empty string')

    def generate_stimuli(self, cand, form):
        wc = [ self.text(w) for w in form.findall('.//%(Wortklasse)s' % self.TAGS) ]
        if wc != [''] and wc != []:
    
            stimulus = self.asciify(cand)
            stimulus = stimulus.replace('etw.', 'etwas').replace('jmd.', 'jemand').replace('jmdm.', 'jemandem').replace('jmdn.', 'jemanden').replace('jmds.', 'jemandes')
    
            # special case nouns
            if wc[0] == 'Substantiv':
                for g in set([ self.text(g) for g in form.findall('.//%(Genus)s' % self.TAGS) ]):
                    yield {'mask.': 'der_', 'fem.': 'die_', 'neutr.': 'das_', 'ohne erkennbares Genus': ''}[g] + stimulus, wc[0]
            else:
                yield stimulus, wc[0]

    def is_visible(self, element):

        if element.get('class') == 'invisible':
            return False
        else:
            parent = element.getparent()
            if parent is None:
                return True
            else:
                return self.is_visible(parent)


    def recently_modified(self, path):

        if int(time.time() - self.__NOW) > 0:
            self.NOW = int(datetime.datetime.now(pytz.utc).strftime('%s'))
            self.__NOW = time.time()

        f = pathlib.Path(path)
        if self.NOW - f.stat().st_mtime < self.MAX_FILE_AGE:
            return True
        else:
            return False

    def n_address(self, element, tail=None):
        if element.tag != self.TAGS['Lesart'] and tail is None:
            return None
        elif element.tag != self.TAGS['Lesart']:
            return tail
        else:
            n = element.get('n', '').strip().rstrip('.)')
            if tail is None:
                tail = ''
            return self.n_address(element.getparent(), n+' '+tail)

    def tagname(self, qname):
        return self._REVERSED_TAGS[qname]

    def is_visible(self, element):
        if element.get('class', '').strip() == 'invisible':
            return False
        else:
            parent = element.getparent()
            if parent is not None:
                return(self.is_visible(parent))
        return True


if __name__ == '__main__':
    wb = Wb()
