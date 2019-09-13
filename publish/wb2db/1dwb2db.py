#!/usr/bin/env python
# encoding: utf-8

# alter table dwb1_entry modify column rendered_text longtext character set utf8 COLLATE utf8_general_ci NOT NULL;

import logging, optparse, os, re
import MySQLdb
import lxml.etree as ET
import lxml.html.soupparser as SP
import settings, orthography

TAG = { 'id':   ET.QName('http://www.w3.org/XML/1998/namespace', 'id') }
for tag in ('entry', 're', 'form', 'orth', 'sense', 
        'cit', 'l', 'def', 'title', 'author', 'gramGrp', 'lb', 'lbl', 'bibl',
        'table', 'row', 'cell', ):
    TAG[tag] = ET.QName('http://www.tei-c.org/ns/1.0', tag)

rend_map = {
        'recte': ('<span style="font-style:normal;">', '</span>'),
        'italics': ('<i style="letter-spacing:0em; text-transform:none;">', '</i>'),
        'uppercase': ('<span style="text-transform:uppercase;">', '</span>'),
        'bold': ('<b>', '</b>'),
        'spaced': ('<span style="letter-spacing: 0.25em;">', '</span>'),
        'caps': ('<span style="font-variant: small-caps;">', '</span>'),
        'inline': ('', ''),
}

tag_map = {
        (TAG['cit'], 'verse'): ('<span style="display:block;margin-top:0.1em;margin-bottom:0.5em;">', '</span>'),
        (TAG['l'], None): ('<br/><span style="margin-left:3em;">', '</span>'),
        (TAG['form'], 'lemma'): ('<span style="text-transform: uppercase;">', '</span>'),
        (TAG['form'], 'subl'): ('<span style="letter-spacing: 0.25em;">', '</span>'),
        (TAG['gramGrp'], None): ('<i>', '</i>'),
        (TAG['def'], None): ('<i>', '</i>'),
        (TAG['title'], None): ('<i>', '</i>'),
        (TAG['title'], 'bible'): ('<i>', '</i>'),
        (TAG['title'], 'head'): ('<i>', '</i>'),
        (TAG['author'], None): ('<span style="font-variant: small-caps;">', '</span>'),
        (TAG['lb'], None): ('<br/>&#160;&#160;', ''),
        (TAG['lbl'], None): ('<i>', '</i>'),
        (TAG['table'], None): ('<table>', '</table>'),
        (TAG['row'], None): ('<tr>', '</tr>'),
        (TAG['cell'], None): ('<td>', '</td>'),
}

entry_counter = 0L


def get_depth(element, depth=0):
    '''
    '''
    
    parent = element.getparent()
    
    if parent is None or parent.tag == TAG['entry']:
        return depth
    else:
        return get_depth(parent, depth+1)


def render(element):
    bucket = []

    text = (element.text or '').replace('&', '&amp;')
    tail = (element.tail or '').replace('&', '&amp;')

    markup_start, markup_stop = '', ''
    try:
        markup_start, markup_stop = tag_map[element.tag, element.get('type')]
    except KeyError:
        pass

    # markup hooks
    
    previous = element.getprevious()
    if element.tag == TAG['bibl'] and previous is not None and previous.tag == TAG['l']:
        markup_start = '<br/><span style="display:block;text-align:right">' + markup_start
        markup_stop = markup_stop + '</span>'
    elif element.tag == TAG['sense']:
        depth = get_depth(element)
        n = element.get('n') or ''
        text = ('&#160;&#160;' if n else '') + n + ' ' + text
        markup_start = '<span style="margin-left:' + str(depth*0.5)+ 'em;">' + markup_start
        markup_stop = markup_stop + '</span>'


    bucket.append(markup_start)

    for rend in (element.get('rend') or '').split():
        bucket.append(rend_map[rend][0])


    bucket.append(text)
    
    for subelement in element:
        
        if subelement.tag == TAG['sense']:
            break
        else:
            bucket.extend(render(subelement))
        
    for rend in reversed((element.get('rend') or '').split()):
        bucket.append(rend_map[rend][1])
    
    bucket.append(markup_stop)

    bucket.append(tail) 

    return bucket


def parse(file_name, parser):
    '''
    '''

    global entry_counter
    
    logging.debug('Parsing %s', file_name)
    root = ET.parse(file_name, parser).getroot()

    entries = []
    forms = []
    senses = []

    for entry in ET.ETXPath(u'( .//%(entry)s | .//%(re)s )' % TAG)(root):
        
        # prepare entries

        entry_counter += 1
        
        metadata = entry.get('meta').split(':')

        logging.debug(entry.get(TAG['id']))
        entries.append( (
            entry_counter,
            entry.get(TAG['id']),
            metadata[0],
            int(metadata[1]) if metadata[1] else 0,
            int(metadata[2]) if metadata[2] else 0,
            metadata[3],
            metadata[4],
            metadata[5],
            int(metadata[6]) if metadata[6] else 0,
            len(''.join(ET.ETXPath('.//text()')(entry))),
            ''.join(render(entry)).strip(),
        ) )

        # prepare forms
    
        for orth in ET.ETXPath(u'./%(form)s/%(form)s/%(orth)s' % TAG)(entry):
            
            lemma_literal = (orth.text or '').rstrip(',.;:')
            lemma_norm = orth.get('expand')
            
            for single_form in lemma_norm.split():
                forms.append( (
                    None,
                    entry_counter,
                    lemma_literal,
                    orthography.fuzzify(lemma_norm),
                ) )

        # prepare senses

        for sense in ET.ETXPath('.//%(sense)s' % TAG)(entry):
            
            raw_text = render(sense)
            tree = ET.fromstring(''.join(raw_text))
            length = len(''.join(ET.ETXPath('.//text()')(tree)))
            
            if length > 55:
                prune_factor = length / 55.0
                raw_intro = ''.join(raw_text).split()
                raw_intro = raw_intro[ : int(len(raw_intro)/prune_factor) ]
                tree = SP.fromstring(' '.join(raw_intro))
                tree.tag = 'span'
                for i in ET.ETXPath('.//br | .//div')(tree):
                    i.tag = 'span'
                intro = ET.tostring(tree)
            else:
                intro = ''

            senses.append( (
                None,
                entry_counter,
                get_depth(sense),
                length,
                intro,
                ''.join(raw_text).strip(),
            ) )

    return entries, forms, senses


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
        logging.warning('Clearing current data in dwb1_* ...')
        cursor.execute('DELETE FROM dwb1_entry;')
        cursor.execute('DELETE FROM dwb1_form;')
        cursor.execute('DELETE FROM dwb1_sense;')
        cursor.execute('DROP INDEX entry_id ON dwb1_sense;')
        cursor.execute('DROP INDEX lemma_fuzzified ON dwb1_form;')

    parser = ET.XMLParser(load_dtd=True, resolve_entities=True)

    for file_name in arguments:
        try:
            entries, forms, senses = parse(file_name, parser)
            if options.write:
                logging.debug('Writing %i entries ...', len(entries))
                cursor.executemany('''INSERT INTO dwb1_entry VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);''', entries)
                cursor.executemany('''INSERT INTO dwb1_form VALUES (%s, %s, %s, %s);''', forms)
                cursor.executemany('''INSERT INTO dwb1_sense VALUES (%s, %s, %s, %s, %s, %s);''', senses)
            else:
                logging.debug('NOT writing data!')
        except IOError, message:
            logging.error(message)
            exit(-1)
        except ET.XMLSyntaxError, message:
            logging.error(message)
            exit(-1)    

    if len(arguments) > 0 and options.write:
        logging.info('Building indexes ...')
        cursor.execute('ALTER TABLE dwb1_form ADD INDEX(lemma_fuzzified);')
        cursor.execute('ALTER TABLE dwb1_sense ADD INDEX(entry_id);')
        logging.info('Done.')
    
    cursor.close()
