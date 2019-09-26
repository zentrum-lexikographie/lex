_article_file_filter = set(["indexedvalues.xml", "__contents__.xml"])


def article_files(articles_dir):
    for f in articles_dir.glob('**/*.xml'):
        if f.name not in _article_file_filter:
            yield f
