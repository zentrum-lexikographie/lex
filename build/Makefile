define clear-classpath-cache =
find .. -name .cpcache -exec rm -rf {} \; || true
endef

.PHONY: build
build:
	$(clear-classpath-cache)
	clojure -m zdl.lex.build
	$(clear-classpath-cache)

.PHONY: build_env
build_env:
	docker build -t lex.dwds.de/zdl-lex/build:1.0 .
#	docker push lex.dwds.de/zdl-lex/build:1.0
