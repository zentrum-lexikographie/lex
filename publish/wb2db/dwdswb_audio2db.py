#!/usr/bin/env python
# encoding: utf-8

import logging, sys, optparse, os, shutil
import codecs, re
import time, random, hashlib
import MySQLdb
import settings

mappingPattern = re.compile(ur'''
    (?P<dwdswb_id>E_[a-z]_\d+(_\d+)?)\t
    (?P<stimulus>[,\'\-\w]+)\t
    (?P<stichwort>[,\'\-\w!]+)\t
    (?P<packet>\d+)\t
    (?P<display>\d+)\t
    (?P<status>\d+)\s*
''', re.U | re.VERBOSE)

def getUniqueID(cache):
    '''Generate a unique and random identifier.
    '''
    ID = None
    while True:
        currentTime = time.time()
        randomValue = random.random()
        ID = hashlib.md5(str(randomValue) + str(currentTime)).hexdigest()
        if ID in cache:
            logging.info('clashing hashes, retrying ...')
        else:
            cache[ID] = None
            break
    return ID


if __name__ == '__main__':
    op = optparse.OptionParser('%prog [options] AUDIO_DIR')
    op.add_option('-v', '--verbose',
            action='store_true', default=False,
            help='be verbose')
    op.add_option('-w', '--write',
            action='store_true', default=False,
            help='alter the database')
    options, arguments = op.parse_args()
    if options.verbose == True:
        logging.basicConfig(format='%(name)s:\t%(message)s', level=logging.DEBUG)
    else:
        logging.basicConfig(format='%(name)s:\t%(message)s', level=logging.INFO)

    if not len(arguments) == 1:
        logging.error('%s\nType \'%s -h\' for help.',
                op.get_usage(),
                op.get_prog_name())
        exit(-1)

    FS_ENCODING = sys.getfilesystemencoding()
    logging.debug('using file system encoding (%s)', FS_ENCODING)
        
    try:
        mapping = codecs.open(os.sep.join( (arguments[0], 'mapping.txt') ),
            encoding='utf-8')
    except IOError, message:
        logging.error(message)
        sys.exit(-2)

    uniqueIDCache = {}
    destination_dir=arguments[0]+'_web'

    if options.write:
        try:
            os.mkdir(destination_dir)
        except OSError, message:
            logging.error(message)
            exit(-3)

    db = MySQLdb.connect(**settings.db_credits)
    logging.debug('DB connection: %s', db)
    db.set_character_set('utf8')
    cursor = db.cursor()
    cursor.execute('SET NAMES utf8;')
    cursor.execute('SET CHARACTER SET utf8;')
    cursor.execute('SET character_set_connection=utf8;')
    if options.write:
        logging.debug('clearing old data ...')
        cursor.execute('UPDATE dwdswb2_forms SET speech_files=NULL WHERE speech_files IS NOT NULL;')

    for index, line in enumerate(mapping):
        match = mappingPattern.match(line)
        if line.lstrip().startswith('#'):
            pass
        elif match is None:
            logging.error('in line %i: syntax error', index+1)
        elif match.group('status') != '6':
            logging.debug('skipping Paket_%s/%s (status != 6)', match.group('packet'), match.group('stimulus'))
        else:
            current_id = getUniqueID(uniqueIDCache)
            cursor.execute('''
                    SELECT dwdswb2_forms.id, dwdswb2_forms.speech_files
                    FROM dwdswb2_entries, dwdswb2_forms
                    WHERE dwdswb2_entries.id=dwdswb2_forms.entry_id
                        AND dwdswb2_forms.sense_id=-1
                        AND dwdswb2_entries.dwdswb_id=%s
            ''', match.group('dwdswb_id'))
            if options.write:
                for form_id, speech_files in cursor.fetchall():
                    cursor.execute('''
                            UPDATE dwdswb2_forms
                            SET speech_files=%s
                            WHERE id=%s
                    ''', (' '.join((speech_files or '').split()+[current_id]), form_id) )
                shutil.copyfile(os.sep.join( (arguments[0], 'Paket_'+match.group('packet'), match.group('stimulus')+'.mp3') ),
                        os.sep.join( (destination_dir, current_id+'.mp3') ))

            if index and index % 1000 == 0:
                logging.debug('wrote %s', index)

    logging.info('processed %s sound files', len(uniqueIDCache))
    db.close()
