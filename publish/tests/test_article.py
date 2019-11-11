import zdl.article


def test_article_creation():
    assert zdl.article.tostring(zdl.article.create('ZDL', 'abc'))
