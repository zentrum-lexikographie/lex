#!/usr/bin/env python
# encoding: utf-8

import logging, os
import codecs, re, lxml.etree as et

class Lexicon(object):
    '''
    '''

    TEI_NS = 'http://www.tei-c.org/ns/1.0'
    XML_NS = 'http://www.w3.org/XML/1998/namespace'
    XML_ID = et.QName(XML_NS, 'id')
    ALL_ENTRIES = './/%s' % et.QName(TEI_NS, 'entry')
    ALL_DIVS = './/%s/%s' % (et.QName(TEI_NS, 'body'), et.QName(TEI_NS, 'div'))

    headwordsPattern = re.compile(ur'''
            (?P<id>E_[a-z]_\d+(_\d+)?)\t
            (?P<headword>[\w\-\.'’\,&!]+(%(?P<hidx>\d))?)\t
            (?P<type>main|sub|comp1|comp2|deriv|comp1\-embedded|comp2plus|comp1plus)\t
            (?P<pos>[A-Z]+(\|[A-Z]+)*)\t
            (?P<gram>\w+)\s*
    ''', re.U | re.VERBOSE)

    _typeMap = {
        'block': 0, # only for div, deprecated
        'main': 1,
        'sub': 1,
        'comp1': 2,
        'comp2': 3,
        'deriv': 4, # deprecated
        'comp1-embedded': 5, # deprecated
        'comp2plus': 3, # deprecated
        'comp1plus': 2, # deprecated
    }

    # notwendig, um eine logische Liste
    # in einem einzelnen Datenbankfeld zu speichern:
    _escape = lambda self, s: s.replace(' ', '_')

    logging = logging.getLogger(__name__)


    def __init__(self, pathToData):
        
        self._entryCache = {}
        self.pathToData = pathToData
        self.lexiconType = None # to be set in child classes


    def readHeadwords(self):
        # read headword list and update cache accordingly
        headwordsFileName = os.sep.join((self.pathToData, 'headwords.'+self.lexiconType+'.txt', ))
        f = codecs.open(headwordsFileName, encoding='utf-8')
        self.logging.debug('Reading %s', f.name)
        for counter, line in enumerate(f):
            match = self.headwordsPattern.match(line)
            if match is None:
                self.logging.error('Syntax error on line %d (ignored)',
                        counter+1)
            else:
                id = match.group('id')
                headword = match.group('headword')
                type = match.group('type')
                try:
                    self._entryCache[id]['lemma'].append(headword)
                except KeyError:
                    self._entryCache[id] = {
                            'lemma': [headword, ],
                            'type': self._typeMap[type],
                            'id': len(self._entryCache),
                    }
        self.logging.info('%d entries', len(self._entryCache))


    def parseRendered(self):
        # read pre-rendered entries if appropriate for the ressource
        for section in self.sections:
            renderFileName = os.sep.join(
                    (self.pathToData, 'src', self.lexiconType+section+'.xml.rendered', )
            )
            f = codecs.open(renderFileName, encoding='utf-8')
            self.logging.debug('Reading %s', f.name)
            
            for line in f:
                id, text = line.split('\t', 1)
                self._entryCache[id]['renderedText'] = text


    def parseSource(self, start='entry'):
        for section in self.sections:
            xmlFileName = os.sep.join((self.pathToData, 'src', self.lexiconType+section+'.xml', ))
            self.logging.debug('Parsing %s', xmlFileName)
            xmlTree = et.parse(xmlFileName)
            xmlRoot = xmlTree.getroot()
            if start == 'entry':
                for entry in et.ETXPath(self.ALL_ENTRIES)(xmlRoot):
                    self.updateCache(entry)
            elif start == 'div':
                for div in et.ETXPath(self.ALL_DIVS)(xmlRoot):
                    self.updateCache(div)

    def updateCache(self, entry):
        raise NotImplementedError('BUG: To be implemented in derived classes.')

    def updateDb(self, cursor):
        raise NotImplementedError('BUG: To be implemented in derived classes.')


class EtymWB(Lexicon):
    '''
    '''

    logging = logging.getLogger('etymwb')


    def __init__(self, pathToData):
        
        super(EtymWB, self).__init__(pathToData)
        self.lexiconType = 'etymwb'
        self.sections = 'abcdefghijklmnopqrstuvwxyz'
        self.readHeadwords()


    def updateCache(self, entry):

        # set up XPaths
        TAG = {}
        for tag in ('form', 'orth', 'div', 'gramGrp', 'sense'):
            TAG[tag] = et.QName(self.TEI_NS, tag)
        ALL_LEMMAS = './%(form)s/%(orth)s' % TAG
        ALL_SUBLEMMAS = './%(div)s//%(orth)s' % TAG
        SENSE = './%(sense)s/*' % TAG
        GRAMMAR = './%(form)s/%(gramGrp)s/*' % TAG

        id = entry.get(self.XML_ID)
        
        # find data in the sources and update cache accordingly
        lemmas = [ '%'.join([e.text or '', e.get('hidx') or '']).strip('%')
                for e in et.ETXPath(ALL_LEMMAS)(entry)
        ]
        self._entryCache[id]['lemma'] = lemmas
        
        sublemmas = [ '%'.join([(e.text or '').replace(' ', '_'), e.get('hidx') or '']).strip('%')
                for e in et.ETXPath(ALL_SUBLEMMAS)(entry)
        ]
        self._entryCache[id]['sublemmas'] = sublemmas

        grammarText = ' '.join([ e.text or ''
                for e in et.ETXPath(GRAMMAR)(entry) ]
        )
        self._entryCache[id]['grammar'] = grammarText

        senseText = ' '.join([ e.text or ''
                for e in et.ETXPath(SENSE)(entry) ]
        )
        self._entryCache[id]['sense'] = senseText


    def updateDb(self, cursor):
        
        self.logging.info('Clearing EtymWB table ...')
        cursor.execute('DELETE FROM etymwb_eintrag;')
        
        self.logging.info('Flushing EtymWB table ...')
        feeder = (
            (
                None,
                id,
                u' '.join(data['lemma']),
                u' '.join(data['sublemmas']),
                data['grammar'],
                data['sense'],
                data['renderedText'],
            )
            for id, data in self._entryCache.iteritems()
        )
        cursor.executemany('''INSERT INTO etymwb_eintrag
                VALUES (%s, %s, %s, %s, %s, %s, %s);''', feeder)
        self.logging.info('Building index ...')
        cursor.execute('DROP INDEX etymwb_id ON etymwb_eintrag;')
        cursor.execute('ALTER TABLE etymwb_eintrag ADD INDEX(etymwb_id);')
        self.logging.debug('Done.')


class DWDSWB(Lexicon):
    '''
    '''

    _rendMap = {
            'sep:arrow': u'\u2192',
            'ldelim:slash': '/',
            'rdelim:slash': '/',
    }

    logging = logging.getLogger('dwdswb')

    def __init__(self, pathToData):
        
        super(DWDSWB, self).__init__(pathToData)
        self.lexiconType = 'dwdswb'
        self.sections = 'abcdefghijklmnopqrstuvwxyz+'

        # additional caches for database tables
        self._divCache = []
        self._senseCache = []
        self._linkCache = []
        self._grammarCache = []
        self._citationCache = []
        self._citQuotationCache = []
        self._speechCache = []

        self.readHeadwords()
        
        # prepare tag names
        self.TAG = {}
        for tag in ('def', 'usg', 'form', 'orth', 'div', 'gramGrp', 'sense', 'etym', 'xr', 'lbl', 'ref', 'w', 'cit', 'entry', 'note', 'pos'):
                self.TAG[tag] = et.QName(self.TEI_NS, tag)


    def updateCache(self, div, level=0, parent=-1):
        text = [ t.text for t in et.ETXPath('./%(note)s' % self.TAG)(div) ]
        links = et.ETXPath('./%(xr)s' % self.TAG)(div)
        linkIDs = self.getLinks(links)

        type = div.get('type')
        
        self._divCache.append((level, type, parent, text, linkIDs))
        myID = len(self._divCache)

        # process <entry> (only direct children!)
        for entry in div.findall('./%(entry)s' % self.TAG):
            self.updateEntryCache(entry, parent=myID)

        # process embedded <div>s
        for subDiv in div.findall('./%(div)s' % self.TAG):
            self.updateCache(subDiv, level+1, myID)


    def updateEntryCache(self, entry, parent):

        # set up XPaths
        ETYMOLOGY = './%(etym)s' % self.TAG
        LINKS = './%(xr)s' % self.TAG
        ALL_FORMS = './%(form)s' % self.TAG

        xmlID = entry.get(self.XML_ID)
        dbID = self._entryCache[xmlID]['id']
        
        # collect data for main table dwdswb_entry
        lemmas, rule = self.getLemmas(et.ETXPath(ALL_FORMS)(entry))
        lemmasValid = filter(lambda x: not x[2].endswith('U'), lemmas)
        lemmasInvalid = set(lemmas) - set(lemmasValid)
        self._entryCache[xmlID]['lemma_valid'] = [l for _, l, _ in lemmasValid]
        self._entryCache[xmlID]['lemma_invalid'] = [l for _, l, _ in lemmasInvalid]
        self._entryCache[xmlID]['orth_rule'] = (rule or '').split()
        self._entryCache[xmlID]['parent_div'] = parent
        self._entryCache[xmlID]['etymology'] = self.renderEtymology(et.ETXPath(ETYMOLOGY)(entry))
        self._entryCache[xmlID]['usage'] = self.renderUsage(entry)
        self._entryCache[xmlID]['link'] = self.getLinks(et.ETXPath(LINKS)(entry))
        self._entryCache[xmlID]['grammar'] = self.getGrammar(et.ETXPath(ALL_FORMS)(entry))

        for sense in entry.findall('./%(sense)s' % self.TAG):
            self.updateSenseCache(sense, dbID)


    def updateSenseCache(self, sense, parentEntry, parentSense=-1, level=0):
        LINKS = './%(xr)s' % self.TAG
        ALL_FORMS = './%(form)s' % self.TAG

        myID = len(self._senseCache) + 1
        self._senseCache.append(
            (
                myID, # id
                sense.get(self.XML_ID), # DWDSWB_id
                level, 
                sense.get('n') or '', # n
                parentSense,
                parentEntry,
                0, # frequency, TODO
                u' '.join(self.getGrammar(et.ETXPath(ALL_FORMS)(sense))),
                self.renderNote(sense),
                self.renderDefinition(sense),
                self.renderUsage(sense),
                self.renderPragmatics(sense),
                u' '.join(self.getLinks(et.ETXPath(LINKS)(sense)))
            )
        )

        # collect examples/citations
        # Achtung: *erst* Beispiele, dann eingebettete Lesarten suchen,
        # weil so die Beispiele im dwdswb-Panel einfach nach id
        # sortiert werden koennen, um die richtige Reihenfolge zu erhalten
        for cit in et.ETXPath('./%(cit)s' % self.TAG)(sense):
            self._citationCache.append(self.getCitation(cit, parentEntry, myID))

        # process all (only direct children) <sense>
        for subSense in et.ETXPath('./%(sense)s' % self.TAG)(sense):
            self.updateSenseCache(subSense, parentEntry, myID, level+1)
        

    def renderQuote(self, element):
        # im Moment können hier noch <add type="addendum"> und
        # <add type="paraphrase" auftreten, daher rekursiv
        parts = []
        if element.get('type') == 'addendum':
            parts.append('['+element.text+']')
        elif element.get('type') == 'paraphrase':
            parts.append('(='+element.text+')')
        else:
            # Belegtextteil
            parts.append(element.text or '')
            for subelement in element:
                parts.append(self.renderQuote(subelement))
        parts.append(element.tail or '')
        
        return ' '.join(parts)


    def getCitation(self, cit, parentEntry, parentSense):
        # XPaths vorbereiten
        BIB_AUTHOR = './/%s' % et.QName(self.TEI_NS, 'author')
        BIB_TITLE = './/%s' % et.QName(self.TEI_NS, 'title')
        BIB_SCOPE = './/%s' % et.QName(self.TEI_NS, 'biblScope')
        BIB_NOTE = './/%s/%s' % (et.QName(self.TEI_NS, 'bib'),
                et.QName(self.TEI_NS, 'note'))
        BIB_NOTE2 = './/%s/%s' % (et.QName(self.TEI_NS, 'ref'),
                et.QName(self.TEI_NS, 'bibl'))
        PARAPHRASES = './%s' % et.QName(self.TEI_NS, 'add')
        USAGES = './%s[@type!="plev"]' % et.QName(self.TEI_NS, 'usg')
        NOTES = './%s' % et.QName(self.TEI_NS, 'note')
        
        type = cit.get('type')
        text = self.renderQuote(cit[0]) # eigentliche Belegstelle rendern
        
        # Paraphrase(n) -- falls vorhanden
        paraphrase = []
        for element in et.ETXPath(PARAPHRASES)(cit):
            paraphrase.append(self._escape(element.text or ''))

        # Gebrauchsangaben -- falls vorhanden
        usage = []
        for element in et.ETXPath(USAGES)(cit):
            usage.append(self._escape(element.text or ''))
        
        # sonstige Bemerkungen -- falls vorhanden
        note = []
        for element in et.ETXPath(NOTES)(cit):
            note.append(self._escape(element.text or ''))

        # bibliographische Angaben
        element = et.ETXPath(BIB_AUTHOR)(cit)
        author = (element[0].text or '') if element else ''
        element = et.ETXPath(BIB_TITLE)(cit)
        title = (element[0].text or '') if element else ''
        element = et.ETXPath(BIB_SCOPE)(cit)
        scope = (element[0].text or '') if element else ''
        element = et.ETXPath(BIB_NOTE)(cit)
        bibNote = (element[0].text or '') if element else ''

        # FIXME: dirty hack um automatisch generierte Beispiele einspielen zu koennen
        if not author or title or scope or bibNote:
            element = et.ETXPath(BIB_NOTE2)(cit)
            bibNote = (element[0].text or '') if element else ''
        
        # alles einpacken fuer die DB
        data = (
                None, type, parentEntry, parentSense,
                text, ' '.join(paraphrase), ' '.join(usage), ' '.join(note),
                author, title, scope, bibNote,
        )
        return data


    def getGrammar(self, forms):
        grammarIDs = []
        for form in forms:
            ALL_ORTHS = et.ETXPath('./%(orth)s' % self.TAG)(form)
            ALL_GRAMS = et.ETXPath('./%(gramGrp)s/*' % self.TAG)(form)
            pos = et.ETXPath('./%(gramGrp)s/%(pos)s' % self.TAG)(form)

            lemmas = [ self._escape(o.get('expand') or o.text)
                    for o in ALL_ORTHS ]
            grams = []
            tag, type = None, None
            for position, gram in enumerate(ALL_GRAMS):
                newTag = gram.tag
                newType = gram.get('type')
                text = gram.text
                if grams and text:
                    # Trenner nur, wenn's schon was anzuzeigen gibt
                    if newTag == tag and newType == type:
                        grams.append('/')
                    else:
                        grams.append('; ')
                if text is not None:
                    if newTag.endswith('colloc') and newTag != tag:
                        grams.append('in Verbindung mit ')
                    if newType == 'plural' and len(grams) == 0:
                        grams.append('Plural: ')
                    grams.append(text)
                tag = newTag
                type = newType
            usage = self.renderUsage(form)
            self._grammarCache.append(
                    (' '.join(lemmas), ''.join(grams), usage)
            )
            grammarIDs.append(str(len(self._grammarCache)))
        return grammarIDs or ['-1', ]


    def getLinks(self, xrs):
        linkIDs = []
        for xr in xrs:
            type = xr.get('type')
            label = xr[0].text if type == 'UNCLASSIFIED' else ''
            for ref in et.ETXPath('./%(ref)s' % self.TAG)(xr):
                sidx = ref.get('sidx') or ''
                cRef = ref.get('cRef')
                lemmas = [ u'%'.join([w.text or '', w.get('hidx') or '']).strip('%') for w in ref ]
                # update links cache
                self._linkCache.append((type, cRef, ' '.join(lemmas), sidx, label))
                linkIDs.append(str(len(self._linkCache)))
        return linkIDs or ['-1', ]


    def getLemmas(self, forms):
        # @rank wird erstmal ignoriert in der Sortierung
        ALL_ORTHS = './%(orth)s' % self.TAG
        lemmas = []
        lemmaCache = {}
        rule = None
        for form in forms:
            rule = rule or form.get('rule')
            for orth in et.ETXPath(ALL_ORTHS)(form):
                rank = orth.get('rank') or '1'
                lemma = orth.get('expand') or self._escape(orth.text)
                valid = orth.get('type') or ''
                if not lemma in lemmaCache:
                    lemmas.append( (int(rank), lemma, valid, ) )
                    lemmaCache[lemma] = True
        
        # lemmas nicht sortieren (Quelltextreihenfolge behalten)
        return lemmas, rule


    def renderUsage(self, parent):
        # Achtung: @type=='Pragmatik' hat ein eigenes Feld!
        usage = et.ETXPath('./%(usg)s[@type!="Pragmatik"]' % self.TAG)(parent)
        if usage:
            return u' '.join( [ self._escape(u.text) for u in usage ] )
        return ''


    def renderDefinition(self, parent):
        definition = et.ETXPath('./%(def)s' % self.TAG)(parent)
        if definition:
            return u' '.join( [ self._escape(d.text) for d in definition ] )
        return ''

    
    def renderNote(self, parent):
        note = et.ETXPath('./%(note)s' % self.TAG)(parent)
        if note:
            return u' '.join( [ self._escape(n.text) for n in note ] )
        return ''

    
    def renderPragmatics(self, parent):
        pragmatics = et.ETXPath('./%(usg)s[@type="Pragmatik"]' % self.TAG)(parent)
        if pragmatics:
            return u' '.join( [ self._escape(p.text) for p in pragmatics ] )
        return ''

    
    def renderEtymology(self, etym):
        if etym == []:
            return ''
        else:
            text = []
            for lang in etym[0]:
                text.append(lang.text)
                rend = lang.get('rend')
                text.append('&#8594;' if rend == 'sep:arrow' else ', ')
            return u''.join(text[ : -1]) # letztes typographisches Zeichen weg


    def updateDb(self, cursor):
        
        # main table: dwdswb_entry
        self.logging.info('Clearing dwdswb_entry ...')
        cursor.execute('DELETE FROM dwdswb_entry;')
        self.logging.info('Flushing dwdswb_entry ...')
        feeder = (
            (
                data['id'],
                id,
                u' '.join(data['lemma_valid']),
                u' '.join(data['lemma_invalid']),
                u' '.join(data['orth_rule']),
                data['type'],
                data['parent_div'],
                data['etymology'],
                data['usage'],
                u' '.join(data['link']),
                u' '.join(data['grammar']),
            )
            if len(data) == 11
            else (data['id'], id, '', '', '', -1, -1, '', '', '', '')
            for id, data in self._entryCache.iteritems()
        )
        cursor.executemany('''INSERT INTO dwdswb_entry
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);''', feeder)

        # div table: dwdswb_div
        self.logging.info('Clearing dwdswb_div ...')
        cursor.execute('DELETE FROM dwdswb_div;')
        self.logging.info('Flushing dwdswb_div (%i rows) ...', len(self._divCache))
        feeder = (
                (
                    counter+1,
                    data[0],
                    self._typeMap[data[1]],
                    data[2],
                    '; '.join(data[3]),
                    ' '.join(data[4]),
                )
                for counter, data in enumerate(self._divCache)
        ) # counter+1 because 0 means autoincrement
        cursor.executemany('''INSERT INTO dwdswb_div
                VALUES (%s, %s, %s, %s, %s, %s);''', feeder)

        # sense table: dwdswb_sense
        self.logging.info('Clearing dwdswb_sense ...')
        cursor.execute('DELETE FROM dwdswb_sense;')
        self.logging.info('Flushing dwdswb_sense (%i rows) ...', len(self._senseCache))
        sensesTotal = len(self._senseCache)
        chunkSize = 10000
        for chunk in xrange(0, len(self._senseCache), chunkSize):
            feeder = ( data for data in self._senseCache[chunk : chunk + chunkSize] )
            self.logging.debug('chunk %i:%i (%i rows)',
                    chunk, chunk + chunkSize - 1,
                    len(self._senseCache[chunk : chunk + chunkSize]))
            cursor.executemany('''INSERT INTO dwdswb_sense
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);''', feeder)

        # link table: dwdswb_link
        self.logging.info('Clearing dwdswb_link ...')
        cursor.execute('DELETE FROM dwdswb_link;')
        self.logging.info('Flushing dwdswb_link (%i rows) ...', len(self._linkCache))
        feeder = (
            (
                counter+1,
                data[0] or '',
                data[1] or '',
                data[2] or '',
                data[3] or '',
                data[4] or '',
            )
                for counter, data in enumerate(self._linkCache)
        ) # counter+1 because 0 means autoincrement
        cursor.executemany('''INSERT INTO dwdswb_link
                VALUES (%s, %s, %s, %s, %s, %s);''', feeder)

        # grammar table: dwdswb_grammar
        self.logging.info('Clearing dwdswb_grammar ...')
        cursor.execute('DELETE FROM dwdswb_grammar;')
        self.logging.info('Flushing dwdswb_grammar (%i rows) ...',
                len(self._grammarCache))
        feeder = (
            (
                counter+1,
                data[0],
                data[1],
                data[2],
            )
            for counter, data in enumerate(self._grammarCache)
        ) # counter+1 because 0 means autoincrement
        cursor.executemany('''INSERT INTO dwdswb_grammar
                VALUES (%s, %s, %s, %s);''', feeder)


        # citation table
        self.logging.info('Clearing dwdswb_citation ...')
        cursor.execute('DELETE FROM dwdswb_citation;')
        self.logging.info('Flushing dwdswb_citation (%i rows) ...',
                len(self._citationCache))
        chunkSize = 20000
        for chunk in xrange(0, len(self._citationCache), chunkSize):
            self.logging.debug('chunk %i:%i (%i rows)',
                    chunk, chunk + chunkSize - 1,
                    len(self._citationCache[chunk : chunk + chunkSize]))
            cursor.executemany('''INSERT INTO dwdswb_citation
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s);''',
                    self._citationCache[chunk : chunk + chunkSize])

        self.logging.info('Building citation indexes ...')
        cursor.execute('DROP INDEX entry_id ON dwdswb_citation;')
        cursor.execute('DROP INDEX sense_id ON dwdswb_citation;')
        cursor.execute('ALTER TABLE dwdswb_citation ADD INDEX(entry_id);')
        cursor.execute('ALTER TABLE dwdswb_citation ADD INDEX(sense_id);')

        self.logging.info('Building sense indexes ...')
        cursor.execute('DROP INDEX parent_entry ON dwdswb_sense;')
        cursor.execute('DROP INDEX parent_sense ON dwdswb_sense;')
        cursor.execute('ALTER TABLE dwdswb_sense ADD INDEX(parent_entry);')
        cursor.execute('ALTER TABLE dwdswb_sense ADD INDEX(parent_sense);')

        self.logging.info('Building entry indexes ...')
        cursor.execute('DROP INDEX parent_div ON dwdswb_entry;')
        cursor.execute('DROP INDEX dwdswb_id ON dwdswb_entry;')
        cursor.execute('ALTER TABLE dwdswb_entry ADD INDEX(parent_div);')
        cursor.execute('ALTER TABLE dwdswb_entry ADD INDEX(dwdswb_id);')

        self.logging.info('Building div index ...')
        cursor.execute('DROP INDEX parent_div ON dwdswb_div;')
        cursor.execute('ALTER TABLE dwdswb_div ADD INDEX(parent_div);')
        self.logging.debug('Done.')


class WDG(Lexicon):
    '''
    '''

    logging = logging.getLogger('wdg')

    def __init__(self, pathToData):
        
        super(WDG, self).__init__(pathToData)
        self.lexiconType = 'wdg'
        self.sections = 'abcdefghijklmnopqrstuvwxyz'
        self.readHeadwords()


    def updateDb(self, cursor):
        self.logging.info('Clearing WDG table ...')
        cursor.execute('DELETE FROM wdg_entry;')

        # with max_allowed_packet=16M for MySQL we still have to
        # split the WDG into smaller chunks ...
        self.logging.info('Flushing WDG table ... (%i rows)',
                len(self._entryCache))
        chunkSize = 15000
        keys = self._entryCache.keys()
        for chunk in xrange(0, len(self._entryCache), chunkSize):
            # dict lookup is much faster than list search:
            idChunkList = keys[chunk : chunk + chunkSize]
            idChunk = dict(map(lambda x: (x, None), idChunkList))
            feeder = (
                (
                    None,
                    id,
                    u' '.join(data['lemma']),
                    data['renderedText'],
                )
                for id, data in self._entryCache.iteritems() if id in idChunk
            )
            self.logging.debug('chunk %i:%i (%i rows)',
                    chunk, chunk + chunkSize - 1, len(idChunk))
            cursor.executemany('''INSERT INTO wdg_entry
                    VALUES (%s, %s, %s, %s);''', feeder)
        self.logging.info('Building index ...')
        cursor.execute('DROP INDEX wdg_id ON wdg_entry;')
        cursor.execute('ALTER TABLE wdg_entry ADD INDEX(wdg_id);')
        self.logging.debug('Done.')


class Wortwarte(object):

    def __init__(self, fileName):
        self._cache = []
        wortwarte = codecs.open(fileName, encoding='utf8')
        self.logging.debug('Reading %s', wortwarte.name)
        for entry in wortwarte:
            _, lemma, ID, definition = entry.split('\t')
            definition = definition.strip()
            self._cache.append((lemma, definition, ID))
        self.logging.info('%d entries', len(self._cache))


    def updateDb(self, cursor):
        self.logging.info('Clearing Wortwarte table ...')
        cursor.execute('DELETE FROM wortwarte_eintrag;')
        feeder = ( 
                (
                    None,
                    data[0],
                    data[1],
                    data[2],
                ) 
                for data in self._cache
        )
        cursor.executemany('''INSERT INTO wortwarte_eintrag
                VALUES (%s, %s, %s, %s);''', feeder)
        self.logging.info('Building lemma index ...')
        cursor.execute('DROP INDEX lemma ON wortwarte_eintrag;')
        cursor.execute('ALTER TABLE wortwarte_eintrag ADD INDEX(lemma);')
        self.logging.debug('Done.')
