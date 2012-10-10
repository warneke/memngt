package edu.berkeley.icsi.memngt.pools;

import java.util.ArrayDeque;

import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.utils.ClientUtils;

public abstract class AbstractMemoryPool<T> {

	private final int ADAPTATION_GRANULARITY = 10 * 1024;

	private final String name;

	private final int pid;

	private final ArrayDeque<T> buffers;

	/**
	 * The total amount of memory allocated by this pool in kilobytes.
	 */
	private int allocatedMemory = 0;

	/**
	 * The granted memory size of the application containing the memory pool in kilobytes.
	 */
	private int grantedMemorySize = -1;

	protected AbstractMemoryPool() {
		final int pid = ClientUtils.getPID();
		this.name = getDefaultName(pid);
		this.pid = pid;
		this.buffers = new ArrayDeque<T>();
	}

	protected AbstractMemoryPool(final String name) {
		this.name = name;
		this.pid = ClientUtils.getPID();
		this.buffers = new ArrayDeque<T>();
	}

	protected AbstractMemoryPool(final String name, final int initialCapacity) {
		this.name = name;
		this.pid = ClientUtils.getPID();
		this.buffers = new ArrayDeque<T>(initialCapacity);
	}

	protected AbstractMemoryPool(final int initialCapacity) {
		final int pid = ClientUtils.getPID();
		this.name = getDefaultName(pid);
		this.pid = pid;
		this.buffers = new ArrayDeque<T>(initialCapacity);
	}

	/**
	 * Returns the default name for this memory pool.
	 * 
	 * @param pid
	 *        the ID of the process containing this memory pool
	 * @return the default name for this memory pool
	 */
	private static String getDefaultName(final int pid) {
		return "MemoryPool " + pid;
	}

	/**
	 * Sets the granted memory size.
	 * 
	 * @param grantedMemorySize
	 *        the granted memory size in kilobytes
	 */
	public void setGrantedMemorySize(final int grantedMemorySize) {

		if (grantedMemorySize <= 0) {
			throw new IllegalArgumentException("grantedMemorySize must be larger than 0");
		}

		this.grantedMemorySize = grantedMemorySize;
	}

	/**
	 * The granted memory size in kilobytes.
	 * 
	 * @return the granted memory size in kilobytes
	 */
	public int getGrantedMemorySize() {

		return this.grantedMemorySize;
	}

	/**
	 * Clears the memory pool and resets all internal variables.
	 */
	public void clear() {
		this.buffers.clear();
		this.allocatedMemory = 0;
		this.grantedMemorySize = -1;
	}

	/**
	 * Returns the number of available buffers in the memory pool.
	 * 
	 * @return the number of available buffers in the memory pool
	 */
	public int size() {
		return this.buffers.size();
	}

	/**
	 * Adjusts the memory pool to the granted memory size.
	 */
	public void adjust() {

		if (this.grantedMemorySize < 0) {
			throw new IllegalStateException("grantedMemorySize is not set");
		}

		final int oldAllocatedMemory = this.allocatedMemory;

		int allocatedBuffers = 0;
		int kilobytesUntilNextCheck = 0;
		while (true) {

			if (kilobytesUntilNextCheck <= 0) {
				if (ClientUtils.getPhysicalMemorySize(this.pid) > this.grantedMemorySize) {
					break;
				}
				kilobytesUntilNextCheck = ADAPTATION_GRANULARITY;
			}

			final T buffer = allocatedNewBuffer();
			final int sizeOfBuffer = getSizeOfBuffer(buffer);
			this.buffers.add(buffer);
			this.allocatedMemory += sizeOfBuffer;
			kilobytesUntilNextCheck -= sizeOfBuffer;
			++allocatedBuffers;
		}

		int releasedBuffers = 0;
		int excessMemory = ClientUtils.getPhysicalMemorySize(this.pid) - this.grantedMemorySize;
		kilobytesUntilNextCheck = 0;
		while (true) {

			if (kilobytesUntilNextCheck <= 0) {
				if (ClientUtils.getPhysicalMemorySize(this.pid) < this.grantedMemorySize) {
					break;
				}
				kilobytesUntilNextCheck = ADAPTATION_GRANULARITY;
			}

			if (this.buffers.isEmpty()) {
				Log.error(this.name + ": No more buffers to release");
				break;
			}

			final T buffer = this.buffers.pop();
			final int sizeOfBuffer = getSizeOfBuffer(buffer);
			this.allocatedMemory -= sizeOfBuffer;
			excessMemory -= sizeOfBuffer;
			kilobytesUntilNextCheck -= sizeOfBuffer;
			++releasedBuffers;

			if (excessMemory < 0) {
				Log.info("Requesting garbage collection");
				System.gc();
				excessMemory = ClientUtils.getPhysicalMemorySize(this.pid) - this.grantedMemorySize;
				if (excessMemory < 0) {
					break;
				}
			}
		}

		// Construct the debug message
		if (Log.INFO) {
			final StringBuilder sb = new StringBuilder();
			sb.append(this.name);
			sb.append(": Allocated ");
			sb.append(allocatedBuffers);
			sb.append(" buffers, ");
			sb.append(" released ");
			sb.append(releasedBuffers);
			sb.append(" buffers, ");
			sb.append(this.allocatedMemory - oldAllocatedMemory);
			sb.append(" kilobytes, ");
			sb.append(this.buffers.size());
			sb.append(" buffers available");

			Log.info(sb.toString());
		}

	}

	/**
	 * Requests a buffer from the memory pool.
	 * 
	 * @return a buffer from the memory pool or <code>null</code> if no buffer is available
	 */
	public T requestBuffer() {

		return this.buffers.poll();
	}

	/**
	 * Returns a previously removed buffer back to the memory pool.
	 * 
	 * @param buffer
	 *        the buffer to return to the pool
	 */
	public void returnBuffer(final T buffer) {

		this.buffers.add(buffer);
	}

	/**
	 * Allocates a new buffer to be added to the memory pool.
	 * 
	 * @return the newly allocated buffer
	 */
	protected abstract T allocatedNewBuffer();

	/**
	 * Returns the size of the provided buffer in kilobytes.
	 * 
	 * @param buffer
	 *        the buffer to determine the size of
	 * @return the size of the provided buffer in kilobytes
	 */
	protected abstract int getSizeOfBuffer(T buffer);
}
