#! /usr/bin/env python3
# encoding: utf-8

import requests, time, logging, json, os, dotenv
import lxml.etree as et
from Wb import Wb

class GoodEx(object):

    API_URL = 'https://evidence.bbaw.de/gbe/examples/'
    DEFAULT_DELAY = 0.5
    MAX_CITATIONS = 5

    def __init__(self,
            auth=('', ''),
            loglevel=logging.WARNING):
        
        self.session = requests.Session()
        self.session.auth = auth
        self.session.headers.update({ 'Accept': 'application/json'} )

        self.logger = logging.getLogger(__name__)
        self.logger.setLevel(loglevel)
        self.logger.addHandler(logging.StreamHandler())
        self.logger.propagate = True


    def _get_examples(self, lemma, n, sleep=DEFAULT_DELAY):

        lemma = lemma.split('#')[0] # ignore homograph indexes
        
        parameters = {
                'q': lemma.strip(),
                'limit': n,
        }
        if len(parameters['q'].split()) != 1:
            logging.warning('Not a single word lemma: %s', repr(parameters['q']))
            return

        time.sleep(sleep)
    
        response = self.session.get(self.API_URL, params=parameters)
    
        if not parameters['q'] in json.loads(response.text)['result']:
            self.logger.warning(f'Lemma {parameters["q"]} cannot be processed by GoodEx')
        else:
            for example_data in json.loads(response.text)['result'][parameters['q']]['examples']:
                yield example_data

    
    def get_examples(self, lemma, n=MAX_CITATIONS):
        
        for snippet in self._get_examples(lemma, n):
            
            snippet['text'] = snippet['text'].replace('&', '&amp;')
            text = '<ex>'+snippet['text']+'</ex>'
            try:
                xml = et.fromstring(text)
            except et.XMLSyntaxError:
                self.logger.warning('Cannot parse: %s', repr(text))
                continue
                
            bibl = snippet['orig']
            src = 'dwds:' + snippet['corpus']
            
            yield xml, bibl, src

    def goodex2dwdswb(self, cit, bibl, bibl_spec):

        wb = Wb()
        
        b = et.Element(wb.TAGS['Beleg'])
        cit.tag = wb.TAGS['Belegtext']
        b.append(cit)

        for st in cit.findall('em'):
            st.tag = wb.TAGS['Stichwort']
        
        f = et.SubElement(b, wb.TAGS['Fundstelle'])
        f.text = bibl
        f.set('Fundort', bibl_spec)

        return b


if __name__ == '__main__':

    dotenv.load_dotenv()
    goodex = GoodEx(auth=(os.getenv('GOODEX_USERNAME'), os.getenv('GOODEX_PASSWORD')))
    wb = Wb()

    for n, (c, b, s) in enumerate(goodex.get_examples('Müller', n=5)):
        print(n, et.tostring(goodex.goodex2dwdswb(c, b, s)))
    for n, (c, b, s) in enumerate(goodex.get_examples('THS#2', n=5)):
        print(n, et.tostring(goodex.goodex2dwdswb(c, b, s)))
