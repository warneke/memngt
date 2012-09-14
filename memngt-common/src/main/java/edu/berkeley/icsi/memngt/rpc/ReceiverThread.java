package edu.berkeley.icsi.memngt.rpc;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.minlog.Log;

final class ReceiverThread extends Thread {

	private final RPCService rpcService;

	private final DatagramSocket socket;

	private volatile boolean shutdownRequested = false;

	ReceiverThread(final RPCService rpcService, final DatagramSocket socket) {
		this.rpcService = rpcService;
		this.socket = socket;
	}

	@Override
	public void run() {

		final Kryo kryo = RPCService.createKryoObject();
		final byte[] buf = new byte[RPCMessage.MAXIMUM_MSG_SIZE];
		final DatagramPacket dp = new DatagramPacket(buf, buf.length);

		while (!this.shutdownRequested) {

			try {
				this.socket.receive(dp);
			} catch (IOException ioe) {
				Log.error("Error receiving UDP message: ", ioe);
				continue;
			}

			final InetSocketAddress remoteSocketAddress = (InetSocketAddress) dp.getSocketAddress();
			final MemoryBackedInputStream mbis = new MemoryBackedInputStream(dp.getData(), dp.getOffset(),
				dp.getLength());
			final Input input = new Input(mbis);
			final RPCEnvelope envelope = kryo.readObject(input, RPCEnvelope.class);
			final RPCMessage msg = envelope.getRPCMessage();

			if (msg instanceof RPCRequest) {

				while (true) {

					try {
						this.rpcService.processIncomingRPCRequest(remoteSocketAddress, (RPCRequest) msg);
						break;
					} catch (InterruptedException e) {
						if (this.shutdownRequested) {
							return;
						} else {
							continue;
						}
					}
				}
			} else if (msg instanceof RPCResponse) {
				this.rpcService.processIncomingRPCResponse(remoteSocketAddress, (RPCResponse) msg);
			} else {
				this.rpcService.processIncomingRPCCleanup(remoteSocketAddress, (RPCCleanup) msg);
			}
		}

	}

	void shutDown() throws InterruptedException {

		this.shutdownRequested = true;
		interrupt();

		join();
	}
}
