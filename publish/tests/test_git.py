from pytest import fixture
from pathlib import Path

from git import Repo


@fixture
def git_dir():
    p = (Path(__file__).parent) / '..' / '..' / 'data' / 'qa'
    return p.resolve()


def test_red_2(git_dir):
    assert 'zdl-lex-server' in Repo(git_dir.as_posix()).heads
