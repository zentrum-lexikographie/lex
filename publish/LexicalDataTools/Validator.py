# encoding: utf-8

import logging
import lxml.etree as etree

class SemanticValidator(object):
    '''Performs sematic checks on dictionaries.
    '''

    def __init__(self, dictionary):
        '''
        '''
        self.dictionary = dictionary
        self.logger = logging.getLogger(SemanticValidator.__name__)


    def report(self):
        '''
        '''
        
        logging.debug('Begin report.')
        
        for article in self.dictionary:
            print article
        
        logging.debug('End report.')
