import json
from collections import Counter

import numpy
import scipy.stats
from zdl_nlp.annotate import create_pipe
from zdl_nlp.conllu import lemma_text
from zdl_nlp.segment import segment

nlp = create_pipe(batch_size=8, n_procs=2)


def extract_tokens(chunks):
    token_count = 0
    token_freqs = Counter()
    for s in nlp(segment(*chunks)):
        entities = json.loads(s.metadata.get("entities", "[]"))
        entities = {id for e in entities for id in e[1:]}
        for t in s:
            token_count += 1
            if t["id"] in entities:
                continue
            if t.get("upos", "X") not in {"ADJ", "VERB", "NOUN"}:
                continue
            token_freqs[lemma_text(t)] += 1
    return {"tokens": token_count, "freqs": dict(token_freqs)}


def metrics(freqs):
    x = numpy.arange(len(freqs))
    y = numpy.array(freqs)
    slope, intercept, *_ = scipy.stats.theilslopes(y, x)
    tau, p_value = scipy.stats.kendalltau(x, y)
    return {"slope": slope, "intercept": intercept, "tau": tau, "p-value": p_value}
