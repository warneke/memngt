package edu.berkeley.icsi.memngt.rpc;

/**
 * This message is used to transport the return value of a remote procedure call back to the caller.
 * <p>
 * This message is thread-safe.
 * 
 * @author warneke
 */
final class RPCReturnValue extends RPCResponse {

	/**
	 * The return value of the remote procedure call.
	 */
	private final Object retVal;

	/**
	 * Constructs a new RPC return value message.
	 * 
	 * @param messageID
	 *        the message ID
	 * @param retVal
	 *        the return value of the remote procedure call
	 */
	RPCReturnValue(final int messageID, final Object retVal) {
		super(messageID);

		this.retVal = retVal;
	}

	/**
	 * The default constructor required by kryo.
	 */
	private RPCReturnValue() {
		super(0);

		this.retVal = null;
	}

	/**
	 * Returns the return value of the remote procedure call.
	 * 
	 * @return the return value of the remote procedure call
	 */
	Object getRetVal() {
		return this.retVal;
	}
}
