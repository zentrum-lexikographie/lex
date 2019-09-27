from pytest import fixture
from pathlib import Path

import lxml.etree as et

import zdl.article
import zdl.git
import zdl.structure
import zdl.typography


@fixture
def articles_dir():
    p = (Path(__file__).parent) / '..' / '..' / 'data' / 'git' / 'articles'
    return p.resolve()


def test_red_2(articles_dir):
    '''
    This script does the automatic steps for the redaction process step 2
    of the DWDS newly edited entries. This comprises all formal checks and
    verifications of the data before the internal preview publication.

    See http://odo.dwds.de/twiki/bin/view/Lexikographen/RedaktionsProzess
    for detailed information on the complete redaction process.
    '''
    for p in zdl.git.article_files(articles_dir):
        for (document, article) in zdl.article.parse(p):
            modified = False
            if zdl.article.has_status('Red-1', article):
                comments = []
                comments.extend(zdl.structure.check(article))
                comments.extend(zdl.typography.check(article))
                for target, comment in comments:
                    zdl.article.add_comment(target, comment, 'Redaktion2')
                    modified = True
            if modified:
                zdl.article.save(document, p)
