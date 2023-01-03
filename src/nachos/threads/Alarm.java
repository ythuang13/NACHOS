package nachos.threads;

import java.util.PriorityQueue;

import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
//	KThread.currentThread().yield();
    
    	boolean intStatus = Machine.interrupt().disable();
    	
    	long currentTime = Machine.timer().getTime();
    	PriorityThread tempPThread = waitQueue.peek();
    	while (tempPThread != null && tempPThread.getTime() <= currentTime){
    		tempPThread = waitQueue.poll();
    		tempPThread.priorityWake();
    		tempPThread = waitQueue.peek();
        }
    	
    	Machine.interrupt().restore(intStatus);
    	KThread.yield();
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
	// for now, cheat just to get something working (busy waiting is bad)
//	long wakeTime = Machine.timer().getTime() + x;
//	while (wakeTime > Machine.timer().getTime())
//	    KThread.yield();
	
	boolean intStatus = Machine.interrupt().disable();
	
	long wakeTime = Machine.timer().getTime() + x;
	
	KThread currentThread = KThread.currentThread();
	PriorityThread tempPThread = new PriorityThread(currentThread, wakeTime); 
	
	waitQueue.add(tempPThread);
	
	tempPThread.prioritySleep();
	
	Machine.interrupt().restore(intStatus);
    }
    
    private PriorityQueue<PriorityThread> waitQueue = new PriorityQueue<>();
    
    public class PriorityThread implements Comparable<PriorityThread>{
    	private KThread thread;
    	private Long time;
    	private Lock lock;
        private Condition2 cond;
    	
    	public PriorityThread(KThread thread, Long time) {
    		this.thread = thread;
    		this.time = time;
    		lock = new Lock();
    		cond = new Condition2(lock);
    	}
    	
    	@Override
    	public int compareTo(PriorityThread pt) {
    		if (this.time < pt.getTime()) {
    			return -1;
    		} else if (this.time > pt.getTime()) {
    			return 1;
    		} else {
    			return 0;
    		}
    	}
    	
    	Long getTime() {
    		return this.time;
    	}
    	
    	KThread getThread() {
    		return this.thread;
    	}
    	
    	Lock getLock() {
    		return this.lock;
    	}
    	
    	Condition2 getCondition() {
    		return this.cond;
    	}
    	
    	void prioritySleep() {
    		lock.acquire();
    		cond.sleep();
    		lock.release();
    	}
    	
    	void priorityWake() {
    		lock.acquire();
    		cond.wake();
    		lock.release();
    	}
    }
}
