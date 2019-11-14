import datetime

from sqlalchemy import create_engine, MetaData

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

_engine = create_engine(
    'mysql+pymysql://dwdswb:dwdswb@localhost/dwdswb',
    echo=False
)

for stmt in _ddl_statements:
    _engine.execute(stmt)

metadata = MetaData()
metadata.reflect(bind=_engine)
print(metadata.tables.keys())
