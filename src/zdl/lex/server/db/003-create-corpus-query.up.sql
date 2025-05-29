CREATE TABLE IF NOT EXISTS lexeme (
    form VARCHAR(128) PRIMARY KEY,
    ts TIMESTAMP NOT NULL,
    ppm REAL NOT NULL
);

CREATE INDEX IF NOT EXISTS lexeme_ppm ON lexeme (ppm, form);
CREATE INDEX IF NOT EXISTS lexeme_ts ON lexeme (ts, form);

CREATE TABLE IF NOT EXISTS query (
    id SERIAL PRIMARY KEY,
    ts TIMESTAMP NOT NULL,
    lexeme VARCHAR(128) NOT NULL REFERENCES lexeme (form) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS query_lexeme ON query (lexeme, ts);

CREATE TABLE IF NOT EXISTS hit (
    query INTEGER NOT NULL REFERENCES query (id) ON DELETE CASCADE,
    n INTEGER NOT NULL,
    text TEXT NOT NULL,
    text_year INTEGER,
    text_date DATE,
    country CHAR(2),
    collection VARCHAR(64),
    collection_file VARCHAR(128),
    bibl TEXT,
    PRIMARY KEY (query, n)
);

CREATE TABLE IF NOT EXISTS topic (
    query INTEGER NOT NULL,
    n INTEGER NOT NULL,
    k VARCHAR(64) NOT NULL,
    FOREIGN KEY (query, n) REFERENCES hit (query, n) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS topic_query ON topic (query, k);

CREATE TABLE IF NOT EXISTS text_class (
    query INTEGER NOT NULL,
    n INTEGER NOT NULL,
    k VARCHAR(64) NOT NULL,
    FOREIGN KEY (query, n) REFERENCES hit (query, n) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS text_class_query ON text_class (query, k);
