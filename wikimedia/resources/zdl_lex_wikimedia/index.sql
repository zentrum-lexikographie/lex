-- :name set-collation :!
set database collation GERMAN

-- :name create-excerpt-table :!
create table if not exists excerpt (
  id integer identity,
  collection varchar(32) not null,
  contents text not null,
  primary key (id)
)

-- :name create-excerpt-collection-index :!
create index if not exists excerpt_collection
on excerpt (collection)

-- :name create-lemma-table :!
create table if not exists lemma (
  surface_form varchar(255) not null,
  part_of_speech varchar(64) not null,
  excerpt integer not null,
  primary key (surface_form, part_of_speech, excerpt),
  foreign key (excerpt) references excerpt (id) on delete cascade
)

-- :name  delete-collection :!
delete from excerpt where collection = :collection

-- :name insert-excerpt :i!
insert into excerpt (collection, contents)
values (:collection, :contents)

-- :name merge-lemma :!
merge into lemma (surface_form, part_of_speech, excerpt)
values (:surface_form, :part_of_speech, :excerpt)

-- :name select-entries :*
select l.surface_form, l.part_of_speech, e.collection, (e.contents || '') as contents
from lemma l join excerpt e on l.excerpt = e.id
order by l.surface_form, l.part_of_speech
