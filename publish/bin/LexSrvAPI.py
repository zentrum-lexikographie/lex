#!/usr/bin/env python3
# encoding: utf-8

import argparse, logging, os, dotenv
import requests, urllib.parse
import random, collections
import lxml.etree as et
from Wb import Wb

class LexSrvException(Exception):
    pass

class LexSrv(object):

    DEFAULT_TTL = 10
    QUERY_LIMIT = 100 # server side default (or maximum?): 1000
    
    def __init__(self,
            baseurl='https://localhost/',
            auth=('', ''),
            loglevel=logging.WARNING):
        
        self.BASE_URL = baseurl
        self.AUTH = auth

        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(loglevel)
        self.logger.addHandler(logging.StreamHandler())
        self.logger.propagate = False

        # PIs are used for comments and highlighting in oxygen, don't discard!
        self.xml_parser = et.XMLParser(
                remove_comments=True,
                remove_pis=False,
        )

    def _acquire_lock(self, resource, ttl=DEFAULT_TTL):
        resource = urllib.parse.quote(resource, safe='')
        token = ''.join( [ str(random.randint(0, 9)) for _ in range(20) ] )
        r = requests.post(self.BASE_URL+'/lock/'+resource,
                params={'ttl': ttl, 'token': token},
                auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            return r.json()['token']
        else:
            self.logger.warning('Cannot acquire lock: %s', r.text)
            r.raise_for_status()


    def _release_lock(self, resource, token):
        resource = urllib.parse.quote(resource, safe='')
        r = requests.delete(self.BASE_URL+'/lock/'+resource,
                params={'token': token},
                auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            return True
        elif r.status_code == requests.codes.not_found:
            self.logger.warning('Resource not found upon release (strange bug)')
            return True
        else:
            self.logger.warning('Cannot release lock: %s', r.text)
            r.raise_for_status()
        

    def get_locks(self):
        r = requests.get(self.BASE_URL+'/lock', auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            return r.json()
        else:
            r.raise_for_status()


    def fetch_entry(self, resource):
        
        try:
            token = self._acquire_lock(resource)
        except Exception as error:
            logging.error(error)
            # better do nothing
            return None, None
        
        r = requests.get(
                self.BASE_URL+'/article/'+urllib.parse.quote(resource, safe=''),
                params={'token': token},
                auth=self.AUTH
        )
        if r.status_code == requests.codes.ok:
            # use r.content (raw bytes) to allow lxml to do the decoding
            root = et.fromstring(r.content, parser=self.xml_parser)
            return root, token
        else:
            r.raise_for_status()

    def local_path_to_resource_location(self, path):
        print(path)


    def store_entry(self, resource, entry, token):

        payload = et.tostring(entry.getroottree(), encoding='utf-8')

        r = requests.post(
                self.BASE_URL+'/article/'+urllib.parse.quote(resource, safe=''),
                data=payload,
                params={'token': token},
                auth=self.AUTH
        )
        if r.status_code == requests.codes.ok:
            pass
        else:
            r.raise_for_status()
        
        self._release_lock(resource, token)


    def create_new_entry(self, headword, pos, author='DWDS'):
        
        wb = Wb(strip=False)
        
        #ascii_headword = wb.asciify(headword)
        r = requests.put(self.BASE_URL+'/article/',
                params={'form': headword, 'pos': pos},
                auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            # resource = r.json()['id']
            resource = r.headers['x-lex-id']
            return resource

        else:
            r.raise_for_status()

    def get_tickets(self, query):
        r = requests.get(self.BASE_URL+'/mantis/issues',
                params={'q': query},
                auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            return r.json()
        else:
            r.raise_for_status()

    def commit_changes(self):
        r = requests.patch(self.BASE_URL+'/git', auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            if r.text == '':
                return False
            else:
                # return r.json()
                return True
        else:
            r.raise_for_status()

    def get_facets(self):
        r = requests.get(self.BASE_URL+'/index',
                params={'limit': 0},
                auth=self.AUTH,)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            if r.text == '':
                return False
            else:
                return r.json()['facets']
        else:
            r.raise_for_status()

    def query(self, query):
        r = requests.get(self.BASE_URL+'/index',
                params={'q': query},
                auth=self.AUTH)
        if r.status_code == requests.codes.ok:
            r.encoding = 'utf-8'
            return r.json()
        else:
            r.raise_for_status()


    def resource_iterator(self, query='*', offset=0):
        
        limit = self.QUERY_LIMIT
        total = None

        while total is None or total > offset:
            
            r = requests.get(self.BASE_URL+'/index',
                    params={'q': query, 'offset': offset, 'limit': limit},
                    auth=self.AUTH)
            
            if r.status_code == requests.codes.ok:
                r.encoding = 'utf-8'
                parsed = r.json()
                total = parsed['total']
                self.logger.debug('Total results: %s (offset=%s)',
                        total,
                        offset
                )
                
                for result in parsed['result']:
                    yield result['id']

                offset += limit

            else:
                r.raise_for_status()
        
if __name__ == '__main__':

    argument_parser = argparse.ArgumentParser(description='Lex API.')
    argument_parser.add_argument('-v', '--verbose', action='store_true',
            default=False,
            help='verbose output')
    arguments = argument_parser.parse_args()

    dotenv.load_dotenv()
    lex = LexSrv('https://lex.dwds.de',
            (os.getenv('LEX_USERNAME'), os.getenv('LEX_PASSWORD')),
            loglevel=logging.DEBUG
    )
    
    locks = lex.get_locks()
    facets = lex.get_facets()
    
    print('Red-f entries:', facets['status']['Red-f'])
    print('Current locks:',
            len(locks),
            '('+', '.join(sorted(set([l['owner'] for l in locks])))+')' if locks else ''
    )
    
    if arguments.verbose:
        for record in sorted(locks, key=lambda x: x['owner']):
            print(f'\t{record["owner"]}: {record["resource"]}')

        print('Current errors:')
        all_entries = sum( int(_n) for _n in facets['status'].values() )
        for _e, _n in facets['errors'].items():
            print('\t%11s: %8s (%00.2f%%)' % (_e, _n, _n/all_entries*100, ), sep='')
    
    print('Commit pending changes:', lex.commit_changes())
    
    #print('Tickets for "Schwein":', len(lex.get_tickets('Schwein')))
    #print('New entry:', lex.create_new_entry('elastisch', 'Adjektiv'))
    #r = lex.query('datum:[2019-01-01 TO 2019-12-31] AND status:Red-f AND quelle:(DWDS OR Wahrig/DWDS) AND typ:(Vollartikel OR Verweisartikel) AND NOT(klasse:Mehrwortausdruck) AND NOT(ersterfassung:(WDG OR Duden_1999))')
    
    #print('Query results:', r['total'], len(r['result']))

    #for r in lex.resource_iterator():
    #    print(r)

    #r, t = lex.fetch_entry('Neuartikel-002/Testartikel-E_2694405.xml')
    #print(r)
    #r[0].set('Zeitstempel', '2021-09-23')
    #lex.store_entry('Neuartikel-002/Testartikel-E_2694405.xml', r, t)
