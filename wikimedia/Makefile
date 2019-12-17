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

.PHONY: clean
clean:
	$(RM) reports
