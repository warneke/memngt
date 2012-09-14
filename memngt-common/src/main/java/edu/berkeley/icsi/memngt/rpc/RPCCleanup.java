package edu.berkeley.icsi.memngt.rpc;

final class RPCCleanup extends RPCMessage {

	RPCCleanup(final int requestID) {
		super(requestID);
	}

	RPCCleanup() {
		super(0);
	}
}
