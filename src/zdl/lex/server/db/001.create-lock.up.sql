BEGIN NOT ATOMIC

CREATE TABLE IF NOT EXISTS resource_lock (
  resource VARCHAR(255) NOT NULL,
  owner VARCHAR(64) NOT NULL,
  token VARCHAR(36) NOT NULL,
  expires INTEGER NOT NULL,
  PRIMARY KEY (resource, owner, token)
);

CREATE INDEX IF NOT EXISTS resource_lock_query
ON resource_lock (expires, resource, owner, token);

END;
