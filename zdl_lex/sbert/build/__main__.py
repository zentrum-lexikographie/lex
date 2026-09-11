import re
import sys
from itertools import combinations_with_replacement, islice, product
from os import environ
from pathlib import Path
from subprocess import check_call

from datasets import Dataset
from sentence_transformers.sentence_transformer import losses
from sentence_transformers.sentence_transformer.evaluation import (
    EmbeddingSimilarityEvaluator,
)
from sentence_transformers.sentence_transformer.trainer import (
    SentenceTransformerTrainer,
)
from sentence_transformers.sentence_transformer.training_args import (
    SentenceTransformerTrainingArguments,
)
from sentence_transformers.util.similarity import SimilarityFunction

import zdl_lex.spacy
import zdl_lex.wb
from zdl_lex.env import lex_project, logger
from zdl_lex.sbert import WiCTransformer
from zdl_lex.util import monthly_release

dwdswb_version, *_ = zdl_lex.wb.versions()
version = monthly_release(dwdswb_version)
packages = lex_project().packages.list(package_type="pypi")
for p in packages:
    if p.attributes["name"] != "de-zdl-sbert":
        continue
    if p.attributes["version"] == version:
        logger.info(f"WiC SBERT model v{version} already built")
        sys.exit(0)

whitespace_run = re.compile(r"\s+")


def el_text(tree):
    if tree.tag == zdl_lex.wb.qn("Streichung"):
        return ""
    text = tree.text or ""
    for child in tree:
        text += el_text(child)
        text += child.tail or ""
    text = whitespace_run.sub(" ", text).strip()
    if tree.tag == zdl_lex.wb.qn("Stichwort"):
        text = f"<t>{text}</t>"
    return text


def example_texts(sense):
    for e in zdl_lex.wb.xpath(sense, "./d:Verwendungsbeispiele/d:Beleg"):
        # only examples with source
        if zdl_lex.wb.xpath(e, "./d:Fundstelle"):
            for et in zdl_lex.wb.xpath(e, "./d:Belegtext"):
                # only examples with marked headword
                if zdl_lex.wb.xpath(et, ".//d:Stichwort"):
                    yield el_text(et)


def extract_form_property(tree, xpath_suffix):
    elements = zdl_lex.wb.xpath(
        tree, f"./d:Formangabe[@Typ='Hauptform']/{xpath_suffix}"
    )
    elements += zdl_lex.wb.xpath(tree, f"./d:Formangabe/{xpath_suffix}")
    property = ""
    for e in elements:
        property = el_text(e)
        if property:
            break
    return property


def extract_dataset(max_examples_per_sense=3):
    for article in zdl_lex.wb.download(dwdswb_version):
        lemma = extract_form_property(article, "d:Schreibung")
        pos = extract_form_property(article, "d:Grammatik/d:Wortklasse")
        example = {
            "lemma": lemma,
            "pos": pos,
            "type": article.attrib.get("Typ", ""),
            "source": article.attrib.get("Quelle", ""),
            "tranche": article.attrib.get("Tranche", ""),
            "date": article.attrib.get("Zeitstempel", ""),
            "status": article.attrib.get("Status", ""),
        }
        example_sets = tuple(
            tuple(islice(set(example_texts(s)), max_examples_per_sense))
            for s in zdl_lex.wb.xpath(article, "./d:Lesart")
        )
        for es1, es2 in combinations_with_replacement(example_sets, 2):
            score = 1.0 if es1 == es2 else 0.0
            for e1, e2 in product(es1, es2):
                if e1 == e2:
                    continue
                yield example | {"score": score, "sentence1": e1, "sentence2": e2}


train = []  # type: ignore
test = []  # type: ignore

for ei, e in enumerate(extract_dataset()):
    bucket = train if (ei % 10) < 8 else test
    bucket.append(e)


def select_columns(ds):
    return ds.select_columns(["sentence1", "sentence2", "score"])


train_dataset = select_columns(Dataset.from_list(train))
test_dataset = select_columns(Dataset.from_list(test))

args = SentenceTransformerTrainingArguments(
    output_dir="model",
    num_train_epochs=5,
    learning_rate=1e-05,
    weight_decay=0.00,
    per_device_train_batch_size=32,
    per_device_eval_batch_size=32,
    warmup_ratio=0.1,
    bf16=True,
    eval_strategy="steps",
    eval_steps=2500,
    save_strategy="steps",
    save_steps=2500,
    save_total_limit=2,
    logging_steps=500,
    max_steps=500,
    dataloader_drop_last=True,
    run_name="zdl_lex_wic_sbert_train",
)

evaluator = EmbeddingSimilarityEvaluator(
    sentences1=test_dataset["sentence1"],
    sentences2=test_dataset["sentence2"],
    scores=test_dataset["score"],
    main_similarity=SimilarityFunction.COSINE,
    name="zdl_lex_wic_sbert_dev",
)

model = WiCTransformer("xlm-roberta-large")

trainer = SentenceTransformerTrainer(
    model=model,
    args=args,
    train_dataset=train_dataset,
    eval_dataset=test_dataset,
    loss=losses.ContrastiveLoss(model=model),
    evaluator=evaluator,
)
trainer.train()

package_path = Path("zdl_lex/sbert/model")
module_path = package_path / "de_zdl_sbert"
model_path = module_path / "data"
model.save(str(model_path))

(module_path / "__init__.py").touch()
(module_path / "version.py").write_text(f'__version__ = "{version}"')

check_call([sys.executable, "-m", "build", "--wheel"], cwd=package_path)

repo_url = environ.get("TWINE_REPO", "")
if repo_url:
    for whl in (package_path / "dist").rglob("*.whl"):
        check_call(("twine", "upload", "--repository-url", repo_url, whl.as_posix()))
