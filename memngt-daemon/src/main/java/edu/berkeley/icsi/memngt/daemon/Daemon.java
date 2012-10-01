package edu.berkeley.icsi.memngt.daemon;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.protocols.ClientToDaemonProtocol;
import edu.berkeley.icsi.memngt.protocols.DaemonToClientProtocol;
import edu.berkeley.icsi.memngt.protocols.NegotiationException;
import edu.berkeley.icsi.memngt.rpc.RPCService;

public final class Daemon implements ClientToDaemonProtocol {

	private static final int UPDATE_INTERVAL = 1000;

	private static final int MINIMUM_CLIENT_MEMORY = 512 * 1024;

	/**
	 * The fraction by which a client process may exceed its granted memory share before the memory negotiator daemon
	 * interferes.
	 */
	private static final float GRACE_MARGIN = 0.1f;

	private final RPCService rpcService;

	private final Map<Integer, ClientProcess> clientProcesses = new HashMap<Integer, ClientProcess>();

	private Daemon(final int rpcPort) throws IOException {

		this.rpcService = new RPCService(rpcPort);
		this.rpcService.setProtocolCallbackHandler(
			ClientToDaemonProtocol.class, this);

		Log.info("Started local memory negotiator daemon on port " + rpcPort);
	}

	private void reenforceGrantedMemoryShares() {

		while (true) {

			synchronized (this) {

				final Iterator<ClientProcess> it = this.clientProcesses.values().iterator();
				while (it.hasNext()) {

					final ClientProcess clientProcess = it.next();
					int physicalMemorySize = clientProcess.getPhysicalMemorySize();
					final int grantedMemoryShare = clientProcess.getGrantedMemoryShare();
					final int grantedMemoryShareWithGraceMargin = addGraceMargin(grantedMemoryShare);
					if (physicalMemorySize == -1) {
						Log.info("Cannot find client process " + clientProcess + ", removing it...");
						it.remove();
						continue;
					}

					int excessMemoryShare = physicalMemorySize - grantedMemoryShareWithGraceMargin;
					if (excessMemoryShare <= 0) {
						// Client process does exceed its granted share
						continue;
					}

					Log.info(clientProcess + " exceeds its granted memory share by " + excessMemoryShare
						+ " kilobytes, asking it to relinquish memory...");

					try {
						clientProcess.grantedMemoryShareChanged(grantedMemoryShare);
					} catch (IOException ioe) {
						Log.warn("I/O error while enforcing the memory share for " + clientProcess
							+ ", killing process...", ioe);
						kill(clientProcess);
						it.remove();
						continue;
					}

					physicalMemorySize = clientProcess.getPhysicalMemorySize();
					if (physicalMemorySize == -1) {
						Log.info("Cannot find client process " + clientProcess + ", removing it...");
						it.remove();
						continue;
					}

					excessMemoryShare = physicalMemorySize - grantedMemoryShare;
					if (excessMemoryShare > 0) {
						Log.info(clientProcess + " still exceeds its granted memory share by " + excessMemoryShare
							+ " kilobytes, killing it...");
						kill(clientProcess);
						it.remove();
					} else {
						Log.info(clientProcess + " reduced its physical memory consumption to " + physicalMemorySize);
					}
				}
			}

			try {
				Thread.sleep(UPDATE_INTERVAL);
			} catch (InterruptedException ie) {
				return;
			}
		}

	}

	private static void kill(final ClientProcess client) {

		try {
			client.kill();
		} catch (IOException ioe) {
			Log.error("Cannot kill " + client + ": ", ioe);
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

		daemon.reenforceGrantedMemoryShares();

		daemon.shutDown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized int registerClient(final String clientName, final int clientPID, final int clientRPCPort)
			throws NegotiationException {

		Log.debug("Client registration request from " + clientName + ", PID " + clientPID + ", RPC port "
			+ clientRPCPort);

		final Integer pid = Integer.valueOf(clientPID);

		// Check if we already now this process
		ClientProcess clientProcess = this.clientProcesses.get(pid);
		if (clientProcess != null) {
			Log.warn("Client with PID " + clientPID + " is already registered");
			return clientProcess.getGrantedMemoryShare();
		}

		// Verify we are actually talking to the right process
		if (!Utils.verifyPortBinding(clientPID, clientRPCPort)) {
			throw new NegotiationException("Could not verify binding between process ID " + clientPID
				+ " and RPC port " + clientRPCPort);
		}

		// Create RPC proxy for the client
		DaemonToClientProtocol rpcProxy = null;
		try {
			rpcProxy = this.rpcService.getProxy(new InetSocketAddress(clientRPCPort), DaemonToClientProtocol.class);
		} catch (IOException ioe) {
			final String errorMsg = "Unable to create RPC proxy for client " + clientPID;
			Log.error(errorMsg, ioe);
			throw new NegotiationException(errorMsg);
		}

		clientProcess = new ClientProcess(clientName, clientPID, rpcProxy, Math.min(MINIMUM_CLIENT_MEMORY,
			substraceGraceMargin(Utils.getFreePhysicalMemory())));

		this.clientProcesses.put(pid, clientProcess);

		Log.info("Successfully registered new client process " + clientProcess + " with "
			+ clientProcess.getGrantedMemoryShare() + " kilobytes of granted memory");

		return clientProcess.getGrantedMemoryShare();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized boolean requestAdditionalMemory(final int clientPID, final int amountOfMemory)
			throws IOException {

		Log.debug("Process with ID " + clientPID + " requests " + amountOfMemory + " kilobytes of additional memory");

		final Integer pid = Integer.valueOf(clientPID);
		final ClientProcess clientProcess = this.clientProcesses.get(pid);

		if (amountOfMemory < substraceGraceMargin(Utils.getFreePhysicalMemory())) {
			clientProcess.increaseGrantedMemoryShare(amountOfMemory);
			return true;
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public synchronized void relinquishMemory(final int clientPID, final int amountOfMemory) throws IOException {

	}

	private static int addGraceMargin(final int amountOfMemory) {

		return amountOfMemory + Math.round((float) amountOfMemory * GRACE_MARGIN);
	}

	private static int substraceGraceMargin(final int amountOfMemory) {

		return amountOfMemory - Math.round((float) amountOfMemory * GRACE_MARGIN);
	}
}
