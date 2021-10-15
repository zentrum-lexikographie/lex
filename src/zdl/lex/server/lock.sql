-- :name select-active-lock :? :1
-- :doc Retrieves another lock
select * from lock
where expires > :now
and resource = :resource
and (owner = :owner and token = :token)
order by resource, owner, token
