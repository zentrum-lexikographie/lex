from pytest import fixture
from pathlib import Path


@fixture
def articles_dir():
    p = (Path(__file__).parent) / '..' / '..' / 'data' / 'git' / 'articles'
    return p.resolve()


def test_red_2(articles_dir):
    assert articles_dir.is_dir()
