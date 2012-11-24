package edu.berkeley.icsi.memngt.protocols;

import java.io.IOException;

import edu.berkeley.icsi.memngt.rpc.RPCProtocol;

public interface DaemonToClientProtocol extends RPCProtocol {

	void grantedMemoryShareChanged(int sizeOfNewGrantedShare) throws IOException;

	/**
	 * Called by the negotiator daemon to offer additional main memory to the client process.
	 * 
	 * @param amountOfAdditionalMemory
	 *        the amount of additional memory offered in kilobytes
	 * @return the additional amount of main memory in kilobytes the client process actually accepts. Negative return
	 *         values indicate that the client process is not interested in further memory offers.
	 * @throws IOException
	 *         thrown if an I/O error occurred during the RPC call
	 */
	int additionalMemoryOffered(int amountOfAdditionalMemory) throws IOException;
}
