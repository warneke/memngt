package edu.berkeley.icsi.memngt.rpc;

import java.util.ArrayList;
import java.util.List;

import edu.berkeley.icsi.memngt.protocols.NegotiationException;
import edu.berkeley.icsi.memngt.protocols.ProcessType;

public class CommonTypeUtils {

	/**
	 * Private constructor to prevent instantiation.
	 */
	private CommonTypeUtils() {
	}
	
	/**
	 * Returns a list of types frequently used by the RPC protocols of this package and its parent packages.
	 * 
	 * @return a list of types frequently used by the RPC protocols of this package
	 */
	public static List<Class<?>> getRPCTypesToRegister() {

		final ArrayList<Class<?>> types = new ArrayList<Class<?>>();

		types.add(ProcessType.class);
		types.add(NegotiationException.class);

		return types;
	}
}
