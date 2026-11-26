package vn.ptit.snapvocab.mnemonic.dto;

/** A word the learner keeps forgetting ("Từ hay quên"); {@code mnemonic} is null until one is made. */
public record LeechWordDTO(Long topicItemId, String word, String meaning, int forgetCount, MnemonicDTO mnemonic) {
}
