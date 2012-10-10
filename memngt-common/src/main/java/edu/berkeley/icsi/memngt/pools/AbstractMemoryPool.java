package edu.berkeley.icsi.memngt.pools;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.esotericsoftware.minlog.Log;

import edu.berkeley.icsi.memngt.utils.ClientUtils;

public abstract class AbstractMemoryPool<T> {

	private static final int ADAPTATION_GRANULARITY = 10 * 1024;

	private final String name;

	private final int pid;

	private final BlockingQueue<T> buffers;

	private final Object adjustmentLock = new Object();

	/**
	 * The total amount of memory allocated by this pool in kilobytes.
	 */
	private final AtomicInteger allocatedMemory = new AtomicInteger(0);

	/**
	 * The amount of memory still available in the memory pool.
	 */
	private final AtomicInteger availableMemory = new AtomicInteger(0);

	private volatile int lowMemoryThreshold = -1;

	private volatile LowMemoryListener lowMemoryListener = null;

	private final AtomicBoolean lowMemoryNotificationSent = new AtomicBoolean(false);

	private volatile int highMemoryThreshold = Integer.MAX_VALUE;

	private volatile HighMemoryListener highMemoryListener = null;

	private final AtomicBoolean highMemoryNotificationSent = new AtomicBoolean(false);

	/**
	 * The granted memory size of the application containing the memory pool in kilobytes.
	 */
	private int grantedMemorySize = -1;

	protected AbstractMemoryPool(final String name, final int initialCapacity) {
		this.name = name;
		this.pid = ClientUtils.getPID();
		this.buffers = new ArrayBlockingQueue<T>(initialCapacity);
	}

	protected AbstractMemoryPool(final int initialCapacity) {
		final int pid = ClientUtils.getPID();
		this.name = getDefaultName(pid);
		this.pid = pid;
		this.buffers = new ArrayBlockingQueue<T>(initialCapacity);
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
		this.allocatedMemory.set(0);
		this.availableMemory.set(0);
		this.grantedMemorySize = -1;
		this.lowMemoryThreshold = -1;
		this.lowMemoryListener = null;
		this.lowMemoryNotificationSent.set(false);
		this.highMemoryThreshold = Integer.MAX_VALUE;
		this.highMemoryListener = null;
		this.highMemoryNotificationSent.set(false);
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
	 * Sets the low memory listener for this memory pool. The listener is notified when the amount of memory available
	 * in this pool falls below the specified threshold.
	 * 
	 * @param lowMemoryThreshold
	 *        the memory threshold in kilobytes
	 * @param lowMemoryListener
	 *        the low memory listener to register
	 */
	public void setLowMemoryListener(final int lowMemoryThreshold, final LowMemoryListener lowMemoryListener) {

		this.lowMemoryListener = lowMemoryListener;
		this.lowMemoryThreshold = lowMemoryThreshold;
		this.lowMemoryNotificationSent.set(false);
	}

	/**
	 * Sets the high memory listener for this memory pool. The listener is notified when the amount of memory available
	 * in this pool rises above the specified threshold.
	 * 
	 * @param highMemoryThreshold
	 *        the memory threshold in kilobytes
	 * @param highMemoryListener
	 *        the high memory listener to register
	 */
	public void setHighMemoryListener(final int highMemoryThreshold, final HighMemoryListener highMemoryListener) {

		this.highMemoryListener = highMemoryListener;
		this.highMemoryThreshold = highMemoryThreshold;
		this.highMemoryNotificationSent.set(false);
	}

	/**
	 * Adjusts the memory pool to the granted memory size.
	 */
	public void adjust() {

		if (this.grantedMemorySize < 0) {
			throw new IllegalStateException("grantedMemorySize is not set");
		}

		int availableMemory = this.availableMemory.get();
		synchronized (this.adjustmentLock) {

			final int oldAllocatedMemory = availableMemory;

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
				this.allocatedMemory.addAndGet(sizeOfBuffer);
				availableMemory = this.availableMemory.addAndGet(sizeOfBuffer);
				kilobytesUntilNextCheck -= sizeOfBuffer;
				++allocatedBuffers;
			}

			int excessMemory = ClientUtils.getPhysicalMemorySize(this.pid) - this.grantedMemorySize;
			int releasedBuffers = 0;
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

				final T buffer = this.buffers.poll();
				final int sizeOfBuffer = getSizeOfBuffer(buffer);
				this.allocatedMemory.addAndGet(-sizeOfBuffer);
				availableMemory = this.availableMemory.addAndGet(-sizeOfBuffer);
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
				sb.append(this.allocatedMemory.get() - oldAllocatedMemory);
				sb.append(" kilobytes, ");
				sb.append(this.buffers.size());
				sb.append(" buffers available");

				Log.info(sb.toString());
			}
		}

		if (availableMemory > this.lowMemoryThreshold) {
			this.lowMemoryNotificationSent.set(false);
		} else if (availableMemory < this.highMemoryThreshold) {
			this.highMemoryNotificationSent.set(false);
		}

		if (availableMemory < this.lowMemoryThreshold) {
			if (this.lowMemoryNotificationSent.compareAndSet(false, true)) {
				this.lowMemoryListener.indicateLowMemory(availableMemory);
			}
		} else if (availableMemory > this.highMemoryThreshold) {
			if (this.highMemoryNotificationSent.compareAndSet(false, true)) {
				this.highMemoryListener.indicateHighMemory(availableMemory);
			}
		}
	}

	/**
	 * Requests a buffer from the memory pool.
	 * 
	 * @return a buffer from the memory pool or <code>null</code> if no buffer is available
	 */
	public T requestBuffer() {

		final T buffer = this.buffers.poll();
		if (buffer == null) {
			return null;
		}

		final int availableMemory = this.availableMemory.addAndGet(-getSizeOfBuffer(buffer));

		if (availableMemory < this.highMemoryThreshold) {
			this.highMemoryNotificationSent.set(false);
		}

		if (availableMemory < this.lowMemoryThreshold) {
			if (this.lowMemoryNotificationSent.compareAndSet(false, true)) {
				this.lowMemoryListener.indicateLowMemory(availableMemory);
			}
		}

		return buffer;
	}

	/**
	 * Returns a previously removed buffer back to the memory pool.
	 * 
	 * @param buffer
	 *        the buffer to return to the pool
	 */
	public void returnBuffer(final T buffer) {

		// Make sure we update available memory before putting the buffer back to the queue
		final int availableMemory = this.availableMemory.addAndGet(getSizeOfBuffer(buffer));
		this.buffers.add(buffer);

		if (availableMemory > this.lowMemoryThreshold) {
			this.lowMemoryNotificationSent.set(false);
		}

		if (availableMemory > this.highMemoryThreshold) {
			if (this.highMemoryNotificationSent.compareAndSet(false, true)) {
				this.highMemoryListener.indicateHighMemory(availableMemory);
			}
		}
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
