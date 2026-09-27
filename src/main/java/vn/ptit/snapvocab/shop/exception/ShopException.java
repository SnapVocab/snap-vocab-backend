package vn.ptit.snapvocab.shop.exception;

import vn.ptit.snapvocab.shop.domain.enumeration.ShopErrorCode;

public class ShopException extends RuntimeException {

	private final ShopErrorCode code;
	private final transient Object details;

	public ShopException(ShopErrorCode code, String message) {
		this(code, message, null);
	}

	public ShopException(ShopErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ShopErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
