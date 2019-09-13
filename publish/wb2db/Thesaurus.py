#!/usr/bin/env python
# encoding: utf-8

import logging, os
import codecs, re, collections
import ThesaurusData

class OpenThesaurus(object):
    '''
    '''


    logging = logging.getLogger('ot')

    SYNSET_PATTERN = re.compile(r'''
        ^\-\|
        (?P<synset>.+)
        $
    ''', re.U | re.VERBOSE)

    HYPERONYM_PATTERN = re.compile(r'^(?P<wordform>.+)\s\(Oberbegriff\)$', re.U)

    WORDFORM_PATTERN = re.compile(r'''
        ^
        (?P<wordform>.+)
        (\s\((?P<usage>.+?)\))
        $
    ''', re.U | re.VERBOSE)

    USAGE_MARKERS = ThesaurusData.OT_USAGE_MARKERS
    USAGE_IGNORE = ThesaurusData.OT_MARKERS_IGNORE

    def __init__(self, pathToData):
        
        self._synsetCache = {}
        self._wordformCache = {}
        self._lineCache = {}
        self._usageCache = collections.defaultdict(int)
        self.pathToData = pathToData
        self.readSynsets()


    def readSynsets(self):
        otFile = os.sep.join([self.pathToData, 'th_de_DE_v2.dat'])
        for line in codecs.open(otFile, encoding='utf-8'):
            if not line in self._lineCache:
                self._lineCache[line] = None
                match = self.SYNSET_PATTERN.match(line)
                if match is not None:
                    index = len(self._synsetCache)
                    self._synsetCache[index] = {
                        'hyperonym': [],
                    }
                    self.updateWordformCache(match.group('synset'), index)
                else:
                    pass
            else:
                pass
                # self.logging.warning('Ignoring duplicate line: %s', line)
        self.logging.debug('%i synsets', len(self._synsetCache))
        self.logging.debug('%i word forms', len(self._wordformCache))
        if self._usageCache:
            self.logging.warning('%i unknown usage marker(s)', len(self._usageCache))
            for u in sorted(self._usageCache.keys()):
                self.logging.warning('%s (%i)', u, self._usageCache[u])
            self.logging.warning('%i unknown usage marker(s)', len(self._usageCache))


    def updateWordformCache(self, s, index):
        for wf in s.split('|'):
            hyperonym = self.HYPERONYM_PATTERN.match(wf)
            if hyperonym is not None:
                hwf, usg = self.parseWordformUsage(hyperonym.group('wordform'))
                self._synsetCache[index]['hyperonym'].append(hwf)
            else:
                wordform, usage = self.parseWordformUsage(wf)
                if wordform is not None:
                    self._wordformCache[len(self._wordformCache)] = {
                            'wordform': wordform,
                            'synset': index,
                            'usage': ' '.join(usage),
                    }
                else:
                    self.logging.error('Cannot parse line: %s', s)


    def parseWordformUsage(self, line, usage=set([])):
        '''Lemma und Gebrauchsangaben trennen.
        '''
        match = self.WORDFORM_PATTERN.match(line)
        if match is None:
            return line or None, usage
        else:
            try:
                return self.parseWordformUsage(match.group('wordform'),
                        usage.union(self.USAGE_MARKERS[match.group('usage')])
                )
            except KeyError:
                if not match.group('usage') in self.USAGE_IGNORE:
                    self._usageCache[match.group('usage')] += 1
                return line or None, usage


    def updateDb(self, cursor):
        self.logging.info('Clearing openthesaurus_synset ...')
        cursor.execute('DELETE FROM openthesaurus_synset;')
        self.logging.info('Flushing openthesaurus_synset ...')
        feeder = (
                (
                    None,
                    id,
                    u' '.join( [ i.replace(' ', '_')
                            for i in data['hyperonym'] ] ),
                )
                for id, data in self._synsetCache.iteritems()
        )
        cursor.executemany('''INSERT INTO openthesaurus_synset
                VALUES (%s, %s, %s);''', feeder)

        self.logging.info('Clearing openthesaurus_wordform ...')
        cursor.execute('DELETE FROM openthesaurus_wordform;')
        self.logging.info('Flushing openthesaurus_wordform ...')
        feeder = (
                (
                    None,
                    data['wordform'],
                    data['synset'],
                    data['usage'] or '',
                )
                for id, data in self._wordformCache.iteritems()
        )
        cursor.executemany('''INSERT INTO openthesaurus_wordform
                VALUES (%s, %s, %s, %s);''', feeder)
        self.logging.info('Building wordform indexes ...')
        cursor.execute('DROP INDEX lemma ON openthesaurus_wordform;')
        cursor.execute('DROP INDEX synset ON openthesaurus_wordform;')
        cursor.execute('ALTER TABLE openthesaurus_wordform ADD INDEX(lemma);')
        cursor.execute('ALTER TABLE openthesaurus_wordform ADD INDEX(synset);')

        self.logging.info('Building synset indexes ...')
        cursor.execute('DROP INDEX synset_id ON openthesaurus_synset;')
        cursor.execute('ALTER TABLE openthesaurus_synset ADD INDEX(synset_id);')
        self.logging.debug('Done.')
