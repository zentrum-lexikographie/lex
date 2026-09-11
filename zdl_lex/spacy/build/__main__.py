import json
import sys
from collections import namedtuple
from os import environ
from pathlib import Path
from subprocess import check_call

import spacy
from spacy.cli.download import download
from spacy.cli.evaluate import evaluate
from spacy.cli.init_config import init_config
from spacy.cli.package import package
from spacy.cli.train import train

import zdl_lex.spacy.datasets.dwdswb
import zdl_lex.spacy.datasets.hdt
import zdl_lex.spacy.datasets.ner_d
import zdl_lex.wb
from zdl_lex.env import lex_project, logger
from zdl_lex.util import monthly_release

dwdswb_version, *_ = zdl_lex.wb.versions()
version = monthly_release(dwdswb_version)
packages = lex_project().packages.list(package_type="pypi")
for p in packages:
    if p.attributes["name"] not in {"de-zdl-dist", "de-zdl-lg"}:
        continue
    if p.attributes["version"] == version:
        logger.info(f"spaCy models v{version} already built")
        sys.exit(0)

max_steps = int(environ.get("MAX_STEPS", "20000"))
gpu_id = int(environ.get("GPU_ID", "0"))
repo_url = environ.get("TWINE_REPO", "")

dataset_dir = Path(__file__).parent.parent / "datasets"

build_dir = Path(__file__).parent
configs_dir = build_dir / "configs"
training_dir = build_dir / "training"
packages_dir = build_dir / "packages"

for d in (configs_dir, training_dir, packages_dir):
    d.mkdir(parents=True, exist_ok=True)

pipeline = ["tagger", "morphologizer", "parser", "ner", "trainable_lemmatizer"]


def configure_base(gpu, component_names, base_model):
    config = init_config(lang="de", pipeline=pipeline, optimize="accuracy", gpu=gpu)

    # fill config
    nlp = spacy.util.load_model_from_config(config, auto_fill=True, validate=False)
    nlp = spacy.util.load_model_from_config(nlp.config)
    config = nlp.config

    if gpu:
        transformer_model = config["components"]["transformer"]["model"]
        transformer_model["name"] = "distilbert-base-german-cased"

    score_weights = config["training"]["score_weights"]
    for weight in ("ents_f", "ents_p", "ents_r", "lemma_acc"):
        score_weights[weight] = 0.0

    return config


def configure_lemmatizer(gpu, component_names, base_model):
    nlp = spacy.load(base_model)
    zdl_lex.spacy.datasets.dwdswb.prepare(dwdswb_version, nlp)

    if not gpu:
        # retrain lemmatizer with CPU-optimized defaults
        nlp.remove_pipe("trainable_lemmatizer")
        nlp.add_pipe("trainable_lemmatizer")

    config = nlp.config.copy()

    if gpu:
        config["training"]["annotating_components"] = ["transformer"]

    components = config["components"]
    frozen = config["training"]["frozen_components"] = []
    for c in component_names:
        if c != "trainable_lemmatizer":
            components[c] = {"source": base_model}
            frozen.append(c)

    # TODO
    # https://github.com/jmyerston/greCy/blob/main/configs/lemmatizer_trf.cfg
    #
    # lemmatizer = components["trainable_lemmatizer"]
    # lemmatizer["min_tree_freq"] = 1
    # lemmatizer["top_k"] = 7
    # lemmatizer["overwrite"] = True

    score_weights = config["training"]["score_weights"] = {}
    score_weights["lemma_acc"] = 1.0

    return config


def configure_ner(gpu, component_names, base_model):
    config = spacy.load(base_model).config.copy()

    components = config["components"]
    frozen = config["training"]["frozen_components"] = []
    for c in component_names:
        component = components[c] = {"source": base_model}
        if c == "ner":
            component["replace_listeners"] = ["model.tok2vec"]
        else:
            frozen.append(c)

    score_weights = config["training"]["score_weights"] = {}
    score_weights["ents_f"] = 1.0

    return config


def perf_base(perf, base_perf):
    return base_perf


def perf_lemma(perf, lemma_perf):
    perf["lemma_acc"] = lemma_perf["lemma_acc"]
    return perf


def perf_ner(perf, ner_perf):
    perf["ents_p"] = ner_perf["ents_p"]
    perf["ents_r"] = ner_perf["ents_r"]
    perf["ents_f"] = ner_perf["ents_f"]
    return perf


TrainingStage = namedtuple("TrainingStage", ["corpus", "configure", "merge_perf"])

training_stages = [
    TrainingStage("hdt", configure_base, perf_base),
    TrainingStage("dwdswb", configure_lemmatizer, perf_lemma),
    TrainingStage("ner-d", configure_ner, perf_ner),
]

logger.info(f"Building spaCy models v{version}")

base_model = "de_core_news_lg"
try:
    spacy.load(base_model)
    logger.info(f"spaCy base model '{base_model}' available")
except OSError:
    download(base_model)
    logger.info(f"spaCy base model '{base_model}' downloaded")

logger.info("Preparing datasets")
zdl_lex.spacy.datasets.hdt.prepare()
zdl_lex.spacy.datasets.ner_d.prepare()

for gpu in (True, False):
    model_type = "GPU" if gpu else "CPU"
    logger.info(f"Building {model_type} model")
    component_names = pipeline.copy()
    component_names.insert(0, "transformer" if gpu else "tok2vec")

    model = None
    for stage in training_stages:
        logger.info(f"Training {model_type}/{stage.corpus} stage")
        id = ".".join(("gpu" if gpu else "cpu", stage.corpus))
        cfg_path = configs_dir / f"{id}.cfg"
        training_path = training_dir / id
        model_path = training_path / "model-best"
        if model_path.is_dir():
            model = str(model_path)
            continue

        config = stage.configure(gpu, component_names, model)
        config["paths"]["train"] = f"zdl_lex/spacy/datasets/{stage.corpus}.train.spacy"
        config["paths"]["dev"] = f"zdl_lex/spacy/datasets/{stage.corpus}.dev.spacy"
        config["training"]["max_steps"] = max_steps
        if model:
            config["initialize"]["before_init"] = {
                "@callbacks": "spacy.copy_from_base_model.v1",
                "tokenizer": model,
                "vocab": model,
            }

        config.to_disk(cfg_path)
        train(cfg_path, training_path, use_gpu=gpu_id)
        model = str(model_path)

    assert model is not None

    perf = None
    for stage in training_stages:
        logger.info(f"Evaluating {model_type}/{stage.corpus} training")
        perf = stage.merge_perf(
            perf,
            evaluate(
                model,
                dataset_dir / f"{stage.corpus}.test.spacy",
                silent=False,
                use_gpu=gpu_id,
            ),
        )
    meta_path = Path(model) / "meta.json"
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    meta["performance"] = perf
    meta_path.write_text(json.dumps(meta, indent=2), encoding="utf-8")

    logger.info(f"Packaging {model_type} model")
    package(
        Path(model),
        packages_dir,
        name=("zdl_" + ("dist" if gpu else "lg")),
        version=version,
        create_wheel=True,
        create_sdist=False,
        force=True,
        silent=False,
    )

if repo_url:
    for whl in packages_dir.rglob("*.whl"):
        check_call(("twine", "upload", "--repository-url", repo_url, whl.as_posix()))
