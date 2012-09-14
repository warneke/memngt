package edu.berkeley.icsi.memngt.rpc;

import java.lang.reflect.Method;

import com.esotericsoftware.kryo.Kryo;

abstract class RPCMessage {

	public static final int MAXIMUM_MSG_SIZE = 2048;

	protected static final ThreadLocal<Kryo> KRYO = new ThreadLocal<Kryo>() {

		@Override
		protected Kryo initialValue() {
			
			final Kryo kryo = new Kryo();
			
			kryo.register(RPCMessage.class);
			kryo.register(RPCRequest.class);
			kryo.register(Method.class);
			
			return kryo;
		}
	};

	protected final int requestID;

	protected RPCMessage(final int requestID) {
		this.requestID = requestID;
	}

	int getRequestID() {
		return this.requestID;
	}
}
