package edu.berkeley.icsi.memngt.rpc;

/**
 * Abstract base class for all types of response messages used by this RPC service.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke
 */
abstract class RPCResponse extends RPCMessage {

	/**
	 * Constructs a new RPC response.
	 * 
	 * @param messageID
	 *        the message ID
	 */
	protected RPCResponse(final int messageID) {
		super(messageID);
	}
}
