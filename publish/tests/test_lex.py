import re

from pytest import fixture
from random import sample

from zdl.lex import Server, get_filename, generate_id


@fixture
def lex_server():
    return Server('http://localhost:3000', ('admin', 'admin'))


def test_get_filename():
    assert get_filename('Quätsch mit Söße') == 'Quatsch_mit_Sosse'
    assert get_filename('A und O') == 'A_und_O'


def test_generate_id(lex_server):
    assert re.match(r'E_\d+', generate_id(lex_server))


def test_article_roundtrip(lex_server):
    ids = [hit['id'] for hit in lex_server.index('id:*', limit=500).get('result')]
    id, = sample(ids, 1)
    document, article = next(lex_server.get_article(id))
    lex_server.post_article(id, document)
