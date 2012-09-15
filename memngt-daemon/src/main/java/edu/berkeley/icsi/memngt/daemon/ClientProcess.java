package edu.berkeley.icsi.memngt.daemon;

import java.io.IOException;

import edu.berkeley.icsi.memngt.protocols.DaemonToClientProtocol;
import edu.berkeley.icsi.memngt.utils.ClientUtils;

final class ClientProcess implements DaemonToClientProtocol {

	private final String name;

	private final int pid;

	private final DaemonToClientProtocol rpcProxy;

	private final int guaranteedMemoryShare;

	private int grantedMemoryShare;

	ClientProcess(final String name, final int pid, final DaemonToClientProtocol rpcProxy,
			final int guaranteedMemoryShare) {
		this.name = name;
		this.pid = pid;
		this.rpcProxy = rpcProxy;
		this.guaranteedMemoryShare = guaranteedMemoryShare;
		this.grantedMemoryShare = guaranteedMemoryShare;
	}

	String getName() {
		return this.name;
	}

	int getPID() {
		return this.pid;
	}

	int getGrantedMemoryShare() {
		return this.grantedMemoryShare;
	}

	int getPhysicalMemorySize() {
		return ClientUtils.getPhysicalMemorySize(this.pid);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return this.name + " (" + this.pid + ")";
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void grantedMemoryShareChanged(final int sizeOfNewShare) throws IOException {
		this.rpcProxy.grantedMemoryShareChanged(sizeOfNewShare);
	}

	void kill() throws IOException {
		Runtime.getRuntime().exec("kill -9 " + this.pid);
	}

}
