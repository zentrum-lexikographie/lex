CREATE TABLE IF NOT EXISTS lock (
  resource VARCHAR(255) NOT NULL,
  owner VARCHAR(64) NOT NULL,
  token VARCHAR(36) NOT NULL,
  expires BIGINT NOT NULL,
  PRIMARY KEY (resource, owner, token)
);

CREATE INDEX IF NOT EXISTS lock_query_index
ON lock (expires, resource, owner, token);
