import zdl.article


def test_article_creation():
    assert zdl.article.tostring(zdl.article.create(
        'Hotzenplotz', 'Substantiv', 'ZDL', 'middell', 'E_123456'
    ))
