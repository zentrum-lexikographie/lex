#!/usr/bin/env python3

import argparse, logging, time, collections, codecs, traceback
from requests.exceptions import ReadTimeout, HTTPError, ConnectionError
import getpass
import readline  # NOQA: readline modifies input()
import lxml.etree as et

from exist import ExistDB
from qa import StructureChecker, TypographyChecker


class ExistShell(object):

    LS_FORMAT = '%(perms)s %(owner)s %(group)s %(ctime)s %(mtime)s'
    PROMPT = 'exist-shell:'
    ACL = (
        ('dwdswb/data/DWDS', 'admin', 'Lexikographen'),
        ('dwdswb/data/Duden-1999', 'admin', 'Duden'),
        ('dwdswb/data/EtymWB', 'admin', 'Minimalartikel'),
        ('dwdswb/data/MWE', 'admin', 'Neuartikel'),
        ('dwdswb/data/Neuartikel', 'admin', 'Neuartikel'),
        ('dwdswb/data/Neuartikel-001', 'admin', 'Neuartikel'),
        ('dwdswb/data/Neuartikel-002', 'admin', 'Neuartikel'),
        ('dwdswb/data/Neuartikel-003', 'admin', 'Neuartikel'),
        ('dwdswb/data/Neuartikel-004', 'admin', 'Neuartikel'),
        ('dwdswb/data/WDG', 'admin', 'Bestandsartikel'),
        ('dwdswb/data/WDG-neu', 'admin', 'Neuartikel'),
    )

    VALID_WORD_FORMATION_TYPES = (
        [],
        ['Konversion'],
        ['Grundform'],
        ['Kurzform'],
        ['Derivation'],
        ['formal_verwandt'],
        ['Erstglied'],
        ['Letztglied'],
        ['Erstglied', 'Letztglied'],
        ['Erstglied', 'Binnenglied', 'Letztglied'],
        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
        ['Erstglied', 'Binnenglied', 'Binnenglied', 'Binnenglied', 'Letztglied'],
    )
    SANITY_CHECKS = (
        ('@Empfehlung only on published full entries', 'for $i in collection("dwdswb/data")//dwds:Artikel[@Empfehlung="ja"][not(@Typ="Vollartikel") or not(@Status="Red-f")] return fn:base-uri($i)'),
        ('no illegal separators in //Kollokation1', 'for $i in collection("dwdswb/data")//dwds:Kollokation1[dwds:Belegtext[fn:contains(text(), ";") or fn:contains(text(), "/")]] return fn:base-uri($i)'),
        ('no illegal separators in //Kollokation2', 'for $i in collection("dwdswb/data")//dwds:Kollokation2[dwds:Belegtext[fn:contains(text(), ";") or fn:contains(text(), "/")]] return fn:base-uri($i)'),
        ('no illegal separators in //Konstruktionsmuster', 'for $i in collection("dwdswb/data")//dwds:Konstruktionsmuster[dwds:Belegtext[fn:contains(text(), ";") or fn:contains(text(), "/")]] return fn:base-uri($i)'),
        ('no illegal separators in //Phrasem', 'for $i in collection("dwdswb/data")//dwds:Phrasem[dwds:Belegtext[fn:contains(text(), ";") or fn:contains(text(), "/")]] return fn:base-uri($i)'),
        ('no illegal mark-up in //Diachronie', u'for $i in collection("dwdswb/data")//dwds:Diachronie[fn:contains(.//text(), "\'") or fn:contains(.//text(), "&quot;") or fn:contains(.//text(), "‘") or fn:contains(.//text(), "’")] return fn:base-uri($i)'),
        ('no //Diasystematik/* on single /Formangabe', 'for $i in collection("dwdswb/data")//dwds:Artikel[dwds:Formangabe/dwds:Diasystematik/*][count(./dwds:Formangabe) eq 1] return fn:base-uri($i)'),
        ('no //Frequenzangabe on single /Formangabe', 'for $i in collection("dwdswb/data")//dwds:Artikel[dwds:Formangabe/dwds:Frequenzangabe][count(./dwds:Formangabe) eq 1] return fn:base-uri($i)'),
    )

    def fn_help(self, _):
        print(
            '''Commands:
debug       enable/disable debugging
check       performs consistency checks
cat         prints the content of a resource in the terminal
get         retrieves a resource to a local directory
ls          lists collections
lock        lock a resource or collection
unlock      release a resource or collection
pwc         prints the current collection
cc          changes the current collection
find        find resources with specified features (lemma, id)
red-2       editing run for Redaktion-2
xquery      returns the results of an XQery request
exit, quit  leaves exist-shell
help        shows a command overview''')

    def fn_exit(self, _):
        self.db.exit()
        print('Bye.')
        exit(0)

    def fn_cat(self, args):
        if len(args) == 0:
            print('don\'t know what to cat')
        else:
            for arg in args:
                target = arg if arg.startswith('/') else self.rel_path + arg

                print(target)
                result = self.db.get(target)
                ns = et.QName(result[0].tag).namespace
                if ns == self.db.NS_MAP['exist']:
                    print('not a resource (try "ls"):', arg)
                else:
                    print(et.tostring(result, encoding=str))

    def fn_get(self, args):
        if len(args) == 0:
            print('don\'t know what to get')
        else:
            for arg in args:
                result = self.db.get(arg) if arg.startswith('/') else self.db.get(self.rel_path + '/' + arg)
                ns = et.QName(result[0].tag).namespace
                if ns == self.db.NS_MAP['exist']:
                    print('not a resource (try "ls"):', arg)
                else:
                    filename = arg.split('/')[-1]
                    with open(filename, 'w') as f:
                        f.write(et.tostring(result, encoding='utf8'))
                        print(arg, '-->', filename)

    def fn_put(self, args):
        if len(args) != 2:
            print('use: put local_name remote_name')
        else:
            resource_input = et.parse(args[0])
            target = args[1] if args[1].startswith('/') else self.rel_path + '/' + args[1]
            self.db.put(target, et.tostring(resource_input))

    def fn_ls(self, args):
        '''List the content of a specified collection or the current collection.
        '''
        target = self.rel_path + ('' if args == [] else '/' + args[0])

        result = self.db.ls(target)
        if result is None:
            print('not a collection')
            return None
        for name, data in sorted(result.items()):
            print(self.LS_FORMAT % data, name)
        else:
            print('total:', len(result))
        return None

    def fn_xquery(self, args):
        start_time = time.time()
        query = ' '.join(args)

        count = 0
        for count, result in enumerate(self.db.xquery(query), 1):
            print(et.tostring(result))
        else:
            print(count, ('hit' if count == 1 else 'hits'), end=' ')
            print('in %0.3f sec' % (time.time() - start_time))

    def _existing_collections_and_resources(self, path='', resources=True):
        '''Returns a list of existing collections (and resources) in the current collection.
        '''
        return [
            name
            for name, _ in filter(
                lambda item: True if item[1]['type'] == 'collection' or resources is True else False,
                self.db.ls(path or self.rel_path).items()
            )
        ]

    def fn_cc(self, args):
        if len(args) == 0:
            self.rel_path = '/db'
        elif args[0] == '..':
            raise NotImplementedError
        else:
            possible = self._existing_collections_and_resources(resources=False)
            if args[0] in possible:
                self.rel_path += '/' + args[0]
            else:
                print('not a collection:', args[0])

        return None

    def fn_pcc(self, args):
        print(self.rel_path)

    def fn_find(self, args):
        '''Query lemmas or IDs.
        '''
        start_time = time.time()
        if len(args) < 2:
            print('use: find [lemma|link|id] term')
            return None

        query = None
        if args[0] == 'lemma':
            query = '''
                for $a in collection('dwdswb')//dwds:Artikel[dwds:Formangabe/dwds:Schreibung="%s"]
                    return fn:base-uri($a)
                '''
        elif args[0] == 'link':
            query = '''
                for $a in collection('dwdswb')//dwds:Verweis[dwds:Ziellemma="%s"]
                    return fn:base-uri($a)
                '''
        elif args[0] == 'id':
            query = '''
                for $a in collection('dwdswb')//dwds:Artikel/id("%s")
                    return fn:base-uri($a)
                '''

        if query is None:
            print('use: find [lemma|id] term')
            return None

        term = ' '.join(args[1:]).strip('"')
        count = 0
        for count, result in enumerate(self.db.xquery(query % term), 1):
            print(result.text)
        else:
            print(count, 'hits' if count != 1 else 'hit', end=' ')
            print('in %0.3f sec' % (time.time() - start_time))

    def fn_debug(self, args):
        '''Toggle debug messages.
        '''
        if len(args) == 0:
            print('on' if self.debug else 'off')
        elif args[0] in ('on', 'off'):
            self.debug = True if args[0] == 'on' else False
        else:
            print('use: debug [on|off]')

        logging.getLogger().setLevel(logging.DEBUG if self.debug else logging.WARNING)

    def all_collections(self, path='/db/dwdswb/data'):
        '''Recursively list all collections below path.
        '''

        colls = [
            path + '/' + coll.text
            for coll in self.db.xquery('xmldb:get-child-collections("%s")' % path)
            if coll.text != '.svn'
        ]

        extension = []
        deletion = []

        for index, coll in enumerate(colls):
            children = self.all_collections(path=coll)
            if children != []:
                extension.extend(children)
                deletion.insert(0, index)

        for d in deletion:
            del colls[d]

        colls.extend(extension)

        return colls

    def fn_headwords(self, command):

        query = u'''
        for $s in collection("%s")/*/dwds:Artikel/dwds:Formangabe/dwds:Schreibung
            let $a:=$s/ancestor::dwds:Artikel
            let $g:=$s/following-sibling::dwds:Grammatik
            %s
            return <lemma hidx="{$s/@hidx}"
                name="{$s/text()}"
                id="{$a/@xml:id}"
                type="{$a/@Typ}"
                status="{$a/@Status}"
                pos="{$g/dwds:Wortklasse//text()}"
                gen="{$g/dwds:Genus//text()}"
            />
        '''

        sfilter = ''
        if len(command) != 0:
            if command[0] == 'all':
                pass
            elif command[0] == 'red-f':
                sfilter = 'where $a/@Status="Red-f"'
            else:
                print('headwords [all|red-f]')
                return None
        else:
            print('headwords [all|red-f]')
            return None

        colls = self.all_collections()
        headwords = collections.defaultdict(set)

        with codecs.open('headwords-exist-current.txt', 'w', encoding='utf8') as outfile:
            for coll in colls:
                print(coll, end=' ', flush=True)
                for i in self.db.xquery(query % (coll, sfilter)):
                    i.set('name', ' '.join(i.get('name').split()))
                    i.set('pos', i.get('pos').strip())
                    i.set('gen', ' '.join(sorted(list(set(i.get('gen').split())))))
                    outfile.write('%(name)s\t%(hidx)s\t%(id)s\t%(type)s\t%(pos)s\t%(gen)s\t%(status)s\n' % i.attrib)
                    headwords[i.get('name')].add((i.get('id'), i.get('hidx')))
        print

        # sanity checks
        print(len(headwords.keys()))
        headword_duplicates = {k: v for k, v in headwords.items() if len(v) > 1}

        with codecs.open('headwords-exist-current.log', 'w', encoding='utf8') as outfile:

            counter = 0
            for headword, refs in headword_duplicates.items():
                # inconsistent homographs
                if not sorted(h for _, h in refs) == [str(i) for i in range(1, len(refs) + 1)]:
                    counter += 1
                    s_refs = sorted([ref for ref, _ in refs])
                    print('inconsistent homographs: "%s" %s' % (headword, s_refs))
                    outfile.write('inconsistent homographs: "%s" %s\n' % (headword, s_refs))
                    lemma_list = []
                    for ref in refs:
                        for l, v in headwords.items():
                            for i in v:
                                if i[0] == ref[0]:
                                    lemma_list.append(l)
                    if len(lemma_list) > 2:
                        counter += 1
                        print('skewed lemma count:', lemma_list)
                        outfile.write('skewed lemma count: %s\n' % str(lemma_list))
            print(counter, 'warning' if counter == 1 else 'warnings')

    def fn_check(self, args):
        '''Performs database internal consistency checks.

        Also attempts some automatic corrections where possible.
        '''

        _checks = ('rights', 'ids', 'validity', 'links', 'sanity')
        if len(args) == 0 or args[0] not in _checks:
            print('check', '|'.join(_checks))
            return

        start_time = time.time()
        print('this will take some time ...')

        colls = self.all_collections()
        print('found %i collections' % len(colls))

        if args[0] == 'sanity':
            print('checking markup sanity ...')

            for rule, query in self.SANITY_CHECKS:
                print('RULE: ' + rule + ':', end='')
                failed = False
                for a in self.db.xquery(query):
                    if not failed:
                        print
                    failed = True
                    print('\tFAILED:', a.text)
                if not failed:
                    print('PASSED')

        elif args[0] == 'rights':
            print('adjusting ownership and access rights ...')
            chown = 'for $a in collection("%s")//dwds:DWDS return sm:chown(fn:base-uri($a), "%s:%s")'
            chmod = 'for $a in collection("%s")//dwds:DWDS return sm:chmod(fn:base-uri($a), "rw-rw-r--")'
            for ac in self.ACL:
                print('%s -> %s:%s' % ac)
                for _ in self.db.xquery(chown % ac):
                    pass
                for _ in self.db.xquery(chmod % ac[0]):
                    pass

        elif args[0] == 'ids':
            # xml:id duplicates
            print('check for xml:id duplicates ...')
            query = 'for $a in collection("%s")//dwds:Artikel return <r fn="{fn:base-uri($a)}">{string($a/@xml:id)}</r>'
            ids = collections.defaultdict(list)
            for c in colls:
                for i in self.db.xquery(query % c):
                    ids[i.text].append(i.get('fn'))

            id_duplicates = {k: v for k, v in ids.items() if len(v) > 1}
            counter = 0
            for counter, offender in enumerate(id_duplicates.items(), 1):
                print('duplicate xml:id="%s" for %s' % offender)
            print(counter, 'duplicate%s' % ('s' if counter != 1 else ''))

        elif args[0] == 'links':
            print('checking link resolution ...')
            headwords = {}
            with codecs.open('headwords-exist-current.txt', encoding='utf-8') as hw:
                for line in hw:
                    parts = line.split('\t')
                    logging.info(parts)
                    headwords[(parts[0], (parts[1]) or None)] = True

            query = 'for $v in collection("%s")//dwds:Artikel[@Status="Red-f" or @Status="Red-2"]//dwds:Verweis[not(@class)]/dwds:Ziellemma return $v'
            for c in colls:
                for v in self.db.xquery(query % c):
                    text = ''.join(et.ETXPath('.//text()')(v))
                    if not (text, v.get('hidx')) in headwords:
                        print('No such lemma: %s%%%s (in %s)' % (text, v.get('hidx'), c))

            print('checking morphological analyses ...')
            query = 'for $v in collection("%s")//dwds:Artikel[@Status="Red-f" or @Status="Red-2"]/dwds:Verweise return <r fn="{fn:base-uri($v)}">{$v}</r>'
            for c in colls:
                for v in self.db.xquery(query % c):
                    # print v, et.tostring(v)
                    word_formation_type = [vv.get('Typ') for vv in v[0] if type(vv.tag) is str and vv.get('class') is None]
                    if word_formation_type not in self.VALID_WORD_FORMATION_TYPES:
                        print(word_formation_type, v.get('fn'))

        elif args[0] == 'validity':
            # RNC schema validity
            print('check for RNC schema validity ...')
            query = '''
                let $schema:=util:binary-doc("/db/dwdswb/validation/DWDSWB.rnc")
                for $a in collection("%s")//dwds:DWDS
                    let $uri:=fn:base-uri($a)
                    return <r fn="{$uri}">{validation:jing($uri, $schema)}</r>
            '''
            with codecs.open('validation-errors-current.txt', 'w', encoding='utf8') as outfile:
                for c in colls:
                    print(c)
                    for i in self.db.xquery(query % c):
                        if i.text != 'true':
                            print('Failed validation:', i.get('fn'))
                            outfile.write(i.get('fn') + '\n')

        print('took %i sec' % (time.time() - start_time))

    def fn_remove(self, names):
        for name in names:
            self.db.remove_resource_or_collection(name)

    def fn_lock(self, names):
        if len(names) < 1:
            print('usage: lock resource')
        else:
            self.db.lock(names[0])

    def fn_unlock(self, names):
        if len(names) < 1:
            print('usage: unlock resource')
        else:
            query = '''let $path := "%s"
            let $resource := "%s"
            return xmldb:clear-lock($path, $resource)'''
            locks = names[0].split('/')
            locks = ('/'.join(locks[:-1]), locks[-1])
            for xml in self.db.xquery(query % locks):
                print(xml.text)

    def fn_red2(self, _):

        query = u'for $a in collection("dwdswb")//dwds:Artikel[@Status="Red-1"] return fn:base-uri($a)'
        # resolver = LinkChecker(lemma_file=arguments.lemma_file)
        typographer = TypographyChecker()
        restructurer = StructureChecker()

        for metadata in self.db.xquery(query):
            name = metadata.text
            print(name, end=' ')
            try:
                token = self.db.lock(name)
                xml = self.db.get(name)
                data = xml.find(et.QName('http://www.dwds.de/ns/1.0', 'Artikel'))
                restructurer.reset()
                restructurer.remove_xml_space_preserve(data)
                restructurer.remove_stylesheet_pis(data)
                restructurer.reposition_pis(data)
                restructurer.remove_redaction_pis(data)
                restructurer.remove_unneeded_deletions(data)
                restructurer.expand_grammatical_atoms(data)
                restructurer.check_grammatical_info(data)
                # restructurer.hide_semantic_links(data)
                restructurer.insert_n_markers(data)

                typographer.reset()
                typographer.transliterate(data)
                typographer.strip_and_correct_whitespace(data)
                typographer.check_chars(data)
                typographer.check_final_punctuation(data)
                typographer.check_for_missing_whitespace(data)
                typographer.check_balanced_characters(data)

                if typographer.no_objections and restructurer.no_objections and ('DWDS' in data.get('Quelle') or 'ZDL' in data.get('Quelle')):
                    data.set('Status', 'Red-2')
                    # data.set('Zeitstempel',
                    #          datetime.datetime.now(pytz.utc).strftime('%Y-%m-%d'))
                    if data.get('Erstellungsdatum') is None:
                        # AG: Erstellungsdatum ist Datum der internen Redaktionsrunde,
                        # angenähert durch Übergang von Red-1 zu Red-2
                        data.set('Erstellungsdatum', data.get('Zeitstempel'))
                else:
                    data.set('Status', u'Red-2-zurückgewiesen')
                print(data.get('Status'))

                self.db.put(name, xml)
                self.db.unlock(name, token)
            except HTTPError as error:
                print(error)
                continue
            break

    _commands = {
        'cat': fn_cat,
        'cc': fn_cc,
        'cd': fn_cc,
        'check': fn_check,
        'debug': fn_debug,
        'exit': fn_exit,
        'find': fn_find,
        'get': fn_get,
        'headwords': fn_headwords,
        'help': fn_help,
        'lock': fn_lock,
        'ls': fn_ls,
        'pcc': fn_pcc,
        'put': fn_put,
        'quit': fn_exit,
        'red-2': fn_red2,
        'redaktion': fn_red2,
        'release': fn_unlock,
        'rm': fn_remove,
        'unlock': fn_unlock,
        'xquery': fn_xquery,
    }

    def __init__(self, api='http://localhost:8080/exist/rest', collection='/db', auth=()):
        if auth == ():
            username = input('username: ')
            password = getpass.getpass('password: ')
            auth = (username, password)

        self.db = ExistDB(api, auth)
        self.api = api
        self.rel_path = '/db'
        self.debug = False

        try:
            self.db.get(self.rel_path, timeout=2)
        except ReadTimeout:
            print('initial connect timeout')
        except (HTTPError, ConnectionError) as error:
            print(error)
            self.fn_exit(None)

    def parser_loop(self):

        # ATTENTION: exist-db will not respond within the first few seconds
        # after the first connection, don't know why :(

        while True:

            try:
                input_line = input('exist-shell: ')
            except EOFError:
                self.fn_exit([])

            tokens = input_line.split()
            if len(tokens) > 0:

                command = tokens[0]

                if command not in self._commands:
                    print('Unknown command:', command)
                else:
                    try:
                        self._commands[command](self, tokens[1:])
                    except NotImplementedError as error:
                        print('not yet implemented:', error)
                    except Exception as error:
                        if self.debug:
                            print(traceback.format_exc())
                        print(error)


if __name__ == '__main__':
    argument_parser = argparse.ArgumentParser(
        description='Python Exist Shell',
        add_help=False
    )
    argument_parser.add_argument(
        '--help',
        action='help',
        help='show this help message and exit'
    )
    argument_parser.add_argument(
        '-h', '--host', metavar='HOSTNAME',
        help='specify the database host',
        required=False,
        default='localhost'
    )
    argument_parser.add_argument(
        '-p', '--port', metavar='PORT_NUMBER',
        help='specify the database port number',
        required=False,
        default='8080',
    )
    arguments = argument_parser.parse_args()

    print('Connecting to http://%s:%s/exist' % (arguments.host, arguments.port))
    shell = ExistShell(api='http://%s:%s/exist' % (arguments.host, arguments.port))
    shell.parser_loop()
