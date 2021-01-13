.PHONY: build
build: clean-cpcache
	@clojure -X:build

.PHONY: client
client: clean-cpcache
	@clojure -X:oxygen

.PHONY: solr
solr:
	@clojure -X:solr

.PHONY: clean-cpcache
clean-cpcache:
	@find .. -name .cpcache -exec rm -rf {} \; 2>/dev/null || true

