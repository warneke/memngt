package edu.berkeley.icsi.memngt.rpc;

/**
 * An RPC envelope is the basic data structure which wraps all other types of messages for the RPC communication. Its
 * basic purpose is to provide a common entry point for serialization and to separate most of the serialization code
 * from the actual RPC logic.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke
 */
class RPCEnvelope {

	/**
	 * The actual RPC message to be transported.
	 */
	private final RPCMessage rpcMessage;

	/**
	 * Constructs a new RPC envelope and wraps the given message.
	 * 
	 * @param rpcMessage
	 *        the message to be wrapped
	 */
	RPCEnvelope(final RPCMessage rpcMessage) {

		this.rpcMessage = rpcMessage;
	}

	/**
	 * The default constructor required by kryo.
	 */
	@SuppressWarnings("unused")
	private RPCEnvelope() {
		this.rpcMessage = null;
	}

	/**
	 * Returns the wrapped RPC message.
	 * 
	 * @return the wrapped RPC message
	 */
	RPCMessage getRPCMessage() {
		return this.rpcMessage;
	}
}
