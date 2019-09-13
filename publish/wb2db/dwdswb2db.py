#!/usr/bin/env python
# encoding: utf-8

import logging, optparse, os, re, collections
import MySQLdb
import lxml.etree as ET
import settings

##################################################
## TODO: executemany auf list(dict) umstellen
## (geht erst bei MySQLdb-Versionen ab Ende 2012),
## bei wheezy noch nicht
##################################################

TAG = dict( [ (t, ET.QName('http://www.dwds.de/ns/1.0', t))
        for t in ('Artikel', 'Diachronie', 'Definition', 'Paraphrase',
            'Formangabe', 'Schreibung', 'Grammatik',
            'Diasystematik',
            'Verweis', 'Verweise', 'Ziellemma',
            'reflexiv', 'indeklinabel',
            'FlexionsklasseSingular', 'FlexionsklassePlural',
            'Flexionsallomorph', 'Wortklasse', 'Numeruspraeferenz',
            'Genus', 'Genitiv', 'Plural', 'Komparativ', 'Superlativ',
            'Praesens', 'Praeteritum', 'Partizip_II',
            'Lesarten', 'Lesart',
            'Belege', 'Beleg', 'Belegtext', 'Kontext', 'Stichwort',
            'Kompetenzbeispiel', 'Fundstelle', 'Sigle', 'Konstruktionsmuster',
            'Frequenz', 'Lesart',
            'w', 'note',
        )
] )
TAG['id'] = ET.QName('http://www.w3.org/XML/1998/namespace', 'id')

HIDX = { None: '', '1': u'¹', '2': u'²', '3': u'³', '4': u'⁴', '5': u'⁵', '6': u'⁶', '7': u'⁷', '8': u'⁸', '9': u'⁹', }

SHORT_GRAM_FIELDS = (
        TAG['Genus'],
        TAG['Genitiv'],
        TAG['Plural'],
        TAG['Komparativ'],
        TAG['Superlativ'],
        TAG['Praesens'],
        TAG['Praeteritum'],
        TAG['Partizip_II'],
        TAG['Numeruspraeferenz'],
        TAG['indeklinabel'],
        TAG['reflexiv'],
)


class Transfer_Bucket:

    __MAX_BUCKET_SIZE = 10000

    _entries_total = 0L
    _senses_total = 0L
    _forms_total = 0L

    def entries_total(self):
        return self._entries_total + len(self.entries)

    def senses_total(self):
        return self._senses_total + len(self.senses)

    def forms_total(self):
        return self._forms_total + len(self.forms)

    def __init__(self, cursor):
        self.cursor = cursor
        self.clear()

    def clear(self):
        self.entries = []
        self.senses = []
        self.forms = []
        self.links = []
        self.snippets = []

    def flush(self, force=False, write=False):
        if self.entries and (len(self.entries) % self.__MAX_BUCKET_SIZE == 0 or force) and write == True:
            logging.debug('Flushing bucket ...')
            cursor.executemany('INSERT INTO dwdswb2_entries VALUES (%s, %s, %s, %s, %s, %s, %s, %s);', self.entries)
            cursor.executemany('INSERT INTO dwdswb2_forms VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);', self.forms)
            cursor.executemany('INSERT INTO dwdswb2_senses VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s);', self.senses)
            cursor.executemany('INSERT INTO dwdswb2_links VALUES (%s, %s, %s, %s, %s, %s, %s, %s);', self.links)
            cursor.executemany('INSERT INTO dwdswb2_snippets VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);', self.snippets)

            self._entries_total += len(self.entries)
            self._senses_total += len(self.senses)
            self._forms_total += len(self.forms)

            self.clear()

        else:
            pass


def all_entries(file_names):
    for filename in file_names:
        logging.debug('Reading %s', filename)
        root = ET.parse(filename).getroot()
        for a in ET.ETXPath('.//%(Artikel)s' % TAG)(root):
            yield a


def generate_snippet_rank(s):
    c = s.get('class')
    if c is None:
        return 2
    elif 'good_example' in c:
        return 1
    elif 'invisible' in c:
        return 0
    else:
        return 0


def generate_short_gram(form):
    bits = []
    for gram in ( e for e in ET.ETXPath('./%(Grammatik)s/*' % TAG)(form) if e.tag in SHORT_GRAM_FIELDS):
        if gram.tag == TAG['reflexiv']:
            bits.append('reflexiv')
        elif gram.tag == TAG['indeklinabel']:
            bits.append('indeklinabel')
        elif gram.tag in SHORT_GRAM_FIELDS and gram.text:
            bits.append(gram.text) if gram.text else ''
        else:
            pass

        look_ahead = gram.getnext()
        if bits and look_ahead is not None and look_ahead.tag == gram.tag:
            bits[-1] = bits[-1] + '/'
        elif bits and look_ahead is not None:
            bits[-1] = bits[-1] + ', '
        else:
            pass

    return ''.join(filter(lambda x: x, bits)).rstrip(' ,/')


def parent(element, tag):
    p = element.getparent()
    if p is None or p.tag == tag:
        p
    else:
        return parent(p, tag)

def get_sense_depth(s, depth=1):
    p = s.getparent()
    if p.tag == TAG['Lesart']:
        return get_sense_depth(p, depth+1)
    else:
        return depth


def update_links(element, bucket):
    # w - deprecated
    for xr in ET.ETXPath('./%(Verweise)s/%(Verweis)s' % TAG)(element):
        targets = [ w for w in ET.ETXPath('.//%(w)s' % TAG)(xr) ]
        targets.extend( w for w in ET.ETXPath('.//%(Ziellemma)s' % TAG)(xr) )
        new_link = (
                0, # auto increment
                bucket.entries_total(),
                -1 if element.tag == TAG['Artikel'] else bucket.senses_total(),
                xr.get('type'),
                '',  # note (deprecated)
                targets[0].text or '',
                targets[0].get('hidx'),
                xr[0].get('sidx'),
        )
        bucket.links.append(new_link)


def parse(entry, bucket, write):

    # .//Artikel
    new_entry = (
            bucket.entries_total() + 1,
            entry.get(TAG['id']),
            entry.get('Typ') or '',
            entry.get('Quelle') or '',
            entry.get('Erstellung'),
            entry.get('Bearbeitung'),
            entry.get('Revision'),
            (''.join(ET.ETXPath('.//%(Diachronie)s//text()' % TAG)(entry))).strip(),
    )
    bucket.entries.append(new_entry)

    # .//Artikel/Formangabe
    form_index = 0 # TODO: XML-Serialisierung übernehmen, wenn @rank fehlt
    for form in ET.ETXPath('./%(Formangabe)s' % TAG)(entry):
        for orth in ET.ETXPath('./%(Schreibung)s' % TAG)(form):
            form_index += 1
            pos = ET.ETXPath('.//%(Wortklasse)s' % TAG)(form)
            infl_class_sg = ET.ETXPath('.//%(FlexionsklasseSingular)s' % TAG)(form)
            infl_class_pl = ET.ETXPath('.//%(FlexionsklassePlural)s' % TAG)(form)
            infl_allomorph = ET.ETXPath('.//%(Flexionsallomorph)s' % TAG)(form)
            new_form = (
                    bucket.forms_total() + 1,
                    bucket.entries_total(),
                    -1,
                    form.get('Regel') or '',
                    orth.text,
                    orth.get('type') or 'G',
                    orth.get('hidx'),
                    orth.get('rank') or form_index+100,
                    '[speech_files]',
                    ', '.join( [ d.text for d in ET.ETXPath('./%(Diasystematik)s/*' % TAG)(form) ] ),
                    ', '.join( [ f.text for f in ET.ETXPath('.//%(Frequenz)s' % TAG)(form) ] ),
                    generate_short_gram(form),
                    '[grammar_full]',
                    pos[0].text if pos else None,
                    None if not infl_class_sg or infl_class_sg[0].text == 'none' else infl_class_sg[0].text,
                    None if not infl_class_pl or infl_class_pl[0].text == 'none' else infl_class_pl[0].text,
                    None if not infl_allomorph else infl_allomorph[0].text,
            )
            bucket.forms.append(new_form)
    
    # .//Artikel/Verweise
    update_links(entry, bucket)

    # .//Artikel/Lesarten (neu)
    for sense in ET.ETXPath('./%(Lesarten)s/%(Lesart)s' % TAG)(entry):
        new_sense = (
            bucket.senses_total() + 1,
            bucket.entries_total(),
            -1, # no recursive senses yet
            0,  # Einbettungstiefe
            '', # no sense identification
            '', # no notes
            '', # no definition
            '', # no usage
            '', # no frequency
        )
        bucket.senses.append(new_sense)

        for snippet in ET.ETXPath('.//%(Beleg)s' % TAG)(sense):
            new_snippet = (
                    0,
                    bucket.senses_total(),
                    'citation',
                    generate_snippet_rank(snippet),
                    ''.join(ET.ETXPath('./%(Belegtext)s//text()' % TAG)(snippet)),
                    '',
                    '',
                    '[sigle]',
                    snippet[1].text,
                    '[paraphrase]',
                    '[definition]',
                    '[usage]',
            )
            if new_snippet[3] != 0: # no invisible snippets into the DB
                bucket.snippets.append(new_snippet)

    # .//Artikel/Lesarten (alt)
    sense_backlinks = {}
    for sense in ET.ETXPath('.//%(Lesart)s' % TAG)(entry):
        parent_sense = parent(sense, TAG['Lesart'])
        restrictions = [ n.text for n in ET.ETXPath('./%(note)s' % TAG)(sense) ]
        restrictions.extend( [ g.text for g in ET.ETXPath('./%(Formangabe)s//*' % TAG)(sense) ] )
        new_sense = (
            bucket.senses_total() + 1,
            bucket.entries_total(),
            sense_backlinks[parent_sense.get(TAG['id'])] if parent_sense is not None else -1,
            get_sense_depth(sense),
            sense.get('n') or '',
            '; '.join( r for r in restrictions if (r or '').strip() ),
            '; '.join( [ d.text or '' for d in ET.ETXPath('./%(Definition)s' % TAG)(sense) ] ),
            ', '.join( [ d.text for d in ET.ETXPath('./%(Diasystematik)s/*' % TAG)(sense) ] ),
            '', # no frequency information yet
        )
        bucket.senses.append(new_sense)
        sense_backlinks[sense.get(TAG['id'])] = bucket.senses_total()

        # .//sense/Verweise
        update_links(sense, bucket)


        for path, snippet_type in (
                ('./%(Kompetenzbeispiel)s', 'example'),
                ('./%(Konstruktionsmuster)s', 'pattern'),
                ('./%(Beleg)s', 'citation'),
        ):
            for snippet in ET.ETXPath(path % TAG)(sense):
                # Siglen rausstreichen
                for sigle in ET.ETXPath('.//%(Sigle)s' % TAG)(snippet):
                    sigle.getparent().remove(sigle)
                new_snippet = (
                        0,
                        bucket.senses_total(),
                        snippet_type,
                        generate_snippet_rank(snippet),
                        ' '.join(ET.ETXPath('./text()')(snippet[0])),
                        '', # keyword
                        '', # right context
                        '[sigle]',
                        ' '.join(ET.ETXPath('.//%(Fundstelle)s//text()' % TAG)(snippet)),
                        '; '.join( [ p.text for p in ET.ETXPath('.//%(Paraphrase)s' % TAG)(snippet) ] ),
                        '; '.join( [ d.text for d in ET.ETXPath('.//%(Definition)s' % TAG)(snippet) ] ),
                        ' '.join( [ d.text for d in ET.ETXPath('.//%(Diasystematik)s/*' % TAG)(snippet) ] ),
                )
                bucket.snippets.append(new_snippet)

    
    bucket.flush(write=write)



if __name__ == '__main__':
    op = optparse.OptionParser('%prog [FILE]...')
    op.add_option('-v', '--verbose',
            action='store_true', 
            default=False,
            help='be verbose')
    op.add_option('-w', '--write',
            action='store_true',
            default=False,
            help='write to DB')
    options, arguments = op.parse_args()

    if options.verbose == True:
        logging.basicConfig(format='%(message)s', level=logging.DEBUG)
    else:
        logging.basicConfig(format='%(message)s', level=logging.INFO)

    # be kind to others ...
    priority = os.nice(10)
    logging.debug('Running with priority %i', priority)

    db = MySQLdb.connect(**settings.db_credits)
    db.set_character_set('utf8')
    cursor = db.cursor()
    cursor.execute('SET NAMES utf8;')
    cursor.execute('SET CHARACTER SET utf8;')
    cursor.execute('SET character_set_connection=utf8;') 
    logging.debug('Database connection: %s', cursor)
    
    if options.write:
        logging.warning('Clearing current data in dwdswb2_* ...')
        cursor.execute('DELETE FROM dwdswb2_entries;')
        cursor.execute('DELETE FROM dwdswb2_forms;')
        cursor.execute('DELETE FROM dwdswb2_senses;')
        cursor.execute('DELETE FROM dwdswb2_links;')
        cursor.execute('DELETE FROM dwdswb2_snippets;')
        try:
            # this fails if no INDEX was added previously
            cursor.execute('DROP INDEX dwdswb_id ON dwdswb2_entries;')
            cursor.execute('DROP INDEX lemma ON dwdswb2_forms;')
            cursor.execute('DROP INDEX targets ON dwdswb2_links;')
        except Exception, msg:
            logging.warning(msg)

    bucket = Transfer_Bucket(cursor)
    
    for index, entry in enumerate(all_entries(arguments)):
        parse(entry, bucket, options.write)
    
    bucket.flush(force=True, write=options.write)
    logging.debug('Processed lexical entries: %i', bucket.entries_total())

    if options.write:
        logging.info('Building indexes ...')
        cursor.execute('ALTER TABLE dwdswb2_entries ADD INDEX(dwdswb_id);')
        cursor.execute('ALTER TABLE dwdswb2_forms ADD INDEX(lemma);')
        cursor.execute('ALTER TABLE dwdswb2_links ADD INDEX(targets);')
    
    cursor.close()
