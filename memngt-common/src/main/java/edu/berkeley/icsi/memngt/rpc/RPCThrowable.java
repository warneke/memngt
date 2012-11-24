package edu.berkeley.icsi.memngt.rpc;

/**
 * This message is used to transport an exception of a remote procedure call back to the caller.
 * <p>
 * This message is thread-safe.
 * 
 * @author warneke
 */
final class RPCThrowable extends RPCResponse {

	/**
	 * The exception to be transported.
	 */
	private final Throwable throwable;

	/**
	 * Constructs a new RPC exception message.
	 * 
	 * @param messageID
	 *        the message ID
	 * @param throwable
	 *        the exception to be transported
	 */
	RPCThrowable(final int messageID, final Throwable throwable) {
		super(messageID);

		this.throwable = throwable;
	}

	/**
	 * The default constructor required by kryo.
	 */
	private RPCThrowable() {
		super(0);

		this.throwable = null;
	}

	/**
	 * Returns the transported exception.
	 * 
	 * @return the transported exception
	 */
	Throwable getThrowable() {
		return this.throwable;
	}
}
