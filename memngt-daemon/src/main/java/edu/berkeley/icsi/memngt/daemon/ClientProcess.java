package edu.berkeley.icsi.memngt.daemon;

import edu.berkeley.icsi.memngt.protocols.DaemonToClientProtocol;
import edu.berkeley.icsi.memngt.utils.ClientUtils;

final class ClientProcess implements DaemonToClientProtocol {

	private final String name;

	private final int pid;

	private final DaemonToClientProtocol rpcProxy;

	private final int guaranteedMemoryShare;

	private volatile int currentlyGrantedMemoryShare;

	ClientProcess(final String name, final int pid, final DaemonToClientProtocol rpcProxy,
			final int guaranteedMemoryShare) {
		this.name = name;
		this.pid = pid;
		this.rpcProxy = rpcProxy;
		this.guaranteedMemoryShare = guaranteedMemoryShare;
		this.currentlyGrantedMemoryShare = guaranteedMemoryShare;
	}

	String getName() {
		return this.name;
	}

	int getPID() {
		return this.pid;
	}

	int getPhysicalMemorySize() {
		return ClientUtils.getPhysicalMemorySize(this.pid);
	}

	int getGuaranteedMemoryShare() {

		return this.guaranteedMemoryShare;
	}

	int getCurrentlyGrantedMemoryShare() {

		return this.currentlyGrantedMemoryShare;
	}

}
