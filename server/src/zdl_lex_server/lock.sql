-- :name create-lock-table :!
-- :doc Creates the table for lock records
create table if not exists lock (
  resource varchar(255) not null,
  owner varchar(64) not null,
  token varchar(36) not null,
  owner_ip varchar(64) not null,
  expires bigint not null,
  primary key (resource, owner, token)
)

-- :name select-locks :? :*
-- :doc Lists all active/non-expired locks
select * from lock
where expires > :now
order by resource, owner, token

-- :name select-active-locks :? :*
-- :doc Retrieves active locks applying to a given resource
select * from lock
where expires > :now
and resource in (:v*:paths)
order by resource, owner, token

-- :name select-other-locks :? :*
-- :doc Retrieves active locks applying to a given resource
select * from lock
where expires > :now
and resource in (:v*:paths)
and (owner <> :owner or token <> :token)
order by resource, owner, token

-- :name select-active-lock :? :1
-- :doc Retrieves another lock
select * from lock
where expires > :now
and resource = :resource
and (owner = :owner or token = :token)
order by resource, owner, token

-- :name select-other-locks :? :*
-- :doc Retrieves another lock
select * from lock
where expires > :now
and resource in (:v*:paths)
and (owner <> :owner or token <> :token)
order by resource, owner, token

-- :name merge-lock :!
-- :doc Upserts a (new) lock
merge into lock
(resource, owner, token, owner_ip, expires)
values
(:resource, :owner, :token, :owner_ip, :expires)


-- :name delete-lock :! :n
-- :doc Removes a lock
delete from lock
where expires > :now
and resource = :resource
and owner = :owner
and token = :token

-- :name delete-expired-locks :! :n
-- :doc Purges expired lock records
delete from lock
where expires <= :now

-- :name create-lock-query-index :!
-- :doc Create index for speeding up lock lookup
create index if not exists lock_query_index
on lock (expires, resource, owner, token)

