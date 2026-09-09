import re
from itertools import islice
from pathlib import Path
from random import shuffle

import dwdsmor
import dwdsmor.tag.hdt
import spacy
import spacy.tokens
import spacy.vocab

import zdl_lex.wb
from zdl_lex.env import logger

whitespace_run = re.compile(r"\s+")


def el_text(tree):
    if tree.tag == zdl_lex.wb.qn("Streichung"):
        return ""
    text = tree.text or ""
    for child in tree:
        text += el_text(child)
        text += child.tail or ""
    text = whitespace_run.sub(" ", text).strip()
    return text


example_qn = zdl_lex.wb.qn("Belegtext")


def extract_wb_examples(version):
    articles = zdl_lex.wb.download(version)
    examples = [e for a in articles for e in a.iter(example_qn)]
    shuffle(examples)
    return (el_text(e) for e in examples)


def valid_analysis(a):
    return not any((a.orthinfo, a.syninfo, a.metainfo))


def morph(token_morph, k):
    v = ",".join(token_morph.get(k))
    return v if v else None


def analyze(analyzer, token):
    traversals = tuple(analyzer.analyze(token.text))
    traversals = tuple(filter(valid_analysis, traversals))
    if len(traversals) == 1:
        return traversals
    token_morph = token.morph
    criteria = {
        k: frozenset(v) if v else None
        for k, v in dwdsmor.tag.hdt.criteria(
            token.tag_,
            morph(token_morph, "Number"),
            morph(token_morph, "Gender"),
            morph(token_morph, "Case"),
            morph(token_morph, "Person"),
            morph(token_morph, "Tense"),
            morph(token_morph, "Degree"),
            morph(token_morph, "Mood"),
            morph(token_morph, "VerbForm"),
            None,  # TODO: separable verbs via syninfo
        ).items()
    }
    criteria_stack = [(k, v) for k, v in criteria.items() if v]
    criteria_stack.reverse()
    while criteria_stack:
        if len(traversals) == 1:
            break
        attr, attr_vals = criteria_stack.pop()
        filtered = tuple(t for t in traversals if getattr(t, attr) in attr_vals)
        traversals = filtered or traversals
    return sorted(traversals, key=lambda t: len(t.spec))


def lemmatized(analyzer, sentence):
    for token in sentence:
        analyses = analyze(analyzer, token)
        lemmata = {a.analysis for a in analyses}
        if len(lemmata) == 1:
            token.lemma_ = next(iter(lemmata))
            continue
        if token.i - token.sent.start == 0:
            lemmata = {lemma.lower() for lemma in lemmata}
            if len(lemmata) == 1:
                token.lemma_ = next(iter(lemmata))
        # only return sentences which have been fully and unambiguously lemmatized
        return None
    return sentence


def lemmatize_examples(examples):
    analyzer = dwdsmor.analyzer()
    for example in examples:
        example = lemmatized(analyzer, example)
        if example:
            yield example


def condense_spacy_doc(vocab, doc):
    return spacy.tokens.Doc(
        vocab=vocab,
        words=[t.text for t in doc],
        spaces=[bool(t.whitespace_) for t in doc],
        lemmas=[t.lemma_ for t in doc],
    )


def prepare(version, nlp):
    if (Path(__file__).parent / "dwdswb.train.spacy").is_file():
        logger.info("DWDSwb dataset already prepared")
        return

    logger.info("Preparing DWDSwb dataset")
    nlp.add_pipe("doc_cleaner")

    examples = extract_wb_examples(version)
    examples = nlp.pipe(examples)
    examples = lemmatize_examples(examples)

    num_examples = 1000000
    train, dev, test = [], [], []

    for ei, example in enumerate(islice(examples, num_examples), 0):
        bm = ei % 10
        bucket = train if bm < 8 else dev if bm < 9 else test
        bucket.append(example)
    for split, docs in zip(("train", "dev", "test"), (train, dev, test)):
        vocab = spacy.vocab.Vocab()
        docs = tuple(condense_spacy_doc(vocab, d) for d in docs)
        doc_bin = spacy.tokens.DocBin(docs=docs, store_user_data=True)
        (Path(__file__).parent / f"dwdswb.{split}.spacy").write_bytes(
            doc_bin.to_bytes()
        )

    nlp.remove_pipe("doc_cleaner")
