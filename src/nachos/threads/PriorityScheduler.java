package nachos.threads;

import nachos.machine.Lib;
import nachos.machine.Machine;

import java.util.LinkedList;
import java.util.PriorityQueue;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fashion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());

			// implement me
			ThreadState nextThread = pickNextThread();
			threadPriorityQueue.remove(nextThread);
			
			if (nextThread == null) {
				return null;
			}
			
			if (owner != null) {
				this.owner.owns.remove(this);
			}
			nextThread.waitFor = null;
			nextThread.owns.add(this);
			
			// since there's changes in graph, recalculate effective priority for ThreadState
			nextThread.recalcEffectivePriority();
			
			this.owner = nextThread;
			
			return nextThread.thread;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			
			boolean intStatus = Machine.interrupt().disable();
			
			//reorder (solve heap problem?)
			this.threadPriorityQueue =
					new java.util.PriorityQueue<ThreadState>(threadPriorityQueue); 
			
			Machine.interrupt().restore(intStatus);
			
			ThreadState next = threadPriorityQueue.peek();
			return next;
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			System.out.println("Boop");
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread.
		 */
		public boolean transferPriority;
		// threadPriorityQueue holds ThreadState that 
		private java.util.PriorityQueue<ThreadState> threadPriorityQueue = new java.util.PriorityQueue<>();
		// owner hold the ThreadState that owns this PriorityQueue
		private ThreadState owner;
	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState implements Comparable<ThreadState> {
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			setPriority(priorityDefault);
			this.effectivePriority = priorityDefault;
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() {
			// implement me
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) {
            if (this.priority == priority) {
                return;
            }
            this.priority = priority;

            this.recalcEffectivePriority();
        }

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			this.time = Machine.timer().getTime();
			waitQueue.threadPriorityQueue.add(this);
			this.waitFor = waitQueue;
			
			// donation through recalculate effective priority
			recalcEffectivePriority();
		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			// lock acquire
			this.owns.add(waitQueue);
			waitQueue.owner = this;
			
			// recalculateEffectivePriority
			recalcEffectivePriority();
		}
		
		public void recalcEffectivePriority() {
			/* this function donate and restore all nodes */
			int originalPriority = this.getPriority();
			int maximumPriority = -1;
			
			// going forward to calculate max effective priority
			for (PriorityQueue q : owns) {
				ThreadState donatorThread = q.pickNextThread();
				if (donatorThread != null) {
					if (maximumPriority < donatorThread.getEffectivePriority()) {
						if (q.transferPriority) {
							maximumPriority = donatorThread.getEffectivePriority();
						}
					}
				}
			}
			
			if (originalPriority > maximumPriority) {
				maximumPriority = originalPriority;
			}
			this.effectivePriority = maximumPriority;
			
			// propogate recalculate effective priority to threads that owns the thread you're waiting on
			if (this.waitFor != null && this.waitFor.owner != null &&
					this.effectivePriority != this.waitFor.owner.effectivePriority) {
				this.waitFor.owner.recalcEffectivePriority();
			}
				
		}
		
		@Override
		public int compareTo(ThreadState ts) {
			int thisEffective = this.effectivePriority;
			int otherEffective = ts.effectivePriority;
			
			if (thisEffective < otherEffective || this.time >= ts.time) {
				return 1;
			} else if (thisEffective > otherEffective) {
				return -1;
			} else {
				return 0;
			}
		}
		
		public void setEffectivePriority(int newEffectivePriority) {
			if (newEffectivePriority > effectivePriority) {
				this.effectivePriority = newEffectivePriority;
			}
		}
		
		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		
		// the PriorityQueue this thread is waiting for
		protected PriorityQueue waitFor;
		// the PriorityQueue this thread owns
		protected LinkedList<PriorityQueue> owns = new LinkedList<>();
		// caching effective priority
		protected int effectivePriority;
		
		public long time = Machine.timer().getTime();

	}
}
