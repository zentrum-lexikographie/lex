all: data/dewiktionary.xml

data/dewiktionary.xml:
	mkdir -p data
	curl -L\
		https://dumps.wikimedia.org/dewiktionary/latest/dewiktionary-latest-pages-articles.xml.bz2 |\
		bunzip2 >data/dewiktionary.xml

.PHONY: clean
clean:
	rm -r data
