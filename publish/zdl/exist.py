#! /usr/bin/env python2.7
# encoding: utf8

import logging, requests, urllib, re
import lxml.etree as et

class ExistDB(object):
    '''
    '''
    
    NS_MAP = {
            'exist': 'http://exist.sourceforge.net/NS/exist',
            'dwds': 'http://www.dwds.de/ns/1.0',
            'xml': 'http://www.w3.org/XML/1998/namespace',
    }
    LOCK_COMMAND = '<?xml version="1.0"?><lockinfo xmlns="DAV:"><lockscope><exclusive/></lockscope><locktype><write/></locktype><owner>exist.py client</owner></lockinfo>'
    XQuery_PREAMBLE = '''xquery version "3.0";
declare namespace dwds="http://www.dwds.de/ns/1.0";
'''
    WebDAV = '/webdav'
    REST = '/rest'

    xml_parser = et.XMLParser(encoding='utf8',
            remove_comments=True,
            remove_pis=False,
    ) # maybe other options
    stripping_xml_parser = et.XMLParser(
            encoding='utf8',
            remove_comments=True,
            remove_pis=True,
    )
    
    def __init__(self, url, auth=()):
        '''
        '''
        self.url = url.rstrip('/')
        self.session = requests.Session()
        self.session.auth = auth
        self.cookies = {}
        self.QN = {
                'exist:' + n: et.QName(self.NS_MAP['exist'], n)
                for n in ('result', 'collection', 'resource', 'query',
                    'text', 'properties', 'property', 'exception',
                    'session', 'start', 'count', 'hits', )
        }

    def __iter__(self, dirname=''):
        '''Lists all resource (file) names in the database recursivly.
        '''
        for i in self.get(dirname)[0]:
            if i.tag == self.QN['exist:resource']:
                yield dirname + '/' + i.get('name')
            elif i.tag == self.QN['exist:collection'] and i.get('name') != '.svn':
                for j in self.__iter__(dirname+'/'+i.get('name')):
                    yield j
    
    
    def ls(self, collection=''):
        '''
        '''
        root = self.get(collection)
        
        if root.tag == self.QN['exist:result']:
            return self.collection2dict(root)
        else:
            return None
        
    
    def collection2dict(self, root):
        '''
        '''

        entries = {}
        
        if root[0].tag == self.QN['exist:collection']:
            for element in root[0]:
                name = element.get('name')
                if name != '.svn':
                    entry = {
                        'ctime': element.get('created')[ : 19],
                        'mtime': element.get('last-modified'),
                        'owner': element.get('owner'),
                        'group': element.get('group'),
                        'perms': element.get('permissions'),
                    }
                    entry['mtime'] = entry['mtime'][ : 19] if entry['mtime'] is not None else '-'
                
                    if element.tag == self.QN['exist:resource']:
                        entry['type'] = 'resource'
                        entry['perms'] = '-' + entry['perms']
                    elif element.tag == self.QN['exist:collection']:
                        entry['type'] = 'collection'
                        entry['perms'] = 'c' + entry['perms']
                    
                    entries[name] = entry
        else:
            pass

        return entries
    

    def get(self, name, timeout=120, strip=False):
        '''Retrieves either a collection or a resource.

        This is a low level retriever that works regardless of the data
        (Exist data or stored resources).
        '''
        logging.debug(self.url+self.REST+urllib.quote(name))
        r = self.session.get(self.url+self.REST+urllib.quote(name),
                cookies=self.cookies,
                timeout=timeout)
        if not self.cookies:
            self.cookies = r.cookies
        if r.ok:
            try:
                tree = et.fromstring(r.content,
                        parser=self.stripping_xml_parser if strip else self.xml_parser)
                return tree
            except et.XMLSyntaxError, message:
                logging.warning('cannot parse %s: %s', name, message)
                return None
        else:
            logging.critical('Processing: %s', name)
            r.raise_for_status()
        #session.close()

    
    def put(self, name, data):
        '''Upload a resource.

        This is a low level uploader. It is indifferent to existing data. 
        '''
        logging.info('about to PUT %s', self.url+self.REST+urllib.quote(name))
        # don't urlencode names when putting!
        r = self.session.put(self.url+self.REST+name, et.tostring(data, encoding='utf8'))
        if not r.ok:
            r.raise_for_status()


    def xquery(self, expression, name='/db', query_batch_size=2000):
        '''Iterator that yields the results of an XQuery.
        '''

        hits = 0
        pos = 0
        count = 0
        session_ID = None
        
        query = et.Element(self.QN['exist:query'],
                nsmap=self.NS_MAP,
                max=str(query_batch_size),
                cache='yes',
        )
        text = et.SubElement(query, self.QN['exist:text'])
        text.text = self.XQuery_PREAMBLE + expression
        logging.info('About to send XQuery: %s', text.text)
        et.SubElement(query, self.QN['exist:properties'])
        
        while (pos - 1) < hits:

            query.set('start', str(pos or 1))
            if session_ID is not None:
                query.set('session', session_ID)
            data = et.tostring(query, pretty_print=True)
            logging.debug('about to POST: %s', data)

            request = self.session.post(self.url+self.REST+urllib.quote(name),
                    data=data,
                    cookies=self.cookies,
            )
            
            if request.ok:
                if not self.cookies:
                    self.cookies = request.cookies
                result = et.fromstring(request.content, self.xml_parser)
                if result.tag != self.QN['exist:result']:
                    raise SyntaxError, 'in XQuery: ' + request.content
                session_ID = session_ID or result.get(self.QN['exist:session'])
                hits = int(result.get(self.QN['exist:hits']))
                count = int(result.get(self.QN['exist:count']))
                pos = int(result.get(self.QN['exist:start'])) + count

                if count > 0:
                    for r in result:
                        yield r
                else:
                    raise StopIteration

            else:
                request.raise_for_status()


    def create_collection(self, name):
        pass

    def upload_resource(self, name, data):
        # TODO: make sure not to overwrite files unintentionally
        # there is no overwrite indication
        request = self.session.put(self.url+self.REST+urllib.quote(name),
                data=et.tostring(data, encoding='utf8'),
                cookies=self.cookies
        )
        if not self.cookies:
            self.cookies = request.cookies
        logging.debug(request)

    def remove_resource_or_collection(self, name):
        # TODO: sanity checks
        request = requests.delete(self.url+self.REST+urllib.quote(name),
                cookies=self.cookies,
                auth=self.session.auth)
        logging.info(request.content)
        if request.ok:
            return None
        else:
            request.raise_for_status()


    def lock(self, name):
        request = self.session.request('LOCK',
                self.url+self.WebDAV+urllib.quote(name),
                data=self.LOCK_COMMAND)
        if request.ok:
            logging.debug(request.content)
            match = re.search('opaquelocktoken:([a-z0-9\-]+)<', request.content)
            return match.group(1) # lock token
        else:
            request.raise_for_status()

    def unlock(self, name, token=None):
        request = self.session.request('UNLOCK',
                self.url+self.WebDAV+urllib.quote(name),
                headers= {} if token is None else { 'Lock-Token': token }
        )
        if request.ok:
            logging.debug(request)
        else:
            request.raise_for_status()


    def exit(self):
        self.session.close()


if __name__ == '__main__':

    db = ExistDB('http://spock.dwds.de:8080/exist',
            auth=('username', 'password')) # adapt as needed
    query = '''
    for $a in collection("dwdswb/data/DWDS")//dwds:Artikel[dwds:Formangabe/dwds:Schreibung="Schuhware"]
        return fn:base-uri($a)
'''

    # general workflow:
    for f in ( r.text for r in db.xquery(query) ):
        print f
        token = db.lock(f)
        resource = db.get(f)
        #print et.tostring(resource)
        db.put(f, resource)
        db.unlock(f, token)
        break
