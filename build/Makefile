SHELL := /bin/bash
CLOJURE := vendor/clojure/bin/clojure

.PHONY: build
build: clean-cpcache vendor/clojure
	@$(CLOJURE) -X:build

.PHONY: client
client: clean-cpcache vendor/clojure
	@$(CLOJURE) -X:oxygen

.PHONY: solr
solr: vendor/clojure
	@$(CLOJURE) -X:solr

.PHONY: server
server: vendor/clojure
	@$(CLOJURE) -X:server

.PHONY: next-release
next-release: vendor/clojure
	@$(CLOJURE) -X:release

.PHONY: clean-cpcache
clean-cpcache:
	@find .. -name .cpcache -exec rm -rf {} \; 2>/dev/null || true

# ---------------------------------------- Clojure environment/setup

vendor/clojure: vendor/clojure-install.sh
	mkdir -p $@ || /bin/true
	vendor/clojure-install.sh --prefix $@

vendor/clojure-install.sh:
	mkdir -p vendor || /bin/true
	curl -o $@ https://download.clojure.org/install/linux-install-1.10.2.796.sh
	chmod +x $@
