package edu.berkeley.icsi.memngt.rpc;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.protocols.Protocol;

public final class RPCService {

	private static final int TIMEOUT = 100;

	private static final int CLEANUP_INTERVAL = 10000;

	private final SenderThread senderThread;

	private final ReceiverThread receiverThread;

	private final DatagramSocket socket;

	private final AtomicBoolean shutdownRequested = new AtomicBoolean(false);

	private final Timer cleanupTimer = new Timer();

	private final ConcurrentHashMap<String, Protocol> callbackHandlers = new ConcurrentHashMap<String, Protocol>();

	private final ConcurrentHashMap<Integer, RPCRequest> pendingRequests = new ConcurrentHashMap<Integer, RPCRequest>();

	private final ConcurrentHashMap<Integer, RPCResponse> pendingResponses = new ConcurrentHashMap<Integer, RPCResponse>();

	private final ConcurrentHashMap<Integer, CachedResponse> cachedResponses = new ConcurrentHashMap<Integer, CachedResponse>();

	private static final class CachedResponse {

		private final long creationTime;

		private final RPCResponse rpcResponse;

		private CachedResponse(final long creationTime, final RPCResponse rpcResponse) {
			this.creationTime = creationTime;
			this.rpcResponse = rpcResponse;
		}
	}

	private final class RPCInvocationHandler implements InvocationHandler {

		private final InetSocketAddress remoteSocketAddress;

		private final String interfaceName;

		private RPCInvocationHandler(final InetSocketAddress remoteSocketAddress, final String interfaceName) {
			this.remoteSocketAddress = remoteSocketAddress;
			this.interfaceName = interfaceName;
		}

		@Override
		public Object invoke(final Object proxy, final Method method, final Object[] args) throws Throwable {

			final int requestID = (int) (Math.random() * (double) Integer.MAX_VALUE);
			final RPCRequest rpcRequest = new RPCRequest(requestID, this.interfaceName, method, args);

			return sendRPCRequest(this.remoteSocketAddress, rpcRequest);
		}

	}

	private final class CleanupTask extends TimerTask {

		@Override
		public void run() {

			final long now = System.currentTimeMillis();
			final Iterator<Map.Entry<Integer, CachedResponse>> it = cachedResponses.entrySet().iterator();
			while (it.hasNext()) {

				final Map.Entry<Integer, CachedResponse> entry = it.next();
				final CachedResponse cachedResponse = entry.getValue();
				if (cachedResponse.creationTime + CLEANUP_INTERVAL < now) {
					it.remove();
				}
			}

			System.out.println("Timer");

		}
	}

	static Kryo createKryoObject() {

		final Kryo kryo = new Kryo();
		kryo.register(RPCMessage.class);
		kryo.register(RPCRequest.class);
		
		return kryo;
	}
	
	public RPCService(final int port) throws IOException {

		this.socket = new DatagramSocket(port);

		this.senderThread = new SenderThread(this.socket);
		this.senderThread.start();

		this.receiverThread = new ReceiverThread(this, this.socket);
		this.receiverThread.start();

		this.cleanupTimer.schedule(new CleanupTask(), CLEANUP_INTERVAL, CLEANUP_INTERVAL);

	}

	public void setProtocolCallbackHandler(final Class<? extends Protocol> protocol, final Protocol callbackHandler) {

		if (this.callbackHandlers.putIfAbsent(protocol.getName(), callbackHandler) != null) {
			Log.error("There is already a protocol call back handler set for protocol " + protocol.getName());
		}

	}

	@SuppressWarnings("unchecked")
	public <T extends Protocol> T getProxy(final InetSocketAddress remoteAddress, Class<T> protocol)
			throws IOException {

		final Class<?>[] interfaces = new Class<?>[1];
		interfaces[0] = protocol;
		return (T) java.lang.reflect.Proxy.newProxyInstance(RPCService.class.getClassLoader(), interfaces,
			new RPCInvocationHandler(remoteAddress, protocol.getName()));
	}

	Object sendRPCRequest(final InetSocketAddress remoteSocketAddress, final RPCRequest request) throws IOException,
			InterruptedException {

		if (this.shutdownRequested.get()) {
			throw new IOException("Shutdown of RPC service has already been requested");
		}

		final Integer requestID = Integer.valueOf(request.getRequestID());

		this.pendingRequests.put(requestID, request);

		while (true) {

			this.senderThread.sendMessage(remoteSocketAddress, request);

			synchronized (request) {
				request.wait(TIMEOUT);
			}

			// Check if response has arrived
			final RPCResponse rpcResponse = this.pendingResponses.remove(requestID);
			if (rpcResponse == null) {
				// Resend message
				continue;
			}

			// Request is no longer pending
			this.pendingRequests.remove(requestID);

			// Send clean up message
			this.senderThread.sendMessage(remoteSocketAddress, new RPCCleanup(request.getRequestID()));

			return rpcResponse.getRetVal();
		}
	}

	public void shutDown() {

		if (!this.shutdownRequested.compareAndSet(false, true)) {
			return;
		}

		try {
			this.senderThread.shutDown();
		} catch (InterruptedException ie) {
			Log.debug("Caught exception while waiting for sender thread to shut down: ", ie);
		}

		try {
			this.receiverThread.shutDown();
		} catch (InterruptedException ie) {
			Log.debug("Caught exception while waiting for receiver thread to shut down: ", ie);
		}

		this.cleanupTimer.cancel();

		this.socket.close();
	}

	void processIncomingRPCRequest(final InetSocketAddress remoteSocketAddress, final RPCRequest rpcRequest)
			throws InterruptedException {

		final Integer requestID = Integer.valueOf(rpcRequest.getRequestID());
		final CachedResponse cachedResponse = this.cachedResponses.get(requestID);
		if (cachedResponse != null) {
			this.senderThread.sendMessage(remoteSocketAddress, cachedResponse.rpcResponse);
			return;
		}

		final Protocol callbackHandler = this.callbackHandlers.get(rpcRequest.getInterfaceName());
		if (callbackHandler == null) {
			Log.error("Cannot find callback handler for protocol " + rpcRequest.getInterfaceName());
			return;
		}

		Method method = null;
		try {
			method = callbackHandler.getClass().getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
		} catch (Exception e) {
			e.printStackTrace();
			Log.error("Error while processing incoming RPC request: ", e);
			return;
		}

		Object retVal = null;
		try {
			retVal = method.invoke(callbackHandler, rpcRequest.getArgs());
		} catch (Exception e) {
			e.printStackTrace();
			Log.error("Error while processing incoming RPC request: ", e);
			return;
		}

		final RPCResponse rpcResponse = new RPCResponse(rpcRequest.getRequestID(), retVal);
		this.cachedResponses.put(requestID, new CachedResponse(System.currentTimeMillis(), rpcResponse));
		this.senderThread.sendMessage(remoteSocketAddress, rpcResponse);
	}

	void processIncomingRPCResponse(final InetSocketAddress remoteSocketAddress, final RPCResponse rpcResponse) {

		final Integer requestID = Integer.valueOf(rpcResponse.getRequestID());

		final RPCRequest request = this.pendingRequests.get(requestID);
		if (request == null) {
			return;
		}

		this.pendingResponses.put(requestID, rpcResponse);

		synchronized (request) {
			request.notify();
		}
	}

	void processIncomingRPCCleanup(final InetSocketAddress remoteSocketAddress, final RPCCleanup rpcCleanup) {

		this.cachedResponses.remove(Integer.valueOf(rpcCleanup.getRequestID()));
	}
}
