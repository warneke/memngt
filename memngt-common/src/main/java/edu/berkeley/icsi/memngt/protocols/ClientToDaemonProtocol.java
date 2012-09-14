package edu.berkeley.icsi.memngt.protocols;

import edu.berkeley.icsi.memngt.rpc.RPCProtocol;

public interface ClientToDaemonProtocol extends RPCProtocol {

	void registerClient(String clientName, int clientPID, int clientRPCPort);
}
