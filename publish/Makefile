src_user = root
src_host = zwei.dwds.de

mysql = /usr/bin/mysql -sN
mysqldump = /usr/bin/mysqldump

all:
	@echo "Usage: make src_pass=... dst_pass=... (dwdswb|etymwb|wdg|dwb1)-update"

environment:
	/bin/mkdir -p backup/
	@if [ -z "${src_pass}" ] ; then echo '\nPlease set $${src_pass}!\n' ; /bin/false ; fi
	$(eval mysql += -h$(src_host) -u$(src_user) -p$(src_pass))

dwdswb-backup: environment
	$(mysqldump) -h$(src_host) -u$(src_user) -p$(src_pass) dwdswb > ./backup/backup-dwdswb-`date -Iseconds`.sql

etymwb-backup: environment
	$(mysqldump) -h$(src_host) -u$(src_user) -p$(src_pass) etymwb > ./backup/backup-etymwb-`date -Iseconds`.sql

dwb1-backup: environment
	$(mysqldump) -h$(src_host) -u$(src_user) -p$(src_pass) dwb1 > ./backup/backup-dwb1-`date -Iseconds`.sql

wdg-backup: environment
	$(mysqldump) -h$(src_host) -u$(src_user) -p$(src_pass) wdg > ./backup/backup-wdg-`date -Iseconds`.sql

openthesaurus-backup: environment
	$(mysqldump) -h$(src_host) -u$(src_user) -p$(src_pass) openthesaurus > ./backup/backup-openthesaurus-`date -Iseconds`.sql

dwdswb-update: dwdswb-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.lemma;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.relation;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.token;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS dwdswb.article;"
	$(mysql) -e "DROP TABLE IF EXISTS dwdswb.lemma;"
	$(mysql) -e "DROP TABLE IF EXISTS dwdswb.relation;"
	$(mysql) -e "DROP TABLE IF EXISTS dwdswb.token;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE dwdswb_beta.article TO dwdswb.article;"
	$(mysql) -e "RENAME TABLE dwdswb_beta.lemma TO dwdswb.lemma;"
	$(mysql) -e "RENAME TABLE dwdswb_beta.relation TO dwdswb.relation;"
	$(mysql) -e "RENAME TABLE dwdswb_beta.token TO dwdswb.token;"

etymwb-update: etymwb-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM etymwb_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM etymwb_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS etymwb.article;"
	$(mysql) -e "DROP TABLE IF EXISTS etymwb.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE etymwb_beta.article TO etymwb.article;"
	$(mysql) -e "RENAME TABLE etymwb_beta.lemma TO etymwb.lemma;"

wdg-update: wdg-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM wdg_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM wdg_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS wdg.article;"
	$(mysql) -e "DROP TABLE IF EXISTS wdg.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE wdg_beta.article TO wdg.article;"
	$(mysql) -e "RENAME TABLE wdg_beta.lemma TO wdg.lemma;"
