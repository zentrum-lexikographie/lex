create table if not exists zdl_lex_article (
  id varchar(255) not null,
  last_modified timestamp not null,
  status varchar(32),
  type varchar(32),
  pos varchar(32),
  provenance varchar(32),
  source varchar(32),
  primary key (id)
);

create index if not exists zdl_lex_article_last_modified_index
  on zdl_lex_article (last_modified);

create table if not exists zdl_lex_article_link (
  id varchar(255) not null,
  anchor varchar(255) not null,
  incoming boolean not null,
  primary key (anchor, incoming, id),
  foreign key (id) references zdl_lex_article (id) on delete cascade
);

create table if not exists lex_info_form (
  form varchar(255) not null,
  article_id varchar(255) not null,
  article_type varchar(32) not null,
  primary key (article_id, article_type, form),
);

create index if not exists lex_info_form_query_index
  on lex_info_form (form, article_type);

create table if not exists mantis_issue (
  id integer not null,
  form varchar(255) not null,
  attachments integer not null,
  last_updated varchar(64) not null,
  notes integer not null,
  summary varchar(255) not null,
  category varchar(64) not null,
  status varchar(64),
  severity varchar(64),
  reporter varchar(64),
  handler varchar(64),
  resolution varchar(64)
);

create index if not exists mantis_issue_form_index
  on mantis_issue (form);
