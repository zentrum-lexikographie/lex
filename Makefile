SHELL := /bin/bash

CLOJURE_VERSION := 1.10.3.943
CLOJURE := clojure/bin/clojure

all: clojure
	@clojure -X:build 'zdl.lex.build/build!'
	@$(MAKE) -C docker/solr
	@$(MAKE) -C docker/server

release: all
	@clojure -X:build 'zdl.lex.build.release/next!'
	@$(MAKE) all
	@$(MAKE) -C docker/solr push
	@$(MAKE) -C docker/server push

client: clojure
	@clojure -X:build 'zdl.lex.build.oxygen/start!'

server: all solr clojure
	@clojure -X:build 'zdl.lex.build.docker/start-server!'

solr:  clojure
	@$(MAKE) -C docker/solr
	@clojure -X:build 'zdl.lex.build.docker/start-solr!'


.PHONY: help
help:
	@echo 'Targets:'
	@echo ''
	@echo ' all      - Builds client, server and packages both in a Docker'
	@echo '            container'
	@echo ' release  - Runs a test build, creates a release tag for the current'
	@echo '            git revision and reruns the build, pushing resulting'
	@echo '            images in the end'
	@echo ' client   - Builds client and starts an OxygenXML Editor instance'
	@echo '            with the built development version of the client'
	@echo ' server   - Starts local ZDL-Lex server as a Docker container'
	@echo ' solr     - Starts local Apache Solr server as a Docker container'


clojure: | clojure-install.sh
	mkdir -p $@
	./clojure-install.sh --prefix $(CURDIR)/$@

clojure-install.sh:
	curl -o $@ https://download.clojure.org/install/linux-install-$(CLOJURE_VERSION).sh
	chmod +x $@

.clj-kondo/.cache: | clojure
	clj-kondo\
		--lint "$$($(CLOJURE) -A:client:server:build:dev:test -Spath)"\
		--dependencies\
		--parallel\
		--copy-configs
