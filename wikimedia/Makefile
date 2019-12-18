all: reports/dewiktionary-authors-2019.csv
reports/dewiktionary-authors-2019.csv:
	mkdir -p reports
	WIKIMEDIA_DUMP_HUGE=1 clojure -J-Xmx10g\
		-m zdl-lex-wikimedia.wiktionary.history\
		--last-year authors\
		>$@

all: reports/dewiktionary-edits.csv
reports/dewiktionary-edits.csv:
	mkdir -p reports
	WIKIMEDIA_DUMP_HUGE=1 clojure -J-Xmx10g\
		-m zdl-lex-wikimedia.wiktionary.history\
		edits\
		>$@

all: reports/refs.csv
reports/refs.csv:
	mkdir -p reports
	clojure	-m zdl-lex-wikimedia.wiktionary.refs >$@

.PHONY: clean
clean:
	$(RM) reports
