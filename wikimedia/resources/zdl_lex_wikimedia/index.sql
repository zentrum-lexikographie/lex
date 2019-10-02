-- :name create-excerpt-table :!
create table if not exists excerpt (
  id integer not null
  contents text not null,
  primary key (id)
)

-- :name create-lemma-table :!
create table if not exists lemma (
  surface_form varchar(255) not null,
  part_of_speech varchar(64) not null,
  collection varchar(32) not null,
  excerpt integer not null,
  primary key (surface_form, part_of_speech, collection),
  foreign key (excerpt) references excerpt (id)
)

-- :name insert-excerpt :i!
