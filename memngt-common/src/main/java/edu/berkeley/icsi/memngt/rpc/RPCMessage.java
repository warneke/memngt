package edu.berkeley.icsi.memngt.rpc;

import java.net.DatagramPacket;

/**
 * Abstract base class for all types of communication messages used by this RPC service.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke
 */
abstract class RPCMessage {

	/**
	 * The largest amount of data to be put in a single {@link DatagramPacket}.
	 */
	public static final int MAXIMUM_MSG_SIZE = 1016;

	/**
	 * The amount of data reserved for meta data in each {@link DatagramPacket}.
	 */
	public static final int METADATA_SIZE = 8;

	/**
	 * The message ID.
	 */
	private final int messageID;

	/**
	 * Constructs a new RPC message.
	 * 
	 * @param messageID
	 *        the message ID
	 */
	protected RPCMessage(final int messageID) {
		this.messageID = messageID;
	}

	/**
	 * Returns the message ID.
	 * 
	 * @return the message ID
	 */
	final int getMessageID() {
		return this.messageID;
	}
}
