# ZDL/Lex – Dictionary Writing System

_A client/server application implementing an authoring environment for
lexicographic articles at the [ZDL](https://www.zdl.org/)_

![Schreibtisch eines Philologen by Die.keimzelle / Wikimedia Commons / CC-BY-3.0](https://upload.wikimedia.org/wikipedia/commons/thumb/d/dd/Schreibtisch_eines_Philologen.jpg/960px-Schreibtisch_eines_Philologen.jpg)

## Prerequisites

For building versions of the authoring environment, the following
software is required:

* [GNU/Linux](https://www.debian.org/): Development, builds and tests
  of the platform are performed on Linux. While other UNIX-like
  operating systems (i. e. MacOS) might work, they are not
  supported. The same goes for MS Windows.
* [Docker](https://docs.docker.com/get-docker/): The server and the search
  service require a Java runtime and [Apache Solr](https://solr.apache.org/). To
  ease the setup and deployment of these services, both are containerized and
  assume a Docker environment for testing and in production.

For developing the authoring environment, the following software is
required:

* [Clojure](https://clojure.org/): The server component, mediating between the
  [Oxygen-XML-Editor](https://www.oxygenxml.com/)-based client, a Git-based data
  store and a search service, is written Clojure, a LISP dialect. The same goes
  for Oxygen-XML's project-specific extensions.
* [Java (JDK)](https://openjdk.java.net/): Clojure, being a hosted language,
  requires a current Java runtime.

## Build and Release

Building a (new) version of the authoring environment:

```plaintext
$ scripts/release
```

This will trigger a build of 2 Docker container images, one for the
customized Solr Search Server, one for the HTTP-based middleware, which
also contains the plugin and a framework extending Oxygen-XML-Editor.

After the build finishes, the images are pushed to the ZDL-internal
Docker Registry for subsequent deployment.

## Development and Testing

### Configuration

ZDL/Lex is configured via environment variables, in line with the [Twelve-Factor
App guidelines](https://12factor.net/). Variable settings are read from `.env`
files in the current working directory as well the respective process
environment.

To configure the build and development environment, copy `.env.sample` to `.env`
in the project directory and adjust the settings to your needs. See the comments
in the sample file for a documentation of the available options. Example:

```plaintext
# disconnect test setup from production origin
ZDL_LEX_GIT_ORIGIN=

ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

### Test

Before releasing new versions of the application, its client and server
components can be tested locally.

Make sure settings in `.env` point to the local server instance and provide test
credentials, i.e.:

```plaintext
ZDL_LEX_SERVER_URL=http://localhost:3000/
ZDL_LEX_SERVER_USER=admin
ZDL_LEX_SERVER_PASSWORD=admin
```

To build and run a local server instance as a Docker container:

```plaintext
$ docker compose up --build
```

The server component relies on [Apache Solr](https://lucene.apache.org/solr/)
for its facetted search functionality, reachable via

http://localhost:8983/solr

The server container is reachable at

http://localhost:3000/

Then, start the Oxygen XML Editor with the client plugin installed from the
current project sources via

```plaintext
$ clojure -T:build start-editor
```

### Extracting random test data from DWDS sources

    clojure -M:test -m zdl.lex.dev.test-data $DWDS_WB_GIT_DIR

# Custom spaCy Models

_German spaCy models trained on UD-HDT and custom datasets for NER and lemmatization_

This project trains a [spaCy](https://spacy.io/) pipeline with two
models, geared towards the **annotation of German texts for
lexicographic use cases** at the [Zentrum für digitale Lexikographie
der deutschen Sprache](https://www.zdl.org/). The pipeline comprises a
part-of-speech tagger, morphologizer, lemmatizer, syntactic dependency
parser and NER tagger. The notable differences in comparison to
spaCy's default pipeline for German are:

1. The tagger and dependency parser are trained on data from the
   [Hamburg Dependency Treebank](https://aclanthology.org/L14-1666/)
   (HDT). Its tagset, rather than the one from the TIGER corpus, is
   more amenable to ZDL-specific downstream tasks like collocation
   extraction.
1. The NER tagger is trained on an aggregated custom dataset of
   several gold and silver standards (see below for
   details). Moreover, it is an integral part of the pipeline for CPU
   and GPU architectures because recognizing named entities in
   examined textual evidence is a central capability in the context of
   lexicography.
1. As the same applies to the lemmatization of word forms, we train
   spaCy's probabilistic lemmatizer on a large dataset of example
   sentences from ZDL's own lexical resources. These sentences have
   been lemmatized with the deterministic lemmatizer
   [DWDSmor](https://github.com/zentrum-lexikographie/dwdsmor) and
   therefore adhere to the expected conventions of the ZDL.

Evaluated against test splits of the aforementioned datasets, the two
models trained perform as follows:

| Annotation Type           | Accuracy (static emb.) | Accuracy (contextual emb.) |
|:--------------------------|-----------------------:|---------------------------:|
| PoS Tagging               |                 97.69% |                     98.45% |
| Morphological Features    |                 91.33% |                     93.97% |
| Syntactic Relations (LAS) |                 92.45% |                     95.52% |
| Syntactic Relations (UAS) |                 94.69% |                     96.77% |
| Lemmatization             |                 98.62% |                     98.64% |
| Named Entities (f-score)  |                 75.19% |                     87.71% |

One model is trained on static embeddings and should provide higher
throughput on CPU architectures, while the other is trained on
contextual embeddings provided by a transformer base model and should
provide higher accuracy but require GPU hardware for annotating larger
corpora.

## Usage

The models are available from our package registry at [Git.UP](https://gitup.uni-potsdam.de/):

``` shell
pip install de-zdl-lg --index-url https://gitup.uni-potsdam.de/api/v4/projects/21461/packages/pypi/simple
pip install de-zdl-dist --index-url https://gitup.uni-potsdam.de/api/v4/projects/21461/packages/pypi/simple
```

The first package (with suffix `-lg`) contains the model with static
word embeddings, the second (with suffix `-dist`) provides the model
based on [DistilBERT](https://arxiv.org/abs/1910.01108).

Once installed, you can use the pipelines like any other spaCy pipeline, e.g.

``` python
>>> import spacy
>>> nlp = spacy.load("de_zdl_lg") # or "de_zdl_dist"
>>> [(e, e.label_) for e in nlp("Heiner Müller wurde am 9. Januar 1929 in Eppendorf in Sachsen geboren.").ents]
[(Heiner Müller, 'PER'), (Eppendorf, 'LOC'), (Sachsen, 'LOC')]
```

## Training Datasets


* Benikova, Darina, Chris Biemann, und Marc Reznicek. „NoSta-D Named Entity Annotation for German: Guidelines and Dataset“. In Proceedings of the Ninth International Conference on Language Resources and Evaluation (LREC’14), herausgegeben von Nicoletta Calzolari, Khalid Choukri, Thierry Declerck, Hrafn Loftsson, Bente Maegaard, Joseph Mariani, Asuncion Moreno, Jan Odijk, und Stelios Piperidis, 2524–31. Reykjavik, Iceland: European Language Resources Association (ELRA), 2014. https://aclanthology.org/L14-1251/.
* Berlin-Brandenburg Academy of Sciences and Humanities (BBAW) (ed.) (n.d.). DWDS – Digitales Wörterbuch der deutschen Sprache: Das Wortauskunftssystem zur deutschen Sprache in Geschichte und Gegenwart. https://www.dwds.de/
* Borges Völker, Emanuel, Maximilian Wendt, Felix Hennig, und Arne Köhn. „HDT-UD: A very large Universal Dependencies Treebank for German“. In Proceedings of the Third Workshop on Universal Dependencies (UDW, SyntaxFest 2019), herausgegeben von Alexandre Rademaker und Francis Tyers, 46–57. Paris, France: Association for Computational Linguistics, 2019. https://doi.org/10.18653/v1/W19-8006.
* Ehrmann, Maud, Matteo Romanello, SImon Clematide, und Alex Flückiger. „CLEF-HIPE-2020 Shared Task Named Entity Datasets“. Zenodo, 11. März 2020. https://zenodo.org/records/6046853.
Hamdi, Ahmed, Elvys Linhares Pontes, Emanuela Boros, Thi Tuyet Hai Nguyen, Günter Hackl, Jose G. Moreno, und Antoine Doucet. „A Multilingual Dataset for Named Entity Recognition, Entity Linking and Stance Detection in Historical Newspapers“. Gehalten auf der The 44th International ACM SIGIR Conference on Research and Development in Information Retrieval (SIGIR 2021), 15. April 2021. https://doi.org/10.5281/zenodo.4694466.
* Hennig, Leonhard, Phuc Tran Truong, und Aleksandra Gabryszak. „MobIE: A German Dataset for Named Entity Recognition, Entity Linking and Relation Extraction in the Mobility Domain“. In Proceedings of the 17th Conference on Natural Language Processing (KONVENS 2021), herausgegeben von Kilian Evang, Laura Kallmeyer, Rainer Osswald, Jakub Waszczuk, und Torsten Zesch, 223–27. Düsseldorf, Germany: KONVENS 2021 Organizers, 2021. https://aclanthology.org/2021.konvens-1.22/.
* Nothman, Joel, Nicky Ringland, Will Radford, Tara Murphy, und James R. Curran. „Learning multilingual named entity recognition from Wikipedia“. Artificial Intelligence, Artificial Intelligence, Wikipedia and Semi-Structured Resources, 194 (1. Januar 2013): 151–75. https://doi.org/10.1016/j.artint.2012.03.006.
* Schiersch, Martin, Veselina Mironova, Maximilian Schmitt, Philippe Thomas, Aleksandra Gabryszak, und Leonhard Hennig. „A German Corpus for Fine-Grained Named Entity Recognition and Relation Extraction of Traffic and Industry Events“. In Proceedings of the Eleventh International Conference on Language Resources and Evaluation (LREC 2018), herausgegeben von Nicoletta Calzolari, Khalid Choukri, Christopher Cieri, Thierry Declerck, Sara Goggi, Koiti Hasida, Hitoshi Isahara, u. a. Miyazaki, Japan: European Language Resources Association (ELRA), 2018. https://aclanthology.org/L18-1703/.
* Schweter, Stefan. „HisGermaNER (Revision 83571b3)“. Hugging Face, 2025. https://doi.org/10.57967/hf/5770.
* Tjong Kim Sang, Erik F., und Fien De Meulder. „Introduction to the CoNLL-2003 Shared Task: Language-Independent Named Entity Recognition“. In Proceedings of the Seventh Conference on Natural Language Learning at HLT-NAACL 2003, 142–47, 2003. https://aclanthology.org/W03-0419/.
* Zöllner, Jochen, Konrad Sperfeld, Christoph Wick, und Roger Labahn. „Optimizing small BERTs trained for German NER“. Information 12, Nr. 11 (25. Oktober 2021): 443. https://doi.org/10.3390/info12110443.

# Custom Word-in-Context Sentence Transformer

_SBERT Model for Word Sense Disambiguation and Sense-Related Clustering_

This project trains a [sentence-transformers](https://sbert.net/) model finetuning
[XLM-RoBERTa](https://huggingface.co/FacebookAI/xlm-roberta-large) on
example sentences from the DWDS dictionary. Each record in the
training set contains a pair of example sentences and a score,
classifying pairs as sentences exemplifying the same or different
senses of a given word. The trained model then maps sentences to a
dense vector space, contrasting different senses of words in their
respective contexts. Consequently, it should support word sense
disambiguation and sense-related clustering while examining textual
evidence of word usage, i. e. in the context of corpus-based
lexicography.

## References

1. Berlin-Brandenburg Academy of Sciences and Humanities (BBAW) (ed.)
   (n.d.). [DWDS – Digitales Wörterbuch der deutschen Sprache](
   https://www.dwds.de/). Das Wortauskunftssystem zur deutschen
   Sprache in Geschichte und Gegenwart.
1. Pierluigi Cassotti, Lucia Siciliani, Marco DeGemmis, Giovanni
   Semeraro, and Pierpaolo Basile. 2023. [XL-LEXEME: WiC Pretrained
   Model for Cross-Lingual LEXical sEMantic
   changE](https://aclanthology.org/2023.acl-short.135/). In
   Proceedings of the 61st Annual Meeting of the Association for
   Computational Linguistics (Volume 2: Short Papers), pages
   1577–1585, Toronto, Canada. Association for Computational
   Linguistics.

## Links

* XL-DURel
  * [Preprint](https://arxiv.org/pdf/2507.14578)
  * [Github Repository](https://github.com/sachinn12/XL-DURel)
  * [Huggingface Model](https://huggingface.co/sachinn1/xl-durel)


# License

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Lesser Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser Public License for more details.

You should have received a copy of the GNU Lesser Public License
along with this program.  If not, see <https://www.gnu.org/licenses/>.
