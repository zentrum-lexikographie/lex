SHELL := /bin/bash

version := $(shell cat VERSION)
oxygen_home := $(shell bin/find-oxygen.sh)

.PHONY: all deploy clean data-clean vm vm-destroy oxygen server client solr

all: server client

server:
	cd server && lein uberjar

client:
	cd client && lein uberjar && lein package

clean:
	cd client && lein clean
	cd server && lein clean

oxygen: client
	cd client && OXYGEN_HOME="$(oxygen_home)"\
		java -Dconf=oxygen-config.edn\
			-Dcom.oxygenxml.editor.plugins.dir=src/oxygen\
			-Dcom.oxygenxml.app.descriptor=ro.sync.exml.EditorFrameDescriptor\
			-cp "$(oxygen_home)/lib/oxygen.jar:$(oxygen_home)/lib/oxygen-basic-utilities.jar:$(oxygen_home)/classes:$(oxygen_home)"\
			ro.sync.exml.Oxygen\
			test-project.xpr

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

ansible/venv:
	cd ansible &&\
		virtualenv venv && source venv/bin/activate &&\
		pip install -r requirements.txt

vm: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		vagrant up

vm-destroy:
	cd ansible &&\
		vagrant destroy

solr:
	[ "$(shell docker ps -f name=zdl_lex_solr -q)" ] ||\
		docker run -t -d --name zdl_lex_solr\
			-p 8983:8983 -v ${CURDIR}/solr:/config\
			solr:7.7.1\
			solr-create -c articles -d /config

solr-destroy:
	[ "$(shell docker ps -f name=zdl_lex_solr -q)" ] &&\
		docker stop zdl_lex_solr
	docker rm zdl_lex_solr || true

deploy: ansible/venv server client
	cd ansible &&\
		source venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass
