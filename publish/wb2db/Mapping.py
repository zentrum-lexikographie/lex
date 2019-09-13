#!/usr/bin/env python
# encoding: utf-8

import logging, sys
import codecs, re, os, subprocess
import datetime, time
import random, hashlib

class MetaLemmaList(object):
    '''
    '''

    mappingPattern = re.compile(ur'''
        (?P<global_id>\d+)\t
        (?P<lemma>[\w\-\.'’\,&!]+)\t
        (?P<dwdswb_id>E_[a-z]_\d+(_\d+)?|NONE)\t
        (?P<dwdswb_hidx>\d+)\t
        (?P<etymwb_id>E_[a-z]_\d+(_\d+)?|NONE)\t
        (?P<etymwb_hidx>\d+)\t
        (?P<wdg_id>E_[a-z]_\d+(_\d+)?|NONE)\t
        (?P<wdg_hidx>\d+)\s*
    ''', re.U | re.VERBOSE)

    logging = logging.getLogger('MetaLL')

    def __init__(self, fileName):
        
        self._cache = {}
        f = codecs.open(fileName, encoding='utf-8')
        self.logging.debug('Reading %s', f.name)
        for counter, line in enumerate(f):
            match = self.mappingPattern.match(line)
            if match is None:
                self.logging.error('Syntax error on line %d (ignored)', counter+1)
            else:
                self._cache[match.group('global_id')] = match.groupdict()

        self.logging.info('%d relations', len(self._cache))


    def updateDb(self, cursor):
        '''Update the database.
        
        Note: Using cursor.executemany() with a list (or generator) is 
        often *much* faster than iterating over the data and
        doing single INSERTs from Python directly.
        '''

        self.logging.info('Clearing metalemma table ...')
        cursor.execute('DELETE FROM metalemmaliste_element;')

        self.logging.info('Flushing metalemma table ...')
        feeder = (
                (
                    None,
                    id,
                    data['lemma'],
                    data['dwdswb_id'],
                    data['dwdswb_hidx'] or 0,
                    data['etymwb_id'],
                    data['etymwb_hidx'],
                    data['wdg_id'],
                    data['wdg_hidx'],
                )
                for id, data in self._cache.iteritems()
        )
        cursor.executemany('''INSERT INTO metalemmaliste_element
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s);''', feeder)
        self.logging.info('Building lemma index ...')
        cursor.execute('DROP INDEX lemma ON metalemmaliste_element;')
        cursor.execute('ALTER TABLE metalemmaliste_element ADD INDEX(lemma);')
        # for headword list lookups:
        cursor.execute('DROP INDEX wdg_id ON metalemmaliste_element;')
        cursor.execute('ALTER TABLE metalemmaliste_element ADD INDEX(wdg_id);')
        cursor.execute('DROP INDEX dwdswb_id ON metalemmaliste_element;')
        cursor.execute('ALTER TABLE metalemmaliste_element ADD INDEX(dwdswb_id);')
        cursor.execute('DROP INDEX etymwb_id ON metalemmaliste_element;')
        cursor.execute('ALTER TABLE metalemmaliste_element ADD INDEX(etymwb_id);')
        self.logging.debug('Done.')


class AudioMapping(object):
    '''
    '''

    mappingPattern = re.compile(ur'''
        (?P<dwdswb_id>E_[a-z]_\d+(_\d+)?)\t
        (?P<stimulus>[,\'\-\w]+)\t
        (?P<stichwort>[,\'\-\w]+)\t
        (?P<packet>\d+)\t
        (?P<display>\d+)\t
        (?P<status>\d+)\s*
    ''', re.U | re.VERBOSE)

    # silent, constant bit rate: 64kBit/s
    # ENCODER = ('lame', '-S', '--cbr', '-b64')
    # silent, variable bit rate, quality 4 (medium in [0 : 9])
    # with 0 -- best, 9 -- worst
    ENCODER = ('lame', '-S', '-V', '4')
    FILESYSTEM_ENCODING = sys.getfilesystemencoding()

    logging = logging.getLogger('audio')

    def getUniqueID(self):
        '''Generate a unique and random identifier.
        '''
        ID = None
        while True:
            currentTime = time.time()
            randomValue = random.random()
            ID = hashlib.md5(str(randomValue)+str(currentTime)).hexdigest()
            if ID in self._uniqueIDCache:
                self.logging.warning('Clashing hashes, retrying ...')
            else:
                self._uniqueIDCache[ID] = None
                break 
        return ID


    def __init__(self, dirName, dwdswb, write):
        self.sourceDirName = dirName
        self.cache = {}
        self._uniqueIDCache = {}
        self.write = write
        
        self.resultDirName = dirName.rstrip(os.sep)+'_web'
        if self.write:
            try:
                os.mkdir(self.resultDirName)
            except OSError, message:
                raise IOError(message)
        else:
            self.logging.warning('NOT writing files: encoding errors in filenames might pass unnoticed.')

        fileName = os.sep.join([dirName, 'mapping.txt'])
        f = codecs.open(fileName, encoding='utf-8')
        self.logging.debug('Reading %s', f.name)
        for counter, line in enumerate(f):
            match = self.mappingPattern.match(line)
            if match is None:
                self.logging.error('Syntax error on line %d (ignored)', counter+1)
            else:
                dwdswbID, _, stimulus, stichwort, packet, display, status = match.groups()
                entryTableID = -1
                try:
                    entryTableID = dwdswb._entryCache[dwdswbID]['id']
                except KeyError:
                    self.logging.warning('%s (phonDB) missing in DWDSWB (ignored, please update phonDB!)', dwdswbID)
                description = {
                        'id': counter,
                        'stimulus': stimulus,
                        'packet': packet,
                        'parent_entry': entryTableID,
                        'basename': self.getUniqueID(),
                        'rank': int(display),
                }
                if not dwdswbID in self.cache:
                    self.cache[dwdswbID] = []
                self.cache[dwdswbID].append(description)
                
                if status != '6' or self.prepareAudioFile(**description) != 0:
                    # nicht zur Veröffentlichung oder
                    # irgendwas ist schiefgegangen, besser wieder rausnehmen
                    self.cache[dwdswbID].remove(description)
        else:
            self.logging.info('%i sound files (for %i articles)',
                    counter, len(self.cache))

        self.logging.info('Adjusting display rank ...')
        for key in self.cache.keys():
            if len(self.cache[key]) > 1:
                self.cache[key] = sorted(self.cache[key],
                        cmp=lambda x, y: 1 if x['rank'] >= y['rank'] else -1)


    def updateDb(self, cursor):
        '''Update the database.
        '''
        self.logging.info('Clearing dwdswb_speech table ...')
        cursor.execute('DELETE FROM dwdswb_speech;')

        self.logging.info('Flushing dwdswb_speech ...')
        currentTime = str(datetime.datetime.today())
        feeder = (
            (
                data['id'],
                data['parent_entry'],
                data['basename'],
                1,
                currentTime,
            )
            for audioDescription in self.cache.itervalues()
            for data in audioDescription
        )
        cursor.executemany('''INSERT INTO dwdswb_speech
                VALUES (%s, %s, %s, %s, %s);''', feeder)
        self.logging.info('Building index ...')
        cursor.execute('DROP INDEX key_index ON dwdswb_speech;')
        cursor.execute('ALTER TABLE dwdswb_speech ADD INDEX(key_index);')
        self.logging.debug('Done.')
    

    def prepareAudioFile(self, packet=None, stimulus=None, basename=None, **kwargs):
        '''Prepare a directory self.RESULT_DIR_NAME with renamed mp3 files.

        This directory should then be transferred to the web server.
        '''
        mp3FileName = os.sep.join( (self.resultDirName, basename+'.mp3') )
        returnValue = 0
        try:
            wavFileName = os.sep.join(
                (self.sourceDirName, 'Paket_'+packet, stimulus+'.wav')
            ).encode('utf-8') # (self.FILESYSTEM_ENCODING) -- sollte man meinen,
            # klappt aber nicht, daher utf8 festkodiert
            returnValue = subprocess.call(self.ENCODER+(wavFileName, mp3FileName))
        except UnicodeEncodeError, msg:
            self.logging.warning('%s when processing %s (Paket_%s), ignored',
                    msg, stimulus, packet)
            returnValue = 1

        if returnValue != 0:
            self.logging.warning('Problem converting %s (Paket_%s), ignored',
                    stimulus, packet)
        
        return returnValue
