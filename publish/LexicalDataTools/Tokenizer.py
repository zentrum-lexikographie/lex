# encoding: utf-8

import logging

class Tokenizer(object):
    '''Tokenizer for dictionaries.
    '''

    def __init__(self, dictionary, pedantic=False):
        '''
        '''
        self.dictionary = dictionary
        self.pedantic = pedantic
        self.logger = logging.getLogger(Tokenizer.__name__)


    def _tokenize(self, element):
        '''
        '''

        tokens = []

        text = (element.text or '').strip()
        tail = (element.tail or '').strip()

        tokens.append(text)

        for child in element:
            self.logger.debug(child)
            tokens.extend(self._tokenize(child))

        tokens.append(tail)

        return tokens


    def tokenize(self):
        '''
        '''

        tokens = []

        for article in self.dictionary:
            self.logger.debug(article)
            tokens.extend(self._tokenize(article._raw_xml))

        return filter(lambda x: x, tokens)
