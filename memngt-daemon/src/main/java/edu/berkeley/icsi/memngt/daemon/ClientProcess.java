package edu.berkeley.icsi.memngt.daemon;

import java.io.IOException;

import edu.berkeley.icsi.memngt.protocols.DaemonToClientProtocol;
import edu.berkeley.icsi.memngt.protocols.ProcessType;
import edu.berkeley.icsi.memngt.utils.ClientUtils;

final class ClientProcess implements DaemonToClientProtocol, Comparable<ClientProcess> {

	private final String name;

	private final int pid;

	private final ProcessType type;

	private final int priority;

	private final DaemonToClientProtocol rpcProxy;

	private final int guaranteedMemoryShare;

	private int grantedMemoryShare;

	/**
	 * Stores if the process shall be offered free memory.
	 */
	private boolean offerFreeMemory;

	ClientProcess(final String name, final int pid, final ProcessType type, final DaemonToClientProtocol rpcProxy,
			final int guaranteedMemoryShare) {

		this.name = name;
		this.pid = pid;
		this.type = type;
		this.priority = 0;
		this.rpcProxy = rpcProxy;
		this.guaranteedMemoryShare = guaranteedMemoryShare;
		this.grantedMemoryShare = guaranteedMemoryShare;
		this.offerFreeMemory = (type == ProcessType.INFRASTRUCTURE_PROCESS);
	}

	String getName() {
		return this.name;
	}

	int getPID() {
		return this.pid;
	}

	ProcessType getType() {
		return this.type;
	}

	int getPriority() {
		return this.priority;
	}

	int getGuaranteedMemoryShare() {
		return this.guaranteedMemoryShare;
	}

	int getGrantedMemoryShare() {
		return this.grantedMemoryShare;
	}

	void increaseGrantedMemoryShare(final int amountOfMemory) {

		if (amountOfMemory < 0) {
			throw new IllegalStateException("amountOfAdditionalMemory not be non-negative");
		}

		this.grantedMemoryShare += amountOfMemory;
	}

	void decreaseGrantedMemoryShare(final int amountOfMemory) {

		if (amountOfMemory < 0) {
			throw new IllegalStateException("amountOfAdditionalMemory not be non-negative");
		}

		this.grantedMemoryShare -= amountOfMemory;
		if (this.grantedMemoryShare < this.guaranteedMemoryShare) {
			this.grantedMemoryShare = this.guaranteedMemoryShare;
		}
	}

	int getPhysicalMemorySize() {
		return ClientUtils.getPhysicalMemorySize(this.pid);
	}

	/**
	 * Sets if the process shall be offered free memory.
	 * 
	 * @param offerFreeMemory
	 *        <code>true</code> to indicate the process shall be offered free memory, <code>false</code> otherwise
	 */
	void setOfferFreeMemory(final boolean offerFreeMemory) {

		if (this.type == ProcessType.USER_PROCESS) {
			throw new IllegalStateException("setOfferFreeMemory accessed for user process");
		}

		this.offerFreeMemory = offerFreeMemory;
	}

	/**
	 * Returns if the process shall be offered free memory.
	 * 
	 * @return <code>true</code> if the process shall be offered free memory, <code>false</code> otherwise
	 */
	boolean getOfferFreeMemory() {

		if (this.type == ProcessType.USER_PROCESS) {
			throw new IllegalStateException("getOfferFreeMemory accessed for user process");
		}

		return this.offerFreeMemory;
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

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int additionalMemoryOffered(final int amountOfAdditionalMemory) throws IOException {

		if (this.type == ProcessType.USER_PROCESS) {
			throw new IllegalStateException("Additional memory offered to user process");
		}

		return this.rpcProxy.additionalMemoryOffered(amountOfAdditionalMemory);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int compareTo(final ClientProcess clientProcess) {

		return (this.priority - clientProcess.priority);
	}

}
