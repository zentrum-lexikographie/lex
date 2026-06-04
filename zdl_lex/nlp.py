import os

import conllu

from dwds_wic_sbert import WiCTransformer
from zdl_nlp.annotate import create_pipe
from zdl_nlp.conllu import marked_text


batch_size = int(os.environ.get("ZDL_LEX_NLP_BATCH_SIZE", "8"))
n_procs = int(os.environ.get("ZDL_LEX_NLP_NUM_PROCS", "-1"))
gpus = os.environ.get("ZDL_LEX_NLP_GPUS", "")
gpus = [int(g) for g in gpus.split(",") if g]

nlp = create_pipe(batch_size=batch_size, n_procs=n_procs, gpus=gpus)
wic_tf = WiCTransformer.load()


def to_token(t):
    n, t = t
    form, space_after = t
    t = conllu.Token(id=n, form=form, misc={})
    if not space_after:
        t["misc"]["SpaceAfter"] = "No"
    return t


def to_token_list(s):
    return conllu.TokenList(list(map(to_token, enumerate(s, 1))))


def to_dict(s):
    return dict(s.metadata) | {"tokens": tuple(dict(t) for t in s)}


def annotate(sentences):
    sentences = (to_token_list(s) for s in sentences)
    sentences = nlp(sentences)
    return tuple((to_dict(s) for s in sentences))


def embed(sentence_texts):
    embeddings = wic_tf.encode(
        list(sentence_texts),
        batch_size=batch_size,
        show_progress_bar=False,
    )
    return [e.tolist() for e in embeddings]
