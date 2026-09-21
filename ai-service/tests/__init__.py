"""Point NLTK at the corpora shipped in ai-service/nltk_data.

The service does this in Engines.load_all(); tests never call that, so without
this every label-filtering test fails on a missing `words` corpus.
"""
from app.config import get_settings
from app.pipeline.labels import ensure_nltk_data

ensure_nltk_data(str(get_settings().nltk_data_dir))
