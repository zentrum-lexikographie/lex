.PHONY: build
build:
	(cd build ; clojure -m zdl.lex.build)

.PHONY: release
release:
	(cd build ; clojure -m zdl.lex.build release)
