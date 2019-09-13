#!/usr/bin/env python
# encoding: utf-8

import logging, optparse, os
import MySQLdb, lxml.etree as et
import _mysql_exceptions
import Mapping, Lexicon, Thesaurus
import settings

if __name__ == '__main__':
    dbCredits = settings.db_credits
    op = optparse.OptionParser('%prog [options] MAPFILE')
    op.add_option('-v', '--verbose',
        action='store_true', default=False,
	help='be verbose')
    op.add_option('-w', '--write',
            action='store_true', 
            default=False,
            help='write to database')
    op.add_option('--dwdswb',
            action='store',
            default=None,
            metavar='DIR',
            help='use DWDSWB sources in DIR'
    )
    op.add_option('--audio',
            action='store',
            default=None,
            metavar='DIR',
            help='use phon sources in DIR (requires --dwdswb)'
    )
    op.add_option('--etymwb',
            action='store',
            default=None,
            metavar='DIR',
            help='use ETYMWB sources in DIR'
    )
    op.add_option('--wdg',
            action='store',
            default=None,
            metavar='DIR',
            help='use WDG sources in DIR'
    )
    op.add_option('--dwb1',
            action='store',
            default=None,
            metavar='DIR',
            help='use 1DWB sources in DIR'
    )
    op.add_option('--openthesaurus',
            action='store',
            default=None,
            metavar='DIR',
            help='use OpenThesaurus sources in DIR (OpenOffice/LibreOffice version)'
    )
    op.add_option('--wortwarte',
            action='store',
            default=None,
            metavar='FILE',
            help='use Wortwarte definitions in FILE'
    )
    options, arguments = op.parse_args()

    if options.verbose == True:
        logging.basicConfig(format='%(name)s:\t%(message)s', level=logging.DEBUG)
    else:
        logging.basicConfig(format='%(name)s:\t%(message)s', level=logging.INFO)

    # be kind to others ...
    priority = os.nice(15)
    logging.debug('Running with priority %i', priority)

    if not len(arguments) == 1:
        logging.error('%s\nType \'%s -h\' for help.',
                op.get_usage(),
                op.get_prog_name())
        exit(-1)

    try:
        mll = Mapping.MetaLemmaList(arguments[0])
        audio = None
        if options.dwdswb:
            dwdswb = Lexicon.DWDSWB(options.dwdswb)
            dwdswb.parseSource(start='div')
        if options.audio:
            if options.dwdswb is None:
                logging.warning('--audio ignored (--dwdswb not specified)')
                options.audio = None
            else:
                audio = Mapping.AudioMapping(options.audio, dwdswb, write=options.write)
        if options.etymwb:
            etymwb = Lexicon.EtymWB(options.etymwb)
            etymwb.parseRendered()
            etymwb.parseSource()
        if options.wdg:
            wdg = Lexicon.WDG(options.wdg)
            wdg.parseRendered()
        if options.dwb1:
            dwb1 = LexiconDWB1(options.dwb1)
        if options.openthesaurus:
            openthesaurus = Thesaurus.OpenThesaurus(options.openthesaurus)
        if options.wortwarte:
            wortwarte = Lexicon.Wortwarte(options.wortwarte)
    except IOError, message:
        logging.error(message)
        exit(-1)
    except et.XMLSyntaxError, message:
        logging.error(message)
        exit(-1)

    if options.write:
        try:
            db = MySQLdb.connect(**dbCredits)
        except _mysql_exceptions.OperationalError, message:
            logging.critical(message)
            exit(-1)
        db.set_character_set('utf8')
        cursor = db.cursor()
        cursor.execute('SET NAMES utf8;')
        cursor.execute('SET CHARACTER SET utf8;')
        cursor.execute('SET character_set_connection=utf8;') 
        logging.debug('Database connection: %s', cursor)
        mll.updateDb(cursor)
        if options.dwdswb is not None:
            dwdswb.updateDb(cursor)
            if options.audio is not None:
                audio.updateDb(cursor)
        if options.etymwb is not None:
            etymwb.updateDb(cursor)
        if options.wdg is not None:
            wdg.updateDb(cursor)
        if options.dwb1 is not None:
            dwb1.updateDb(cursor)
        if options.openthesaurus is not None:
            openthesaurus.updateDb(cursor)
        if options.wortwarte is not None:
            wortwarte.updateDb(cursor)
        cursor.close()
