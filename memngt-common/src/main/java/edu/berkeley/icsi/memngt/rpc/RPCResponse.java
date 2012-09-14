package edu.berkeley.icsi.memngt.rpc;

final class RPCResponse extends RPCMessage {

	private final Object retVal;

	RPCResponse(final int requestID, final Object retVal) {
		super(requestID);

		this.retVal = retVal;
	}

	RPCResponse() {
		super(0);

		this.retVal = null;
	}

	Object getRetVal() {
		return this.retVal;
	}
}
