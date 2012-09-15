package edu.berkeley.icsi.memngt.protocols;

import java.io.IOException;

import edu.berkeley.icsi.memngt.rpc.RPCProtocol;

public interface ClientToDaemonProtocol extends RPCProtocol {

	int registerClient(String clientName, int clientPID, int clientRPCPort) throws RegistrationException, IOException;
}
