src_user = ${USER}
src_host = zwei.dwds.de

mysql = /usr/bin/mysql -sN
mysqldump = /usr/bin/mysqldump

mkfile_path := $(abspath $(lastword $(MAKEFILE_LIST)))
bin_dir := $(dir $(mkfile_path))/bin

all:
	@echo "Usage:"
	@echo "make [-j] check"
	@echo "make src_pass=... dst_pass=... (dwdswb|etymwb|wdg|dwb1|dwb2|neologismen|varwb|wortgeschichten|wortgeschichten_preprint)-update"

environment:
	/bin/mkdir -p backup/
	@if [ -z "${src_pass}" ] ; then echo '\nPlease set $${src_pass}!\n' ; /bin/false ; fi
	$(eval mysql += -h$(src_host) -u$(src_user) -p"$(src_pass)")
	$(eval mysqldump += -h$(src_host) -u$(src_user) -p"$(src_pass)")

neologismen-backup: environment
	$(mysqldump) neologismen > ./backup/backup-neologismen-`date -Iseconds`.sql

wortgeschichten-backup: environment
	$(mysqldump) wortgeschichten > ./backup/backup-wortgeschichten-`date -Iseconds`.sql

wortgeschichten_preprint-backup: environment
	$(mysqldump) wortgeschichten_preprint > ./backup/backup-wortgeschichten_preprint-`date -Iseconds`.sql

dwdswb-backup: environment
	$(mysqldump) dwdswb > ./backup/backup-dwdswb-`date -Iseconds`.sql

etymwb-backup: environment
	$(mysqldump) etymwb > ./backup/backup-etymwb-`date -Iseconds`.sql

dwb1-backup: environment
	$(mysqldump) dwb1 > ./backup/backup-dwb1-`date -Iseconds`.sql

dwb2-backup: environment
	$(mysqldump) dwb2 > ./backup/backup-dwb2-`date -Iseconds`.sql

varwb-backup: environment
	$(mysqldump) varwb > ./backup/backup-varwb-`date -Iseconds`.sql

wdg-backup: environment
	$(mysqldump) wdg > ./backup/backup-wdg-`date -Iseconds`.sql

openthesaurus-backup: environment
	$(mysqldump) openthesaurus > ./backup/backup-openthesaurus-`date -Iseconds`.sql

openthesaurus-update: openthesaurus-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.category;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.category_link;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.language;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.link_type;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.synset;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.synset_link;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.tag;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.term;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.term_level;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.term_link;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.term_link_type;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.term_tag;"
	$(mysql) -e "SELECT COUNT(*) FROM openthesaurus_beta.word_mapping;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.category;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.category_link;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.language;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.link_type;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.synset;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.synset_link;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.tag;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.term;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.term_level;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.term_link;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.term_link_type;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.term_tag;"
	$(mysql) -e "DROP TABLE IF EXISTS openthesaurus.word_mapping;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE openthesaurus_beta.category TO openthesaurus.category;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.category_link TO openthesaurus.category_link;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.language TO openthesaurus.language;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.link_type TO openthesaurus.link_type;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.synset TO openthesaurus.synset;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.synset_link TO openthesaurus.synset_link;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.tag TO openthesaurus.tag;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.term TO openthesaurus.term;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.term_level TO openthesaurus.term_level;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.term_link TO openthesaurus.term_link;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.term_link_type TO openthesaurus.term_link_type;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.term_tag TO openthesaurus.term_tag;"
	$(mysql) -e "RENAME TABLE openthesaurus_beta.word_mapping TO openthesaurus.word_mapping;"

neologismen-update: neologismen-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM neologismen_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM neologismen_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS neologismen.article;"
	$(mysql) -e "DROP TABLE IF EXISTS neologismen.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE neologismen_beta.article TO neologismen.article;"
	$(mysql) -e "RENAME TABLE neologismen_beta.lemma TO neologismen.lemma;"

wortgeschichten-update: wortgeschichten-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM wortgeschichten_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM wortgeschichten_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS wortgeschichten.article;"
	$(mysql) -e "DROP TABLE IF EXISTS wortgeschichten.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE wortgeschichten_beta.article TO wortgeschichten.article;"
	$(mysql) -e "RENAME TABLE wortgeschichten_beta.lemma TO wortgeschichten.lemma;"

wortgeschichten_preprint-update: wortgeschichten_preprint-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM wortgeschichten_preprint_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM wortgeschichten_preprint_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS wortgeschichten_preprint.article;"
	$(mysql) -e "DROP TABLE IF EXISTS wortgeschichten_preprint.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE wortgeschichten_preprint_beta.article TO wortgeschichten_preprint.article;"
	$(mysql) -e "RENAME TABLE wortgeschichten_preprint_beta.lemma TO wortgeschichten_preprint.lemma;"

dwdswb-update: dwdswb-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.lemma;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.relation;"
	$(mysql) -e "SELECT COUNT(*) FROM dwdswb_beta.token;"
	# determine lemma difference
	$(mysql) -B -N -e "SELECT lemma, hidx FROM dwdswb.lemma ORDER BY lemma, BINARY(lemma), hidx;" dwdswb > backup/dwdswb.current
	$(mysql) -B -N -e "SELECT lemma, hidx FROM dwdswb_beta.lemma ORDER BY lemma, BINARY(lemma), hidx;" dwdswb_beta > backup/dwdswb.new
	diff --color=always backup/dwdswb.current backup/dwdswb.new | less -r
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

varwb-update: varwb-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM varwb_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM varwb_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS varwb.article;"
	$(mysql) -e "DROP TABLE IF EXISTS varwb.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE varwb_beta.article TO varwb.article;"
	$(mysql) -e "RENAME TABLE varwb_beta.lemma TO varwb.lemma;"

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

dwb1-update: dwb1-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM dwb1_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM dwb1_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS dwb1.article;"
	$(mysql) -e "DROP TABLE IF EXISTS dwb1.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE dwb1_beta.article TO dwb1.article;"
	$(mysql) -e "RENAME TABLE dwb1_beta.lemma TO dwb1.lemma;"

dwb2-update: dwb2-backup
	# check whether the source database exists
	$(mysql) -e "SELECT COUNT(*) FROM dwb2_beta.article;"
	$(mysql) -e "SELECT COUNT(*) FROM dwb2_beta.lemma;"
	# empty the target database
	$(mysql) -e "DROP TABLE IF EXISTS dwb2.article;"
	$(mysql) -e "DROP TABLE IF EXISTS dwb2.lemma;"
	# move beta database tables to the target database
	$(mysql) -e "RENAME TABLE dwb2_beta.article TO dwb2.article;"
	$(mysql) -e "RENAME TABLE dwb2_beta.lemma TO dwb2.lemma;"

check-audio:
	python3 $(bin_dir)/check-audio.py

check-bibl:
	python3 $(bin_dir)/check-bibl.py -f
	@echo "Bibliographic checks restricted to Artikel[@Status=\"Red-f\"]"

check-constraints:
	python3 $(bin_dir)/check-constraints.py

check-duplicates:
	python3 $(bin_dir)/check-duplicates.py

check-links:
	python3 $(bin_dir)/check-links.py

check-metadata:
	python3 $(bin_dir)/check-metadata.py

check-morphology:
	python3 $(bin_dir)/check-morphology.py

check-typography:
	python3 $(bin_dir)/check-typography.py -f
	@echo "Typographic checks restricted to Artikel[@Status=\"Red-f\"]"

check: check-typography check-bibl check-links check-audio check-constraints check-duplicates check-metadata check-morphology
	@echo "Note: checks can be run in parallel using \`make -j\`"
