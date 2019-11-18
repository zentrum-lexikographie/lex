import collections
import datetime

from sqlalchemy import create_engine, MetaData

import zdl.article

_version = datetime.datetime.now().strftime('%Y-%m-%d')
_ddl_statements = [
    'DROP FUNCTION IF EXISTS dictionary_version',
    ('CREATE FUNCTION dictionary_version () RETURNS CHAR(%i) RETURN "%s"' %
     (len(_version), _version)),
    'DROP TABLE IF EXISTS article',
    'DROP TABLE IF EXISTS lemma',
    'DROP TABLE IF EXISTS token',
    'DROP TABLE IF EXISTS relation',
    '''CREATE TABLE IF NOT EXISTS article (
         id INT(11) NOT NULL,
         type CHAR(32) DEFAULT "Minimalartikel",
         status CHAR(32) DEFAULT "in_Bearbeitung",
         source CHAR(32) DEFAULT "DWDS",
         recommendation TINYINT(1) default 0,
         date DATE NOT NULL,
         xml MEDIUMTEXT COLLATE utf8_unicode_ci NOT NULL,
         PRIMARY KEY (id),
         KEY status (status)
       ) ENGINE=MyISAM DEFAULT CHARSET=utf8''',
    '''CREATE TABLE IF NOT EXISTS lemma (
         id INT(11) NOT NULL,
         lemma VARCHAR(50) COLLATE utf8_unicode_ci NOT NULL,
         hidx TINYINT(2) DEFAULT NULL,
         type VARCHAR(10),
         article_id INT(11) NOT NULL,
         PRIMARY KEY (id),
         KEY lemma (lemma),
         KEY article_id(article_id, lemma)
       ) ENGINE=MyISAM DEFAULT CHARSET=utf8''',
    '''CREATE TABLE IF NOT EXISTS token (
         token VARCHAR(50) COLLATE utf8_unicode_ci NOT NULL,
         lemma_id INT(11) NOT NULL,
         KEY token (token)
       ) ENGINE=MyISAM DEFAULT CHARSET=utf8''',
    '''CREATE TABLE IF NOT EXISTS relation (
         article1 INT(11) NOT NULL,
         article2 INT(11) NOT NULL,
         type VARCHAR(20) COLLATE utf8_unicode_ci NOT NULL,
         KEY article1 (article1, article2, type),
         KEY article2 (article2, article1, type)
       ) ENGINE=MyISAM DEFAULT CHARSET=utf8'''
]


def read_articles(articles):
    article_count = 0
    lemma_count = 0
    lemma_index = collections.defaultdict(list)
    relation_index = collections.defaultdict(list)

    for article_file in articles:
        for document, article in zdl.article.parse(article_file, strip=True):
            if not zdl.article.has_status('Red-f', article):
                continue
            zdl.article.prune(article)
            article_id = None
            for md in zdl.article.metadata(article):
                if article_id is None:
                    article_count += 1
                    article_id = article_count
                    xml = ''
                    if md['status'] in ('Red-f', None):
                        xml = zdl.article.tostring(article)
                    yield ('article', {
                        'id': article_id,
                        'type': md['type'],
                        'status': md['status'],
                        'source': md['source'],
                        'recommendation': md['recommended'],
                        'date': md['timestamp'] or '1000-01-01',
                        'xml': xml
                    })
                    relations = zdl.article.relations(article)
                    for rel_type, headword, hidx in relations:
                        headword = headword.replace('’', '\'')
                        hidx = int(hidx) if hidx else None
                        relation_index[
                            (article_id, rel_type)
                        ].append(
                            (headword, hidx)
                        )
                lemma = md['name'].replace('’', '\'')
                hidx = int(md['hidx']) if md['hidx'] else None

                headword_sig = (lemma, hidx)
                if headword_sig not in lemma_index:
                    lemma_index[headword_sig].append(article_id)
                    lemma_count += 1
                    lemma_id = lemma_count
                    yield ('lemma', {
                        'id': lemma_id,
                        'lemma': lemma,
                        'hidx': hidx,
                        'type': md['htype'],
                        'article_id': article_id
                    })
                    for token in lemma.split():
                        yield ('token', {
                            'token': token,
                            'lemma_id': lemma_id
                        })
    for (article_1, rel_type), targets in relation_index.items():
        for target in targets:
            article_2 = lemma_index[target]
            if article_2:
                yield ('relation', {
                    'article1': article_1,
                    'article2': article_2[0],
                    'type': rel_type
                })


def import_articles(records, db_url=None, echo=False):
    db_url = db_url or 'mysql+pymysql://dwdswb:dwdswb@localhost/dwdswb'
    db = create_engine(db_url, echo=echo)
    for stmt in _ddl_statements:
        db.execute(stmt)

    schema = MetaData()
    schema.reflect(bind=db)

    buckets = collections.defaultdict(list)
    bucket_sizes = {'article': 2000, 'lemma': 10000,
                    'relation': 50000, 'token': 50000}

    for (record_type, record) in records:
        bucket = buckets[record_type]
        bucket.append(record)
        if len(bucket) >= bucket_sizes[record_type]:
            db.execute(schema.tables[record_type].insert(), bucket)
            bucket.clear()

    for (record_type, bucket) in buckets.items():
        if len(bucket) > 0:
            db.execute(schema.tables[record_type].insert(), bucket)


def wb2db(articles):
    import_articles(read_articles(articles))
