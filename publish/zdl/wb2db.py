#!/usr/bin/env python3
# encoding: utf8

'''
Fill the DWDSWB database with XML+Metadata for dictionary entries.
'''

import argparse, logging
import collections, unicodedata, datetime
import MySQLdb, _mysql_exceptions
import lxml.etree as et

from exist import ExistDB


def text_only(element, strip=True):
    text = ''.join(et.ETXPath('.//text()')(element))
    if strip:
        text = text.strip()
    return ' '.join(text.split())


class Dictionary(object):
    '''
    '''

    def __init__(self, file_names, remote=False):
        self.file_names = file_names
        self.remote = remote

    def __iter__(self):
        '''Iterate over entries, a dictionary's basic building blocks.
        '''
        parser = et.XMLParser(
            load_dtd=True,
            remove_comments=True,
            remove_pis=True,
            resolve_entities=True
        )

        # it doesn't make sense to use both local and remote articles
        # so we ignore local files if -r is given on the command line
        if not self.remote:
            for file_name in self.file_names:

                tree = et.parse(file_name, parser)
                for entry in et.ETXPath('.//%s' % self.ENTRY_ELEMENT)(tree.getroot()):
                    yield entry
        else:
            db = ExistDB('http://spock.dwds.de:8080/exist')
            for resource_name in db.__iter__(dirname='/dwdswb/data'):
                if '=' in resource_name:
                    logging.warning('Bogus resource name: %s', resource_name)
                root = db.get(resource_name, strip=True)
                if root is not None:
                    for entry in et.ETXPath('.//%s' % self.ENTRY_ELEMENT)(root):
                        yield entry

    def set_version(self, cursor, version):
        '''
        '''
        if cursor is not None:

            if version is None:
                version = '%s' % datetime.datetime.now().strftime('%Y-%m-%d')

            logging.info('Setting version information: %s', version)
            cursor.execute('DROP FUNCTION IF EXISTS dictionary_version;')
            cursor.execute('CREATE FUNCTION dictionary_version () RETURNS CHAR(%i) RETURN "%s";' % (len(version), version))

    def create_tables(self, cursor):
        '''
        '''
        if cursor is not None:

            logging.info('Dropping existing tables.')
            cursor.execute('DROP TABLE IF EXISTS article;')
            cursor.execute('DROP TABLE IF EXISTS lemma;')
            cursor.execute('DROP TABLE IF EXISTS token;')
            if self.USE_RELATIONS:
                cursor.execute('DROP TABLE IF EXISTS relation;')

            logging.info('Creating tables from scratch.')
            cursor.execute('''CREATE TABLE IF NOT EXISTS article (
                    id INT(11) NOT NULL,
                    type CHAR(32) DEFAULT "Minimalartikel",
                    status CHAR(32) DEFAULT "in_Bearbeitung",
                    source CHAR(32) DEFAULT "DWDS",
                    recommendation TINYINT(1) default 0,
                    date DATE NOT NULL,
                    xml MEDIUMTEXT COLLATE utf8_unicode_ci NOT NULL,
                    PRIMARY KEY (id),
                    KEY status (status)
                    ) ENGINE=MyISAM
                    DEFAULT CHARSET=utf8;
            ''')
            cursor.execute('''CREATE TABLE IF NOT EXISTS lemma (
                    id INT(11) NOT NULL,
                    lemma VARCHAR(50) COLLATE utf8_unicode_ci NOT NULL,
                    hidx TINYINT(2) DEFAULT NULL,
                    type VARCHAR(10),
                    article_id INT(11) NOT NULL,
                    PRIMARY KEY (id),
                    KEY lemma (lemma),
                    KEY article_id(article_id, lemma)
                    ) ENGINE=MyISAM
                    DEFAULT CHARSET=utf8;
            ''')
            cursor.execute('''CREATE TABLE IF NOT EXISTS token (
                    token VARCHAR(50) COLLATE utf8_unicode_ci NOT NULL,
                    lemma_id INT(11) NOT NULL,
                    KEY token (token)
                    ) ENGINE=MyISAM
                    DEFAULT CHARSET=utf8;
            ''')
            if self.USE_RELATIONS:
                cursor.execute('''CREATE TABLE IF NOT EXISTS relation(
                        article1 INT(11) NOT NULL,
                        article2 INT(11) NOT NULL,
                        type VARCHAR(20) COLLATE utf8_unicode_ci NOT NULL,
                        KEY article1 (article1, article2, type),
                        KEY article2 (article2, article1, type)
                        ) ENGINE=MyISAM;
                ''')

    def prune(self, article):
        '''
        '''
        raise NotImplementedError

    def is_publishable(self, article):
        '''This can be used as a hook for certain dictionaries.
        '''
        return True

    def first_lemma(self, article):
        try:
            lemma = article.findall(self.HEADWORD_PATH)[0]
        except IndexError:
            logging.critical('Entry without headword\n%s', et.tostring(article))
            raise
        hidx = lemma.get('hidx') or ''
        return (text_only(lemma) + '%' + hidx).rstrip('%')


class DWDSWB(Dictionary):
    '''
    '''

    DATABASE_NAME = 'dwdswb_beta'
    ENTRY_ELEMENT = et.QName('http://www.dwds.de/ns/1.0', 'Artikel')
    HEADWORD_PATH = './*/{http://www.dwds.de/ns/1.0}Schreibung'
    # NOTE: only morphological relations!
    RELATION_PATH = './{http://www.dwds.de/ns/1.0}Verweise/{http://www.dwds.de/ns/1.0}Verweis'
    USE_RELATIONS = True

    def __init__(self, file_names, remote=False):
        Dictionary.__init__(self, file_names, remote=remote)

    def prune(self, article):
        '''
        '''

        for element in et.ETXPath('.//*[@class="invisible"]')(article):
            element.getparent().remove(element)

        for element in et.ETXPath('.//*[@Originaltext]')(article):
            element.attrib.pop('Originaltext')

    def is_publishable(self, article):
        # see http://odo.dwds.de/mantis/view.php?id=30407
        # return article.get('Status') in ('Red-f')
        return article.get('Status') in ('Red-f', 'Red-2', 'Lex-zur_Abgabe', u'Red-f-zurückgewiesen')


class EtymWB(Dictionary):
    '''
    '''

    DATABASE_NAME = 'etymwb_beta'
    ENTRY_ELEMENT = et.QName('http://www.tei-c.org/ns/1.0', 'entry')
    HEADWORD_PATH = './/{http://www.tei-c.org/ns/1.0}orth'  # all embedded forms
    USE_RELATIONS = False

    def __init__(self, file_names):
        Dictionary.__init__(self, file_names)

    def prune(self, article):
        pass


class WDG(Dictionary):
    '''
    '''

    DATABASE_NAME = 'wdg_beta'
    ENTRY_ELEMENT = et.QName('http://www.tei-c.org/ns/1.0', 'entry')
    HEADWORD_PATH = './/{http://www.tei-c.org/ns/1.0}orth'
    USE_RELATIONS = False

    def __init__(self, file_names):
        Dictionary.__init__(self, file_names)

    # TODO: overload __iter__() to return comp1 initial data and comp2 nests

    def prune(self, article):
        pass


class DWB1(Dictionary):
    '''
    '''

    DATABASE_NAME = 'dwb1'
    ENTRY_ELEMENT = et.QName('http://www.tei-c.org/ns/1.0', 'entry')
    HEADWORD_PATH = './/{http://www.tei-c.org/ns/1.0}orth'  # all embedded forms
    USE_RELATIONS = False

    def __init__(self, file_names):
        Dictionary.__init__(self, file_names)

    # TODO: overload __iter__() to also catch <re> as separate entries?

    def prune(self, article):
        pass


class Bucket(object):
    '''
    '''

    def __init__(self, name, cursor, query, max_size=1000):
        '''
        '''
        self.query = query
        self.data = set([])
        self.name = name
        self.max_size = max_size
        self.cursor = cursor

    def update(self, data):
        '''
        '''
        if len(self.data) >= self.max_size:
            self.flush()
        if data not in self.data:
            self.data.add(data)

    def flush(self):
        '''
        '''
        logging.info('Flushing bucket %s (%i rows).', self.name, len(self.data))
        if self.data and self.cursor is not None:
            self.cursor.executemany(self.query, self.data)
        self.data = set([])


if __name__ == '__main__':

    # CLI argument handling
    argument_parser = argparse.ArgumentParser(description='Feed lexical data (DWDS-XML) into the lexical database.')
    argument_parser.add_argument('--host', default='localhost', metavar='HOST', type=str, help='host name or IP address of the database server')
    argument_parser.add_argument('--user', default='', metavar='USERNAME', type=str, help='username for the database server')
    argument_parser.add_argument('--passwd', default='', metavar='PASSWORD', type=str, help='passphrase for the database server')
    argument_parser.add_argument('input_files', metavar='FILE', type=str, nargs='*', help='(list of) file names')
    argument_parser.add_argument('-t', '--dictionary-type', choices=('dwdswb', 'etymwb', 'wdg', 'dwb1'), help='set the type of dictionary that is used', metavar='TYPE', required=True)
    argument_parser.add_argument('-r', '--remote', action='store_true', default=False, help='use a remote repository (eXist, dwdswb only)')
    argument_parser.add_argument('-v', '--verbose', action='store_true', default=False, help='enable verbose diagnostic messages')
    argument_parser.add_argument('-V', '--version', type=str, metavar='VERSION_NUMBER', help='specify the version number')
    argument_parser.add_argument('-w', '--write', action='store_true', default=False, help='actually write to DB')
    arguments = argument_parser.parse_args()

    # set up logging
    if arguments.verbose is True:
        logging.basicConfig(format='%(message)s', level=logging.INFO)
    else:
        logging.basicConfig(format='%(message)s', level=logging.WARNING)

    # set up dictionary type
    if arguments.dictionary_type == 'dwdswb':
        dictionary = DWDSWB(arguments.input_files, remote=arguments.remote)
    elif arguments.dictionary_type == 'etymwb':
        dictionary = EtymWB(arguments.input_files)
    elif arguments.dictionary_type == 'wdg':
        dictionary = WDG(arguments.input_files)
    elif arguments.dictionary_type == 'dwb1':
        dictionary = DWB1(arguments.input_files)

    # set up DB connection
    cursor = None
    if arguments.write:
        database_credentials = {
            'host': arguments.host,
            'user': arguments.user,
            'passwd': arguments.passwd,
        }
        try:
            db = MySQLdb.connect(db=dictionary.DATABASE_NAME, **database_credentials)
            db.set_character_set('utf8')
            cursor = db.cursor()
            logging.debug('Using %s as DB cursor.', cursor)
            cursor.execute('SET NAMES utf8;')
            cursor.execute('SET CHARACTER SET utf8;')
            cursor.execute('SET CHARACTER_SET_SERVER=utf8;')
            cursor.execute('SET CHARACTER_SET_DATABASE=utf8;')
            cursor.execute('SET CHARACTER_SET_CONNECTION=utf8;')
            cursor.execute('SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";')
            dictionary.create_tables(cursor)
            dictionary.set_version(cursor, arguments.version)
        except _mysql_exceptions.OperationalError as error:
            logging.critical(error)
            exit(-1)
    else:
        logging.warning('I will NOT actually write to DB.')

    # relations can only be heuristically determined based on lemmas at present
    lemma_index = collections.defaultdict(list)
    lemma_count = 0
    relation_index = collections.defaultdict(list)

    # to speed up MySQL INSERTs we use a bucket and executemany()
    bucket_article = Bucket('article', cursor, 'INSERT INTO article VALUES (%s, %s, %s, %s, %s, %s, %s);', max_size=2000)
    bucket_lemma = Bucket('lemma', cursor, 'INSERT INTO lemma VALUES (%s, %s, %s, %s, %s);', max_size=10000)
    bucket_token = Bucket('token', cursor, 'INSERT INTO token VALUES (%s, %s);', max_size=50000)
    bucket_relation = Bucket('relation', cursor, 'INSERT INTO relation VALUES (%s, %s, %s);', max_size=50000)

    for index, article in enumerate(dictionary, 1):

        dictionary.prune(article)

        if dictionary.is_publishable(article):

            bucket_article.update(
                (index,
                 article.get('Typ') or article.get('type'),
                 article.get('Status'),
                 article.get('Quelle'),
                 True if article.get('Empfehlung') == 'ja' else False,
                 article.get('Zeitstempel') or '1000-01-01',
                 et.tostring(article) if article.get('Status') in ('Red-f', None) else '')
            )

            # extract lemmas
            for position, lemma in enumerate(article.iterfind(dictionary.HEADWORD_PATH)):
                hidx = lemma.get('hidx')
                htype = lemma.get('Typ') or 'AR_G'
                # splitting and unicode mangling is needed for TEI dictionaries'
                # use of orth@expand and the text compression present in headwords
                headwords = [
                    headword.replace('_', ' ')
                    for headword in (str(lemma.get('expand') or u'').split())
                ]
                if not headwords:
                    headwords = [str(text_only(lemma)) or u'']
                for headword in headwords:
                    headword = u''.join([
                        character
                        for character in unicodedata.normalize('NFC', headword)
                        if unicodedata.category(character) in ('Ll', 'Lu', 'Pd', 'Po', 'Zs', 'Nd', 'No', ) or character == u'’'
                    ]).replace(u'’', "'")
                    if headword and not (lemma_count, headword, hidx, htype, index, ) in bucket_lemma.data:
                        lemma_count += 1
                        bucket_lemma.update((lemma_count, headword, hidx, htype, index, ))
                        lemma_index[(headword, hidx)].append(index)

                        for token in headword.split():
                            bucket_token.update( (token, lemma_count) )


            # extract morphological relations (first pass)
            if dictionary.USE_RELATIONS and article.get('Status') == 'Red-f':
                for link in article.iterfind(dictionary.RELATION_PATH):
                    hidx = link[0].get('hidx')
                    relation_index[(index, link.get('Typ'))].append((text_only(link[0]).replace(u'’', "'"), hidx))

        else:  # not(dictionary.is_publishable(article))
            logging.debug('Cannot publish article "%s"', dictionary.first_lemma(article))

    else:
        # flush the remaining (possible) half-full buckets
        bucket_article.flush()
        bucket_lemma.flush()
        bucket_token.flush()

    # update morphological relations (second pass)
    # NOTE: this only works if the lexical data are globally consistent
    # with regard to morphological links and if they are processed completely
    # -- else the lookup for (lemma, hidx) tuples will fail, of course.
    # If only parts of dictionary are processed, this is expected.
    if dictionary.USE_RELATIONS:
        for (article1, relation_type), targets in relation_index.items():
            for target in targets:
                article2 = lemma_index[target]
                if not article2:
                    logging.warning(
                        'Cannot find %s word lemma "%s" (%s) in lemma index, skipping.',
                        'single' if len(target[0].split()) <= 1 else 'multi',
                        target[0],
                        target[1],
                    )
                else:
                    bucket_relation.update((article1, article2[0], relation_type))
        else:
            bucket_relation.flush()
