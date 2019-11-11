from pytest import fixture
from pathlib import Path

from git import Repo


@fixture
def git_dir():
    p = (Path(__file__).parent) / '..' / '..' / 'data' / 'git'
    return p.resolve()


def test_git_dir(git_dir):
    assert 'master' in Repo(git_dir.as_posix()).heads
