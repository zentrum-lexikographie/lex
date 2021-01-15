.PHONY: build
build:
	@$(MAKE) -C build
	@$(MAKE) -C docker/solr
	@$(MAKE) -C docker/server

.PHONY: release
release: build
	@$(MAKE) -C build next-release
	@$(MAKE) build
	@$(MAKE) -C docker/solr push
	@$(MAKE) -C docker/server push

.PHONY: client
client:
	@$(MAKE) -C build client

.PHONY: server
server: build solr
	@$(MAKE) -C build server

.PHONY: solr
solr:
	@$(MAKE) -C docker/solr
	@$(MAKE) -C build solr


.PHONY: help
help:
	@echo 'Targets:'
	@echo ''
	@echo ' build    - Builds client, server and packages both in a Docker'
	@echo '            container'
	@echo ' release  - Runs a test build, creates a release tag for the current'
	@echo '            git revision and reruns the build, pushing resulting'
	@echo '            images in the end'
	@echo ' client   - Builds client and starts an OxygenXML Editor instance'
	@echo '            with the built development version of the client'
	@echo ' server   - Starts local ZDL-Lex server as a Docker container'
	@echo ' solr     - Starts local Apache Solr server as a Docker container'


