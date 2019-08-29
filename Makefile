SHELL := /bin/bash

version := $(shell cat VERSION)
oxygen_home := $(shell bin/find-oxygen.sh)

.PHONY: all
all: server client

bin/lein:
	curl -s -o $@\
		https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein
	chmod +x $@

.PHONY: common
common: bin/lein
	cd common && ../bin/lein install

.PHONY: server
server: common schema bin/lein
	cd server && ../bin/lein uberjar

.PHONY: client
client: common schema bin/lein
	cd client && ../bin/lein uberjar && ../bin/lein package

.PHONY: schema
schema:
	git submodule update --init --recursive $@
	make -C $@ install

.PHONY: clean
clean: bin/lein
	cd schema && make clean
	cd common && ../bin/lein clean
	cd client && ../bin/lein clean
	cd server && ../bin/lein clean

.PHONY: oxygen
oxygen: client
	$(RM) client/.lein-env
	cd client && OXYGEN_HOME="$(oxygen_home)"\
		"$(oxygen_home)/jre/bin/java"\
			-Dzdl.lex.repl.port=3001\
			-Dcom.oxygenxml.editor.plugins.dir=src/oxygen\
			-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor\
			-cp "$(oxygen_home)/lib/oxygen.jar:$(oxygen_home)/lib/oxygen-basic-utilities.jar:$(oxygen_home)/classes:$(oxygen_home)"\
			ro.sync.exml.Oxygen\
			test-project.xpr

ansible/venv:
	cd ansible &&\
		virtualenv venv && source venv/bin/activate &&\
		pip install -r requirements.txt

.PHONY: deploy
deploy: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass

.PHONY: data-clean
data-clean:
	rm -rf data/exist-db data/git data/repo.git || true

data/exist-db.zip:
	mkdir -p data
	bin/download-exist-db-dump.sh $@

data/exist-db/db: | data/exist-db.zip
	mkdir -p data/exist-db
	cd data/exist-db &&\
		unzip ../exist-db.zip -x\
			'*indexedvalues.xml'\
			'*__contents__.xml'\
			'db/apps*'\
			'db/system/versions*'\
			'db/__lost_and_found__*'

data/repo.git:
	mkdir -p $@
	cd $@ && git init --bare --shared

data/git: | data/exist-db/db data/repo.git
	git clone file://${CURDIR}/data/repo.git data/git
	rsync -av data/exist-db/db/dwdswb/data/ data/git/articles
	cd data/git &&\
		git add articles &&\
		git commit -m 'Initial checkin' &&\
		git push -u origin &&\
		git gc

.PHONY: vm
vm: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		vagrant up

.PHONY: vm-provision
vm-provision: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		vagrant provision

.PHONY: vm-destroy
vm-destroy:
	cd ansible &&\
		vagrant destroy

.PHONY: solr
solr:
	[ "$(shell docker ps -f name=zdl_lex_solr -q)" ] ||\
		docker run -t -d --name zdl_lex_solr\
			-p 8983:8983 -v ${CURDIR}/solr:/config\
			solr:7.7.1\
			solr-create -c articles -d /config

.PHONY: solr-destroy
solr-destroy:
	[ "$(shell docker ps -f name=zdl_lex_solr -q)" ] &&\
		docker stop zdl_lex_solr
	docker rm zdl_lex_solr || true

.PHONY: existdb
existdb:
	[ "$(shell docker ps -f name=zdl_lex_exist -q)" ] ||\
		docker run -t -d -i --name zdl_lex_exist\
			-p 8080:8080\
			existdb/existdb:release

.PHONY: existdb-destroy
existdb-destroy:
	[ "$(shell docker ps -f name=zdl_lex_exist -q)" ] &&\
		docker stop zdl_lex_exist
	docker rm zdl_lex_exist || true

.PHONY: spock-tunnel
spock-tunnel:
	ssh -N -L 8080:localhost:8080 -o "ServerAliveInterval 60" -v spock.dwds.de

.PHONY: new-version
new-version:
	echo -n `date +%Y%m.%d.%H` >VERSION
