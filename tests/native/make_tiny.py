"""Builds a tiny random-weight llama GGUF that uses the real llama SPM tokenizer.

It is only for testing the JNI bridge end to end: the output is gibberish,
but tokenization, chat template, sampling, streaming and UTF-8 handling all run
through the real llama.cpp code paths.
"""
import sys

import numpy as np

import os
sys.path.insert(0, os.environ["GGUF_PY_DIR"])
from gguf import GGUFReader, GGUFValueType, GGUFWriter  # noqa: E402

VOCAB = os.environ["LLAMA_VOCAB_GGUF"]
OUT = sys.argv[1] if len(sys.argv) > 1 else "tiny-llama-f32.gguf"

N_EMBD = 64
N_HEAD = 4
N_FF = 128
N_LAYER = 1
CTX = 512

reader = GGUFReader(VOCAB)
tokens_field = reader.get_field("tokenizer.ggml.tokens")
n_vocab = len(tokens_field.contents())
print("vocab size:", n_vocab)

writer = GGUFWriter(OUT, "llama")
writer.add_architecture()
writer.add_uint32("llama.context_length", CTX)
writer.add_uint32("llama.embedding_length", N_EMBD)
writer.add_uint32("llama.block_count", N_LAYER)
writer.add_uint32("llama.feed_forward_length", N_FF)
writer.add_uint32("llama.attention.head_count", N_HEAD)
writer.add_uint32("llama.attention.head_count_kv", N_HEAD)
writer.add_float32("llama.attention.layer_norm_rms_epsilon", 1e-5)
writer.add_uint32("llama.rope.dimension_count", N_EMBD // N_HEAD)
writer.add_string("general.name", "soplau-tiny-test")

# Copy the tokenizer metadata verbatim from the reference vocab file.
for field in reader.fields.values():
    if not field.name.startswith("tokenizer."):
        continue
    vt = field.types[0]
    if vt == GGUFValueType.ARRAY:
        writer.add_key_value(field.name, field.contents(), GGUFValueType.ARRAY, field.types[1])
    else:
        writer.add_key_value(field.name, field.contents(), vt)

rng = np.random.default_rng(0)


def w(*shape):
    return (rng.standard_normal(shape) * 0.02).astype(np.float32)


def ones(n):
    return np.ones((n,), dtype=np.float32)


writer.add_tensor("token_embd.weight", w(n_vocab, N_EMBD))
writer.add_tensor("output_norm.weight", ones(N_EMBD))
writer.add_tensor("output.weight", w(n_vocab, N_EMBD))
for i in range(N_LAYER):
    p = f"blk.{i}."
    writer.add_tensor(p + "attn_norm.weight", ones(N_EMBD))
    writer.add_tensor(p + "attn_q.weight", w(N_EMBD, N_EMBD))
    writer.add_tensor(p + "attn_k.weight", w(N_EMBD, N_EMBD))
    writer.add_tensor(p + "attn_v.weight", w(N_EMBD, N_EMBD))
    writer.add_tensor(p + "attn_output.weight", w(N_EMBD, N_EMBD))
    writer.add_tensor(p + "ffn_norm.weight", ones(N_EMBD))
    writer.add_tensor(p + "ffn_gate.weight", w(N_FF, N_EMBD))
    writer.add_tensor(p + "ffn_up.weight", w(N_FF, N_EMBD))
    writer.add_tensor(p + "ffn_down.weight", w(N_EMBD, N_FF))

writer.write_header_to_file()
writer.write_kv_data_to_file()
writer.write_tensors_to_file()
writer.close()
print("wrote", OUT)
