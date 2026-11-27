package vn.ptit.snapvocab.mnemonic.domain;

/** How the AI ties the English word to something a Vietnamese learner already knows. */
public enum MnemonicType {
	/** The English sound resembles Vietnamese words ("ambulance" → "ăm bu lăng xê"). */
	SOUND_ALIKE,
	/** A pun or a split of the word into known parts. */
	WORDPLAY,
	/** A short, vivid scene that links the word to its meaning. */
	STORY
}
