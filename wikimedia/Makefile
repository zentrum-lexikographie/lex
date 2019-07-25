all: data/dewiktionary.xml dkpro-jwktl

data/dewiktionary.xml:
	mkdir -p data
	curl -L\
		https://dumps.wikimedia.org/dewiktionary/latest/dewiktionary-latest-pages-articles.xml.bz2 |\
		bunzip2 >data/dewiktionary.xml

.PHONY: dkpro-jwktl
dkpro-jwktl:
	git submodule update --init --recursive $@
	cd $@ ; mvn install

.PHONY: clean
clean:
	rm -r data
