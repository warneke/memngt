package edu.berkeley.icsi.memngt.protocols;

import java.io.IOException;

import edu.berkeley.icsi.memngt.rpc.RPCProtocol;

public interface ClientToDaemonProtocol extends RPCProtocol {

	/**
	 * Registers a client process with the memory negotiator daemon.
	 * 
	 * @param clientName
	 *        the name chosen by the client process to identify itself
	 * @param clientPID
	 *        the process ID of the client
	 * @param clientRPCPort
	 *        the RPC port the memory negotiator daemon can use to communicate with the client process
	 * @return the initially granted memory share in kilobytes
	 * @throws NegotiationException
	 *         thrown if the daemon was unable to successfully complete the registration
	 * @throws IOException
	 *         thrown if an I/O error occurred during the RPC call
	 */
	int registerClient(String clientName, int clientPID, int clientRPCPort) throws NegotiationException, IOException;

	/**
	 * Requests additional main memory for the client process with the given ID.
	 * 
	 * @param clientPID
	 *        the process ID of the client requesting the memory
	 * @param amountOfMemory
	 *        the requested amount of additional memory in kilobytes
	 * @return <code>true</code> if the memory negotiator daemon granted the request, <code>false</code> otherwise
	 * @throws NegotiationException
	 *         thrown if the daemon could not process the request for additional memory
	 * @throws IOException
	 *         thrown if an I/O error occurred during the RPC call
	 */
	boolean requestAdditionalMemory(int clientPID, int amountOfMemory) throws NegotiationException, IOException;

	/**
	 * Relinquishes the given amount of main memory
	 * 
	 * @param clientPID
	 *        the process ID of the client relinquishing the memory
	 * @param amountOfMemory
	 *        the amount of relinquished memory in kilobytes
	 * @throws NegotiationException
	 *         thrown if the daemon could not process the client's relinquishment notification
	 * @throws IOException
	 *         thrown if an I/O error occurred during the RPC call
	 */
	void relinquishMemory(int clientPID, int amountOfMemory) throws IOException;
}
