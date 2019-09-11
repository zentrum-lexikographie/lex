SHELL := /bin/bash

../venv:
	make -C .. venv

.PHONY: deploy
deploy: ../venv build
	source ../venv/bin/activate &&\
		ansible-playbook main.yml -b -K --ask-vault-pass

.PHONY: build
build:
	[ -n $$NO_BUILD ] || make -C .. client server

.PHONY: vm
vm: ../venv build
	source ../venv/bin/activate &&\
		vagrant up

.PHONY: vm-provision
vm-provision: ../venv build
	source ../venv/bin/activate &&\
		vagrant provision

.PHONY: vm-destroy
vm-destroy:
	vagrant destroy

.PHONY: solr
solr:
	[ "$(shell docker ps -f name=zdl_lex_solr -q)" ] ||\
		docker run -t -d --name zdl_lex_solr\
			-p 8983:8983 -v ${CURDIR}/../solr:/config\
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
