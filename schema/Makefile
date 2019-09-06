CSS_DIR = css
RNC_DIR = rnc
RNG_DIR = rng

CSS = $(wildcard $(CSS_DIR)/*.css)
RNC = $(wildcard $(RNC_DIR)/*.rnc)
RNG = $(RNC:$(RNC_DIR)/%.rnc=$(RNG_DIR)/%.rng)

RNG_MAIN = $(RNG_DIR)/DWDSWB.rng
SCH_XSLT = $(RNG_DIR)/DWDSWB.sch.xsl

.PHONY: all
all: $(RNG_MAIN) $(SCH_XSLT) $(CSS)

.PHONY: clean
clean:
	$(RM) $(RNG) $(SCH_XSLT)

.PHONY: deploy
deploy: $(RNC) $(RNG) $(SCH_XSLT) $(CSS)
	for f in `ls rnc/*; ls rng/*`; do\
		curl -n -v -T $$f\
			http://spock.dwds.de:8080/exist/webdav/db/dwdswb/validation/`basename $$f`;\
	done
	for f in `ls css/*`; do\
		curl -n -v -T $$f\
			http://spock.dwds.de:8080/exist/webdav/db/dwdswb/presentation/`basename $$f`;\
	done


$(RNG_MAIN) $(SCH_XSLT): $(RNC)
	mkdir -p $(RNG_DIR)
	make -C .. bin/lein
	cd ../build && ../bin/lein run -m zdl-lex-build.schema
