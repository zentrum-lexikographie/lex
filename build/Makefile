SHELL := /bin/bash

export PATH := $(CURDIR)/vendor/clojure/bin:$(PATH)

.PHONY: build
build: clean-cpcache vendor/clojure
	@clojure -X:build

.PHONY: client
client: clean-cpcache vendor/clojure
	@clojure -X:oxygen

.PHONY: solr
solr: vendor/clojure
	@clojure -X:solr

.PHONY: server
server: vendor/clojure
	@clojure -X:server

.PHONY: next-release
next-release: vendor/clojure
	@clojure -X:release

.PHONY: clean-cpcache
clean-cpcache:
	@find .. -name .cpcache -exec rm -rf {} \; 2>/dev/null || true

# ---------------------------------------- Clojure environment/setup

vendor/clojure: vendor/clojure-install.sh
	mkdir -p $@ || /bin/true
	vendor/clojure-install.sh --prefix $(CURDIR)/$@

vendor/clojure-install.sh:
	mkdir -p vendor || /bin/true
	curl -o $@ https://download.clojure.org/install/linux-install-1.10.2.796.sh
	chmod +x $@
