package edu.berkeley.icsi.memngt.rpc;

abstract class RPCResponse extends RPCMessage {

	protected RPCResponse(final int requestID) {
		super(requestID);
	}
}
