package nachos.threads;

import java.util.LinkedList;
import java.util.Queue;

import nachos.machine.*;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>,
 * and multiple threads can be waiting to <i>listen</i>. But there should never
 * be a time when both a speaker and a listener are waiting, because the two
 * threads can be paired off at this point.
 */
public class Communicator {
    /**
     * Allocate a new communicator.
     */
    public Communicator() {
    	lock = new Lock();
    }

    /**
     * Wait for a thread to listen through this communicator, and then transfer
     * <i>word</i> to the listener.
     *
     * <p>
     * Does not return until this thread is paired up with a listening thread.
     * Exactly one listener should receive <i>word</i>.
     *
     * @param	word	the integer to transfer.
     */
    public void speak(int word) {
    	

    	lock.acquire();
    	
    	if (listenQueue.isEmpty()) {
    		ComThread tempThread = new ComThread(word);
    		speakQueue.add(tempThread);
    		tempThread.getCond().sleep();
    		
    	} else {
    		ComThread listenComThread = listenQueue.poll();
    		listenComThread.setWord(word);
    		listenComThread.getCond().wake();
    	}
    	lock.release();
    	
    }

    /**
     * Wait for a thread to speak through this communicator, and then return
     * the <i>word</i> that thread passed to <tt>speak()</tt>.
     *
     * @return	the integer transferred.
     */    
    public int listen() {
    	int msg = 0;
    	lock.acquire();
    	
    	ComThread speakConThread;
    	if (speakQueue.isEmpty()) {
    		ComThread tempThread = new ComThread();
    		listenQueue.add(tempThread);
    		tempThread.getCond().sleep();
    		msg = tempThread.getWord();
    	} else {
    		speakConThread = speakQueue.poll();
    		speakConThread.getCond().wake();
    		msg = speakConThread.getWord();
    	}
    	lock.release();
    	
    	return msg;
    }
    
    private Lock lock;
    private Queue<ComThread> listenQueue = new LinkedList<ComThread>();
    private Queue<ComThread> speakQueue = new LinkedList<ComThread>();
    
    public class ComThread
    {
    	int word;
    	Condition2 cond = new Condition2(lock);
    	
    	public ComThread(int word)
    	{
    		this.word = word;
    	}
    	
    	public ComThread()
    	{
    		this.word = -1;
    	}
    	
    	public int getWord()
    	{
    		return this.word;
    	}
    	
    	public Condition2 getCond()
    	{
    		return this.cond;
    	}
    	
    	public void setWord(int word)
    	{
    		this.word = word;
    	}
    }
    
}


