CREATE TABLE IF NOT EXISTS wikidata_lexeme (
  id VARCHAR(32) NOT NULL, 
  lemma VARCHAR(256) NOT NULL,
  pos VARCHAR(4) NOT NULL,
  last_modified  TIMESTAMP WITH TIME ZONE NOT NULL,
  entity TEXT NOT NULL,
  PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS wikidata_lemma_pos
ON wikidata_lexeme (lemma, pos);

CREATE TABLE IF NOT EXISTS dwdsmor_analysis (
  analysis VARCHAR(256) NOT NULL,
  pos VARCHAR(4) NOT NULL,
  spec VARCHAR(256) NOT NULL,
  inflected VARCHAR(256) NOT NULL,
  gender VARCHAR(7),
  casus VARCHAR(7),
  person VARCHAR(1),
  number VARCHAR(6),
  nonfinite VARCHAR(4),
  tense VARCHAR(4),
  degree VARCHAR(4),
  mood VARCHAR(4),
  funct VARCHAR(10),
  aux VARCHAR(5),
  category VARCHAR(7)
);

CREATE INDEX IF NOT EXISTS dwdsmor_lemma_pos
ON dwdsmor_analysis (analysis, pos, spec);
