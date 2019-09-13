# encoding: utf8

import collections

class Article(object):
    '''A generic lexical entry.
    '''

    def __init__(self):
        self.type = None
        self.id = None
        self.lemmas = []
        self.senses = []
        self._raw_data = None


    def __str__(self):
        return '<ID=%s, type=%s, lemmas=%s>' % (self.id, self.type, self.lemmas)


class Lemma(object):
    '''A generic lemma.
    '''

    def __init__(self, type):
        self.orthographic_forms = []
        self.parts_of_speech = set([])
        self.genders = set([])
        self.grammar = collections.defaultdict(list)
        self.type = type


    def __str__(self):
        
        o = [ (x[0]+'%'+(x[1] or '')).rstrip('%')
                for x in self.orthographic_forms
        ]
        
        p = [ x or '' for x in self.parts_of_speech]
        
        return '<%s (%s)>' % ('|'.join(o), '|'.join(p))


class Sense(object):
    '''A generic sense.
    '''

    def __init__(self):
        pass
