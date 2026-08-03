"""Label normalization, filtering and matching.

Ported from the `object_scan_florence2` research notebook (cell 6 + parts of cell 8).
Kept close to the original implementation on purpose -- it encodes a lot of
hard-won, empirically-tuned behavior (see inline comments) that isn't obvious
from first principles.
"""
import re

import nltk
from nltk.corpus import wordnet
from nltk.corpus import words as nltk_words
from nltk.stem import WordNetLemmatizer

NLTK_PACKAGES = [
    "wordnet",
    "omw-1.4",
    "words",
    "averaged_perceptron_tagger",
    "averaged_perceptron_tagger_eng",
]


def ensure_nltk_data(download_dir: str) -> None:
    if download_dir not in nltk.data.path:
        nltk.data.path.insert(0, download_dir)
    for pkg in NLTK_PACKAGES:
        nltk.download(pkg, download_dir=download_dir, quiet=True)


_lemmatizer = WordNetLemmatizer()
_ENGLISH_WORDS: set[str] | None = None


def _english_words() -> set[str]:
    global _ENGLISH_WORDS
    if _ENGLISH_WORDS is None:
        _ENGLISH_WORDS = {w.lower() for w in nltk_words.words()}
    return _ENGLISH_WORDS


SCENE_BACKGROUND_STOPWORDS = {
    "wall", "floor", "ceiling", "sky", "room", "background", "shadow", "light",
    "photo", "image", "picture", "texture", "pattern", "surface", "reflection",
    "scene", "view", "outdoor", "indoor", "ground", "area", "space",
    "kitchen", "bathroom", "bedroom", "hallway", "interior",
}

GENERIC_LABEL_BLACKLIST = {
    "food", "dinner", "lunch", "breakfast", "meal", "snack",
    "dish", "cuisine", "produce", "object", "item", "thing", "stuff",
    "groceries", "goods", "product",
}

# Prepositions used to cut a caption short: "a red car parked ON the street" -> "a red car parked"
_PREPOSITIONS = {
    "in", "on", "at", "with", "of", "near", "by", "under", "over", "behind", "beside",
    "above", "below", "around", "against", "across", "during", "from", "to", "next", "front",
}
_CONJUNCTIONS = {"and", "or", "an", "a", "the"}
_FUNC_WORDS = {
    "a", "an", "the", "this", "that", "these", "those", "and", "or",
    "it", "its", "his", "her", "their", "my", "your", "our", "some", "any",
}

BASE_VOCAB = [
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "bench", "bird", "cat", "dog", "horse",
    "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella", "handbag",
    "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite", "baseball bat",
    "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange", "broccoli",
    "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant", "bed",
    "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
    "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors",
    "teddy bear", "hair drier", "toothbrush", "tablet",
]

EXTRA_HOUSEHOLD_VOCAB = [
    "mug", "plate", "jar", "can", "kettle", "pan", "pot", "cutting board", "spatula",
    "ladle", "whisk", "tray", "jug", "pitcher", "glass", "napkin", "towel", "apron",
    "glove", "faucet", "stove", "cabinet", "drawer", "shelf", "basket", "bucket",
    "lamp", "candle", "mirror", "pillow", "blanket", "curtain", "rug", "hanger",
    "toy", "doll", "camera", "headphones", "speaker", "watch", "wallet", "key",
    "pen", "pencil", "notebook", "envelope", "monitor", "printer", "charger", "cable",
    "kale", "lettuce", "tomato", "onion", "garlic", "pepper", "mushroom", "cucumber",
    "corn", "potato", "cabbage", "herb",
    "turkey", "chicken", "ham", "steak", "meat", "egg", "bread", "cheese", "sausage",
    "fish", "shrimp", "salad", "bell pepper", "watermelon", "grape", "strawberry", "lemon",
]

_physical_roots_cache: set | None = None


def _physical_roots() -> set:
    # Lazy: wordnet data is only guaranteed present after ensure_nltk_data() has run
    # (called from Engines.load_all at startup), which is after this module is imported.
    global _physical_roots_cache
    if _physical_roots_cache is None:
        _physical_roots_cache = {wordnet.synset("physical_entity.n.01")}
    return _physical_roots_cache


_CONCRETE_LEXNAMES = {
    "noun.artifact", "noun.animal", "noun.food", "noun.person",
    "noun.plant", "noun.object", "noun.body", "noun.substance",
}
_CONCRETE_WHITELIST = {
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat",
    "traffic light", "fire hydrant", "stop sign", "parking meter", "bench", "bird", "cat", "dog",
    "horse", "sheep", "cow", "elephant", "bear", "zebra", "giraffe", "backpack", "umbrella",
    "handbag", "tie", "suitcase", "frisbee", "skis", "snowboard", "sports ball", "kite",
    "baseball bat", "baseball glove", "skateboard", "surfboard", "tennis racket", "bottle",
    "wine glass", "cup", "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch", "potted plant",
    "bed", "dining table", "toilet", "tv", "laptop", "mouse", "remote", "keyboard", "cell phone",
    "microwave", "oven", "toaster", "sink", "refrigerator", "book", "clock", "vase", "scissors",
    "teddy bear", "hair drier", "toothbrush", "ring", "sign", "kite",
}

LABEL_ALIASES: dict[str, set[str]] = {
    "person": {"man", "woman", "boy", "girl", "child", "kid", "lady", "guy", "people",
               "player", "pedestrian", "rider", "baby", "human"},
    "tv": {"television", "monitor", "screen", "tv set"},
    "couch": {"sofa", "settee"},
    "motorcycle": {"motorbike"},
    "bicycle": {"bike", "cycle"},
    "cell phone": {"phone", "mobile phone", "smartphone", "telephone"},
    "sports ball": {"ball", "soccer ball", "basketball", "baseball", "tennis ball", "football", "volleyball"},
    "potted plant": {"plant", "houseplant", "flowerpot", "pot plant"},
    "dining table": {"table", "desk"},
    "hair drier": {"hair dryer", "dryer", "blow dryer"},
    "airplane": {"plane", "aircraft", "jet"},
    "boat": {"ship", "sailboat", "canoe", "kayak"},
    "car": {"automobile", "suv", "sedan", "taxi", "van"},
    "remote": {"remote control", "controller"},
    "handbag": {"purse", "bag"},
    "backpack": {"rucksack", "knapsack"},
    "wine glass": {"wineglass", "glass"},
    "traffic light": {"stoplight", "traffic signal"},
    "tire": {"wheel", "tyre"},
    "auto part": {"wheel", "tire", "bumper", "headlight", "windshield", "mirror"},
    "land vehicle": {"car", "truck", "bus", "van", "motorcycle", "bicycle", "vehicle"},
    "vehicle registration plate": {"license plate", "number plate", "plate"},
    "human head": {"head", "face", "human face"},
    "human body": {"body", "person", "torso"},
    "sports equipment": {"ball", "racket", "bat", "glove", "helmet", "ski"},
    "clothing": {"shirt", "jacket", "dress", "coat", "t-shirt", "pants", "trousers",
                 "trouser", "jeans", "uniform", "jersey", "shorts"},
    "footwear": {"shoe", "sneaker", "boot", "sandal", "cleat"},
}


def normalize_word(word: str) -> str:
    word = word.lower().strip()
    return _lemmatizer.lemmatize(word, pos="n")


def is_valid_dictionary_word(word: str) -> bool:
    word = word.lower().strip()
    if not word.replace("-", "").isalpha():
        return False
    if word in _english_words() or wordnet.synsets(word):
        return True
    lemma = normalize_word(word)
    return lemma in _english_words() or bool(wordnet.synsets(lemma))


def is_scene_background_word(tag: str) -> bool:
    tokens = tag.strip().lower().split()
    if len(tokens) != 1:
        return False
    return normalize_word(tokens[0]) in SCENE_BACKGROUND_STOPWORDS


def caption_to_label(caption: str) -> str | None:
    """Extract the trailing noun phrase before the first preposition as the flashcard label.

    "a red vintage car parked on the street" -> "car"; "traffic light" -> "traffic light".
    """
    toks = re.findall(r"[a-z]+", caption.lower())
    cut = []
    for t in toks:
        if t in _PREPOSITIONS:
            break
        if t in _CONJUNCTIONS:
            continue
        cut.append(t)
    if not cut:
        return None

    tagged = nltk.pos_tag(cut)
    run: list[str] = []
    best: list[str] = []
    for w, p in tagged:
        is_nounish = (
            w not in _FUNC_WORDS and len(w) > 1
            and (p.startswith("NN") or bool(wordnet.synsets(w, pos=wordnet.NOUN)))
        )
        if is_nounish:
            run.append(normalize_word(w))
        else:
            if run:
                best = run
            run = []
    if run:
        best = run
    if not best:
        return None
    if len(best) >= 2 and wordnet.synsets("_".join(best[-2:])):
        return " ".join(best[-2:])
    return best[-1] or None


def _sense_is_concrete(syn, max_up: int = 20) -> bool:
    if syn.lexname() in _CONCRETE_LEXNAMES:
        return True
    frontier = {syn}
    for _ in range(max_up):
        if frontier & _physical_roots():
            return True
        nxt = set()
        for s in frontier:
            nxt.update(s.hypernyms())
        if not nxt:
            return False
        frontier = nxt
    return False


def is_concrete_object_word(tag: str) -> bool | None:
    t = tag.strip().lower()
    if t in _CONCRETE_WHITELIST:
        return True
    key = t.replace(" ", "_")
    syns = wordnet.synsets(key, pos=wordnet.NOUN)
    if not syns and " " in t:
        syns = wordnet.synsets(t.split()[-1], pos=wordnet.NOUN)
    if not syns:
        return False if wordnet.synsets(key) else None
    if _sense_is_concrete(syns[0]):
        return True
    return sum(_sense_is_concrete(s) for s in syns[1:3]) >= 2


def check_synonyms(word1: str, word2: str) -> bool:
    w1, w2 = word1.lower().strip(), word2.lower().strip()
    if w1 == w2:
        return True
    syns1 = set()
    for syn in wordnet.synsets(w1.replace(" ", "_")):
        for lemma in syn.lemmas():
            syns1.add(lemma.name().lower().replace("_", " "))
    return w2 in syns1


def _hypernym_match(det_label: str, gt_label: str, max_up: int = 6) -> bool:
    det_syns = wordnet.synsets(det_label.replace(" ", "_"), pos=wordnet.NOUN)
    gt_syns = set(wordnet.synsets(gt_label.replace(" ", "_"), pos=wordnet.NOUN))
    if not det_syns or not gt_syns:
        return False
    frontier = set(det_syns)
    for _ in range(max_up):
        nxt = set()
        for s in frontier:
            nxt.update(s.hypernyms())
        if nxt & gt_syns:
            return True
        frontier = nxt
        if not frontier:
            break
    return False


def labels_match(det_label: str, gt_label: str) -> bool:
    d, g = det_label.lower().strip(), gt_label.lower().strip()
    if check_synonyms(d, g):
        return True
    if g in LABEL_ALIASES and d in LABEL_ALIASES[g]:
        return True
    if d in LABEL_ALIASES and g in LABEL_ALIASES[d]:
        return True
    return _hypernym_match(d, g)


def compute_iou(box1: list[float], box2: list[float]) -> float:
    x1 = max(box1[0], box2[0])
    y1 = max(box1[1], box2[1])
    x2 = min(box1[2], box2[2])
    y2 = min(box1[3], box2[3])
    inter = max(0, x2 - x1) * max(0, y2 - y1)
    area1 = (box1[2] - box1[0]) * (box1[3] - box1[1])
    area2 = (box2[2] - box2[0]) * (box2[3] - box2[1])
    union = area1 + area2 - inter
    return inter / union if union > 0 else 0.0


def clean_label(
    raw_label: str | None,
    *,
    from_caption: bool,
    strict_dictionary_filter: bool,
) -> str | None:
    """Normalize a raw Florence-2 label/caption phrase into a flashcard label, or None if rejected."""
    if not raw_label:
        return None
    label = caption_to_label(raw_label)
    if not label:
        return None
    if is_scene_background_word(label):
        return None
    if label in GENERIC_LABEL_BLACKLIST:
        return None
    if is_concrete_object_word(label) is False:
        return None
    head = label.split()[-1]
    # Labels extracted from free-form captions must pass the dictionary check even in
    # "soft" mode -- Florence-2 captions sometimes hallucinate proper nouns or typos.
    if from_caption and not is_valid_dictionary_word(head):
        return None
    if strict_dictionary_filter and not is_valid_dictionary_word(head):
        return None
    return label
