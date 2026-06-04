CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS example_request (
    id SERIAL PRIMARY KEY,
    lexemes TEXT[] NOT NULL,
    requested TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    retrieved TIMESTAMP WITH TIME ZONE
);

CREATE TABLE IF NOT EXISTS example (
    req_id INTEGER NOT NULL REFERENCES example_request (id) ON DELETE CASCADE,
    n INTEGER NOT NULL,
    txt TEXT NOT NULL,
    gdex FLOAT NOT NULL,
    doc VARCHAR(128) NOT NULL,
    bibl TEXT NOT NULL,
    conll TEXT NOT NULL,
    ex_year SMALLINT,
    ex_date DATE,    
    embedding VECTOR(1024),
    PRIMARY KEY (req_id, n)
);

CREATE TABLE IF NOT EXISTS example_collocs (
    req_id INTEGER NOT NULL,
    n INTEGER NOT NULL,
    collocation VARCHAR(128) NOT NULL,
    PRIMARY KEY (req_id, n, collocation),
    FOREIGN KEY (req_id, n) REFERENCES example (req_id, n) ON DELETE CASCADE
);

-- CREATE TABLE IF NOT EXISTS query_lexeme (
--     query INTEGER NOT NULL REFERENCES query (id) ON DELETE CASCADE,
--     lexeme VARCHAR(128) NOT NULL
-- )

-- CREATE INDEX IF NOT EXISTS query_lexeme ON query (lexeme, ts);

-- CREATE TABLE IF NOT EXISTS hit (
--     query INTEGER NOT NULL REFERENCES query (id) ON DELETE CASCADE,
--     n INTEGER NOT NULL,
--     text TEXT NOT NULL,
--     text_year INTEGER,
--     text_date DATE,
--     country CHAR(2),
--     collection VARCHAR(64),
--     collection_file VARCHAR(128),
--     bibl TEXT,
--     PRIMARY KEY (query, n)
-- );

-- CREATE TABLE IF NOT EXISTS topic (
--     query INTEGER NOT NULL,
--     n INTEGER NOT NULL,
--     k VARCHAR(64) NOT NULL,
--     FOREIGN KEY (query, n) REFERENCES hit (query, n) ON DELETE CASCADE
-- );

-- CREATE INDEX IF NOT EXISTS topic_query ON topic (query, k);

-- CREATE TABLE IF NOT EXISTS text_class (
--     query INTEGER NOT NULL,
--     n INTEGER NOT NULL,
--     k VARCHAR(64) NOT NULL,
--     FOREIGN KEY (query, n) REFERENCES hit (query, n) ON DELETE CASCADE
-- );

-- CREATE INDEX IF NOT EXISTS text_class_query ON text_class (query, k);
