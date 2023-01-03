package nachos.userprog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/**
 * A kernel that can support multiple user processes.
 */
public class UserKernel extends ThreadedKernel {
	/**
	 * Allocate a new user kernel.
	 */
	public UserKernel() {
		super();

	}

	/**
	 * Initialize this kernel. Creates a synchronized console and sets the
	 * processor's exception handler.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		console = new SynchConsole(Machine.console());

		// part 1, free pages for user process to use
		freePagesList = new LinkedList<Integer>();
		
		int physPageNums = Machine.processor().getNumPhysPages();
		for (int i = 0; i < physPageNums; ++i) {
			freePagesList.add(i);
		}
		freePagesLock = new Lock();
		System.out.println(physPageNums);

		
		Machine.processor().setExceptionHandler(new Runnable() {
			public void run() {
				exceptionHandler();
			}
		});

	}

	/**
	 * Test the console device.
	 */
	public void selfTest() {
		super.selfTest();

		System.out.println("Testing the console device. Typed characters");
		System.out.println("will be echoed until q is typed.");

		char c;

		do {
			c = (char) console.readByte(true);
			console.writeByte(c);
		} while (c != 'q');

		System.out.println("");
	}

	/**
	 * Returns the current process.
	 *
	 * @return the current process, or <tt>null</tt> if no process is current.
	 */
	public static UserProcess currentProcess() {
		if (!(KThread.currentThread() instanceof UThread))
			return null;

		return ((UThread) KThread.currentThread()).process;
	}

	/**
	 * The exception handler. This handler is called by the processor whenever a
	 * user instruction causes a processor exception.
	 *
	 * <p>
	 * When the exception handler is invoked, interrupts are enabled, and the
	 * processor's cause register contains an integer identifying the cause of the
	 * exception (see the <tt>exceptionZZZ</tt> constants in the <tt>Processor</tt>
	 * class). If the exception involves a bad virtual address (e.g. page fault, TLB
	 * miss, read-only, bus error, or address error), the processor's BadVAddr
	 * register identifies the virtual address that caused the exception.
	 */
	public void exceptionHandler() {
		Lib.assertTrue(KThread.currentThread() instanceof UThread);

		UserProcess process = ((UThread) KThread.currentThread()).process;
		int cause = Machine.processor().readRegister(Processor.regCause);
		process.handleException(cause);
	}

	/**
	 * Start running user programs, by creating a process and running a shell
	 * program in it. The name of the shell program it must run is returned by
	 * <tt>Machine.getShellProgramName()</tt>.
	 *
	 * @see nachos.machine.Machine#getShellProgramName
	 */
	public void run() {
		super.run();

		UserProcess process = UserProcess.newUserProcess();

		String shellProgram = Machine.getShellProgramName();
		// process and pass the correct arguments to nachos for c program
		String[] args = Machine.getCommandLineArguments();
		int size = args.length;

		ArrayList<String> processedArgs = new ArrayList<>();
		processedArgs.add(Machine.getShellProgramName());
		for (int i = 0; i < size; ++i) {
			String arg = args[i];
			if (arg.charAt(0) == '-') {
				++i;
			} else {
				processedArgs.add(arg);
			}
		}
		args = processedArgs.toArray(new String[processedArgs.size()]);
		// System.out.println(Arrays.toString(args));
		Lib.assertTrue(process.execute(shellProgram, args));

		KThread.currentThread().finish();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		super.terminate();
	}

	/** Globally accessible reference to the synchronized console. */
	public static SynchConsole console;

	// dummy variables to make javac smarter
	private static Coff dummy1 = null;

	// keep track of free pages in a link list
	public static LinkedList<Integer> freePagesList;

	// synchronization for accessing the free pages list
	public static Lock freePagesLock;
}
