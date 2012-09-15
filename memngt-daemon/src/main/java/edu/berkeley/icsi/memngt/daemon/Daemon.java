package edu.berkeley.icsi.memngt.daemon;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;

import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.protocols.ClientToDaemonProtocol;
import edu.berkeley.icsi.memngt.protocols.DaemonToClientProtocol;
import edu.berkeley.icsi.memngt.protocols.RegistrationException;
import edu.berkeley.icsi.memngt.rpc.RPCService;

public final class Daemon implements ClientToDaemonProtocol {

	private final static int UPDATE_INTERVAL = 1000;

	private final static int MINIMUM_CLIENT_MEMORY = 128 * 1024;

	private final RPCService rpcService;

	private final ConcurrentHashMap<Integer, ClientProcess> clientProcesses = new ConcurrentHashMap<Integer, ClientProcess>();

	private Daemon(final int rpcPort) throws IOException {

		this.rpcService = new RPCService(rpcPort);
		this.rpcService.setProtocolCallbackHandler(
			ClientToDaemonProtocol.class, this);
	}

	private void runMainLoop() {

		while (true) {

			final Iterator<ClientProcess> it = this.clientProcesses.values().iterator();
			while (it.hasNext()) {

				final ClientProcess clientProcess = it.next();
				final int physicalMemorySize = clientProcess.getPhysicalMemorySize();
				if (physicalMemorySize == -1) {
					it.remove();
					continue;
				}
				final int excessMemoryShare = physicalMemorySize - clientProcess.getCurrentlyGrantedMemoryShare();
				if (excessMemoryShare < 0) {
					// Client process does exceed its granted share
					continue;
				}

				System.out.println("Physical memory size is " + physicalMemorySize);
			}

			try {
				Thread.sleep(UPDATE_INTERVAL);
			} catch (InterruptedException ie) {
				return;
			}
		}

	}

	private void shutDown() {

		this.rpcService.shutDown();
	}

	public static void main(final String[] args) {

		// Do some initial sanity checks
		if (Utils.getFreePhysicalMemory() == -1) {
			Log.error("Cannot determine the amount of free physical memory");
			return;
		}

		Daemon daemon = null;
		try {
			daemon = new Daemon(8000);
		} catch (IOException ioe) {
			Log.error("Could not initialize memory negotiator daemon: ", ioe);
			return;
		}

		daemon.runMainLoop();

		daemon.shutDown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int registerClient(final String clientName, final int clientPID, final int clientRPCPort)
			throws RegistrationException {

		Log.debug("Client registration request from " + clientName + ", PID " + clientPID + ", RPC port "
			+ clientRPCPort);

		final Integer pid = Integer.valueOf(clientPID);

		// Check if we already now this process
		ClientProcess clientProcess = this.clientProcesses.get(pid);
		if (clientProcess != null) {
			Log.warn("Client with PID " + clientPID + " is already registered");
			return clientProcess.getGuaranteedMemoryShare();
		}

		// Verify we are actually talking to the right process
		if (!Utils.verifyPortBinding(clientPID, clientRPCPort)) {
			throw new RegistrationException("Could not verify binding between process ID " + clientPID
				+ " and RPC port " + clientRPCPort);
		}

		// Create RPC proxy for the client
		DaemonToClientProtocol rpcProxy = null;
		try {
			rpcProxy = this.rpcService.getProxy(new InetSocketAddress(clientRPCPort), DaemonToClientProtocol.class);
		} catch (IOException ioe) {
			final String errorMsg = "Unable to create RPC proxy for client " + clientPID;
			Log.error(errorMsg, ioe);
			throw new RegistrationException(errorMsg);
		}

		clientProcess = new ClientProcess(clientName, clientPID, rpcProxy, Math.min(MINIMUM_CLIENT_MEMORY,
			Utils.getFreePhysicalMemory()));

		final ClientProcess oldVal = this.clientProcesses.putIfAbsent(pid, clientProcess);
		if (oldVal != null) {
			return oldVal.getGuaranteedMemoryShare();
		}

		return clientProcess.getGuaranteedMemoryShare();
	}
}
