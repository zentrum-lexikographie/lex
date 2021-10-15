create table if not exists lock (
  resource varchar(255) not null,
  owner varchar(64) not null,
  token varchar(36) not null,
  owner_ip varchar(64) not null,
  expires bigint not null,
  primary key (resource, owner, token)
);

create index if not exists lock_query_index
  on lock (expires, resource, owner, token);
