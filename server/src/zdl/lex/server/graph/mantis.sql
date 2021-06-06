-- :name delete-mantis-issues :!
delete from mantis_issue;

-- :name insert-mantis-issues :!
insert into mantis_issue (id, form, attachments, last_updated, notes,
                          summary, category, status, severity, reporter,
                          handler, resolution)
values :tuple*:issues;

-- :name select-mantis-issues-by-form :*
select * from mantis_issue where form in (:v*:forms);
