-- :name insert-zdl-lex-article :!
insert into zdl_lex_article
            (id, last_modified, status, type, pos, provenance, source)
values (:id, :last-modified, :status, :type, :pos, :provenance, :source);

-- :name delete-zdl-lex-article :!
delete from zdl_lex_article where id = :id;

-- :name select-outdated-zdl-lex-articles :*
select id from zdl_lex_article where last_modified < :threshold;

-- :name select-zdl-lex-article-by-id :? :1
select * from zdl_lex_article where id = :id;

-- :name insert-zdl-lex-article-link :!
insert into zdl_lex_article_link (id, anchor, incoming)
values (:id, :anchor, :incoming);

-- :name delete-zdl-lex-article-links :!
delete from zdl_lex_article_link where id = :id;

-- :name select-zdl-lex-article-links-by-id :? :*
select * from zdl_lex_article_link where id = :id;

-- :name select-zdl-lex-article-links :*
select this.anchor as this_anchor,
       this.incoming as this_incoming,
       that_article.*
  from zdl_lex_article_link this
       join zdl_lex_article_link that
           on this.anchor = that.anchor and this.incoming <> that.incoming
       join zdl_lex_article that_article
           on that.id = that_article.id
 where this.id = :id and that.id <> :id;

-- :name insert-lex-info-form :!
insert into lex_info_form (form, article_id, article_type)
values (:form, :id, :type);

-- :name delete-lex-info-forms :!
delete from lex_info_form where article_id = :id and article_type = :type;

