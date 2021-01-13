.PHONY: build
build:
	@$(MAKE) -C build
	@$(MAKE) -C docker/solr
	@$(MAKE) -C docker/server

.PHONY: client
client:
	@$(MAKE) -C build client

.PHONY: solr
solr:
	@$(MAKE) -C docker/solr
	@$(MAKE) -C build solr

.PHONY: help
help:
	@echo 'Targets:'
	@echo ''
	@echo ' build  - Builds client, server and packages both in a Docker'
	@echo '          container'
	@echo ' client - Builds client and starts an OxygenXML Editor instance'
	@echo '          with the built development version of the client'
	@echo ' solr   - Starts local Apache Solr server as a Docker container'


