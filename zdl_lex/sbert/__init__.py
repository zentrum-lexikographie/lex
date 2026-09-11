import importlib.resources
import logging
import warnings
from pathlib import Path

from sentence_transformers import SentenceTransformer
from torch import tensor

start_tag = "<t>"
end_tag = "</t>"


class ModelLoaderLoggingContext:
    def __init__(self):
        self.logger = logging.getLogger("sentence_transformers.SentenceTransformer")

    def __enter__(self):
        self.logger_level = self.logger.level
        self.logger.setLevel(logging.ERROR)

    def __exit__(self, _et, _ev, _eb):
        self.logger.setLevel(self.logger_level)


class WiCTransformer(SentenceTransformer):
    @staticmethod
    def load(*args, **kwargs):
        import de_zdl_sbert

        model_package_path = Path(importlib.resources.files(de_zdl_sbert))  # type: ignore
        model = (model_package_path / "data").as_posix()
        with ModelLoaderLoggingContext(), warnings.catch_warnings():
            warnings.simplefilter("ignore")
            return WiCTransformer(model, *args, **kwargs)

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self.tokenizer.add_tokens([start_tag, end_tag])
        if hasattr(self._first_module().auto_model, "resize_token_embeddings"):
            self._first_module().auto_model.resize_token_embeddings(  # type: ignore
                len(self.tokenizer), mean_resizing=False
            )
        tag_ids = self.tokenizer.convert_tokens_to_ids([start_tag, end_tag])
        self.start_tag_id, self.end_tag_id = tag_ids

        self.max_token_length = self.get_max_seq_length()
        assert self.max_token_length is not None
        assert self.tokenizer.num_special_tokens_to_add() == 2
        self.max_token_length -= 2

    def tokenize(self, texts, **kwargs):
        tokenizer = self.tokenizer
        tokens = []
        for text in texts:
            tokenized = tokenizer(
                text,
                add_special_tokens=False,
                return_attention_mask=False,
                verbose=False,
            )
            token_ids = tokenized["input_ids"]
            if len(token_ids) > self.max_token_length:  # type: ignore
                token_ids = self.center_wic(token_ids)
            token_ids.insert(0, tokenizer.cls_token_id)
            token_ids.append(tokenizer.sep_token_id)
            tokens.append(token_ids)
        max_token_length = max(len(token_ids) for token_ids in tokens)
        input_ids = []
        attention_mask = []
        for token_ids in tokens:
            token_length = len(token_ids)
            pad_length = max_token_length - token_length
            input_ids.append(token_ids + ([tokenizer.pad_token_id] * pad_length))
            attention_mask.append(([1] * token_length) + ([0] * pad_length))
        return {
            "input_ids": tensor(input_ids),
            "attention_mask": tensor(attention_mask),
        }

    def center_wic(self, token_ids):
        start_wic = token_ids.index(self.start_tag_id)
        end_wic = token_ids.index(self.end_tag_id) + 1
        assert start_wic < end_wic
        length = len(token_ids)
        context_length = int((self.max_token_length - (end_wic - start_wic)) / 2.0)
        end = max(end_wic, length - context_length)
        start = max(0, end - self.max_token_length)
        return token_ids[start:end]
