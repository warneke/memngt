package edu.berkeley.icsi.memngt.rpc;

import java.lang.reflect.Method;

/**
 * This class implements a request message for the RPC service.
 * <p>
 * This class is thread-safe.
 * 
 * @author warneke
 */
final class RPCRequest extends RPCMessage {

	/**
	 * The name of the protocol this message is designed for.
	 */
	private final String protocolName;

	/**
	 * The method to be called as part of this RPC request.
	 */
	private final String methodName;

	/**
	 * The parameter types of the method.
	 */
	private final Class<?>[] parameterTypes;

	/**
	 * The arguments for the remote procedure call.
	 */
	private final Object[] args;

	/**
	 * Constructs a new RPC request message.
	 * 
	 * @param messageID
	 *        the message ID
	 * @param interfaceName
	 *        the name of the protocol this message is designed for
	 * @param method
	 *        the method to be called
	 * @param args
	 *        the arguments for the remote procedure call
	 */
	RPCRequest(final int messageID, final String interfaceName, final Method method, final Object[] args) {
		super(messageID);

		this.protocolName = interfaceName;
		this.methodName = method.getName();
		this.parameterTypes = method.getParameterTypes();
		this.args = args;
	}

	/**
	 * The default constructor required by kryo.
	 */
	private RPCRequest() {
		super(0);

		this.protocolName = null;
		this.methodName = null;
		this.parameterTypes = null;
		this.args = null;
	}

	/**
	 * Returns the name of the protocol this message is designed for.
	 * 
	 * @return the name of the protocol this message is designed for
	 */
	String getInterfaceName() {

		return this.protocolName;
	}

	/**
	 * Returns the name of the method to be called.
	 * 
	 * @return the name of the method to be called
	 */
	String getMethodName() {

		return this.methodName;
	}

	/**
	 * Returns the parameter types of the method.
	 * 
	 * @return the parameter types of the method
	 */
	Class<?>[] getParameterTypes() {

		return this.parameterTypes;
	}

	/**
	 * Returns the arguments for the remote procedure call.
	 * 
	 * @return the arguments for the remote procedure call
	 */
	Object[] getArgs() {

		return this.args;
	}
}
