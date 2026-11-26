package vn.ptit.snapvocab.mnemonic.exception;

/** The app receives {@code code} as the error and {@code details} (if any) as the response data. */
public class MnemonicException extends RuntimeException {

	private final MnemonicErrorCode code;
	private final transient Object details;

	public MnemonicException(MnemonicErrorCode code, String message) {
		this(code, message, null, null);
	}

	public MnemonicException(MnemonicErrorCode code, String message, Object details, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.details = details;
	}

	public MnemonicErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
