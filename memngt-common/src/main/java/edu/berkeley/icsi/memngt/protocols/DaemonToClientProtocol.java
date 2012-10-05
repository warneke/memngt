package edu.berkeley.icsi.memngt.protocols;

import java.io.IOException;

import edu.berkeley.icsi.memngt.rpc.RPCProtocol;

public interface DaemonToClientProtocol extends RPCProtocol {

	void grantedMemoryShareChanged(int sizeOfNewGrantedShare) throws IOException;

	int additionalMemoryOffered(int amountOfAdditionalMemory) throws IOException;
}
