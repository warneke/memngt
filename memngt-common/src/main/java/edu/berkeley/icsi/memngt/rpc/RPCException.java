package edu.berkeley.icsi.memngt.rpc;

final class RPCException extends RPCResponse {

	private final Throwable exception;

	RPCException(final int requestID, final Throwable exception) {
		super(requestID);

		this.exception = exception;
	}

	RPCException() {
		super(0);

		this.exception = null;
	}

	Throwable getException() {
		return this.exception;
	}
}
