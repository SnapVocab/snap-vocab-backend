package vn.ptit.snapvocab.mnemonic.service;

/** What a card says about its word, read through the template's semantic roles; any part may be null. */
public record WordFacts(String word, String phonetic, String meaning, String definition, String example) {

	public boolean hasWord() {
		return word != null && !word.isBlank();
	}
}
