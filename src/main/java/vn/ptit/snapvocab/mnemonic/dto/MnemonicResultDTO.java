package vn.ptit.snapvocab.mnemonic.dto;

/** A freshly generated mnemonic and how many generations the learner has left today. */
public record MnemonicResultDTO(MnemonicDTO mnemonic, int dailyLimit, int remainingToday) {
}
