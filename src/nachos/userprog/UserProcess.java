package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.HashMap;
import java.util.LinkedList;

/**
 * Encapsulates the state of a user process that is not contained in its user
 * thread (or threads). This includes its address translation state, a file
 * table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see nachos.vm.VMProcess
 * @see nachos.network.NetProcess
 */
public class UserProcess {
	/**
	 * Allocate a new process.
	 */
	public UserProcess() {
		int numPhysPages = Machine.processor().getNumPhysPages();
		pageTable = new TranslationEntry[numPhysPages];
		for (int i = 0; i < numPhysPages; i++)
			pageTable[i] = new TranslationEntry(i, i, true, false, false, false);

		// file descriptor 0 and 1 refer to standard input and standard output
		fileDescriptorList[0] = stdIn;
		fileDescriptorList[1] = stdOut;

		// part 2 exec, join, exit
		processID = processIDCounter;
		++processIDCounter;
		childProcesses = new LinkedList<>();
		childStatusMap = new HashMap<>();
		statusLock = new Lock();
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key <tt>Kernel.processClassName</tt>.
	 *
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to load
	 * the program, and then forks a thread to run it.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		userProcessThread = new UThread(this);
		userProcessThread.setName(name).fork();

		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch. Called by
	 * <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Machine.processor().setPageTable(pageTable);
	}

	/**
	 * Read a null-terminated string from this process's virtual memory. Read at
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for the
	 * null terminator, and convert it to a <tt>java.lang.String</tt>, without
	 * including the null terminator. If no null terminator is found, returns
	 * <tt>null</tt>.
	 *
	 * @param vaddr     the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 *                  including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was found.
	 */
	public String readVirtualMemoryString(int vaddr, int maxLength) {
		Lib.assertTrue(maxLength >= 0);

		byte[] bytes = new byte[maxLength + 1];

		int bytesRead = readVirtualMemory(vaddr, bytes);

		for (int length = 0; length < bytesRead; length++) {
			if (bytes[length] == 0)
				return new String(bytes, 0, length);
		}

		return null;
	}

	/**
	 * Transfer data from this process's virtual memory to all of the specified
	 * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data  the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 *
	 * @param vaddr  the first byte of virtual memory to read.
	 * @param data   the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 *               array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0) {
			vaddr = 0;
		}
		int lastAddress = Processor.makeAddress(numPages - 1, pageSize - 1);
		if (length > lastAddress - vaddr) {
			length = lastAddress - vaddr;
		}

		int firstVirPageNum = Processor.pageFromAddress(vaddr);
		int lastVirPageNum = Processor.pageFromAddress(vaddr + length);
		int bytesTransferred = 0;

		for (int i = firstVirPageNum; i <= lastVirPageNum; i++) {
			if (!pageTable[i].valid) {
				break; // page still valid, return bytes transferred immediately
			}
			int firstVirAddress = Processor.makeAddress(i, 0);
			int lastVirAddress = Processor.makeAddress(i, pageSize - 1);
			int pointer1;
			int pointer2;
			// data is the entire page
			if (vaddr <= firstVirAddress && vaddr + length >= lastVirAddress) {
				pointer1 = 0;
				pointer2 = pageSize - 1;
			}
			// data is from middle to end
			else if (vaddr > firstVirAddress && vaddr + length >= lastVirAddress) {
				pointer1 = vaddr - firstVirAddress;
				pointer2 = pageSize - 1;
			}
			// data is from beginning to middle
			else if (vaddr <= firstVirAddress && vaddr + length < lastVirAddress) {
				pointer1 = 0;
				pointer2 = (vaddr + length) - firstVirAddress;
			}
			// data is from first middle to later middle
			else {
				pointer1 = vaddr - firstVirAddress;
				pointer2 = (vaddr + length) - firstVirAddress;
			}
			int firstPhysAddress = Processor.makeAddress(pageTable[i].ppn, pointer1);
			System.arraycopy(memory, firstPhysAddress, data, offset + bytesTransferred, pointer2 - pointer1);
			bytesTransferred += (pointer2 - pointer1);
			pageTable[i].used = true;
		}

		return bytesTransferred;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual memory.
	 * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 *
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data  the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory. This
	 * method handles address translation details. This method must <i>not</i>
	 * destroy the current process if an error occurs, but instead should return the
	 * number of bytes successfully copied (or zero if no data could be copied).
	 *
	 * @param vaddr  the first byte of virtual memory to write.
	 * @param data   the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 *               memory.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);

		byte[] memory = Machine.processor().getMemory();

		if (vaddr < 0) {
			vaddr = 0;
		}
		int lastAddress = Processor.makeAddress(numPages - 1, pageSize - 1);
		if (length > lastAddress - vaddr) {
			length = lastAddress - vaddr;
		}

		int firstVirPageNum = Processor.pageFromAddress(vaddr);
		int lastVirPageNum = Processor.pageFromAddress(vaddr + length);
		int bytesTransferred = 0;
		for (int i = firstVirPageNum; i <= lastVirPageNum; i++) {
			if (!pageTable[i].valid || pageTable[i].readOnly) {
				break; // stop writing, return bytes transferred immediately
			}
			int firstVirAddress = Processor.makeAddress(i, 0);
			int lastVirAddress = Processor.makeAddress(i, pageSize - 1);
			int pointer1;
			int pointer2;

			// data is entire page
			if (vaddr <= firstVirAddress && vaddr + length >= lastVirAddress) {
				pointer1 = 0;
				pointer2 = pageSize - 1;
			}
			// data is from middle to end
			else if (vaddr > firstVirAddress && vaddr + length >= lastVirAddress) {
				pointer1 = vaddr - lastVirAddress;
				pointer2 = pageSize - 1;
			}
			// data is from beginning to middle
			else if (vaddr <= firstVirAddress && vaddr + length < lastVirAddress) {
				pointer1 = 0;
				pointer2 = (vaddr + length) - firstVirAddress;
			}
			// data is from first middle to later middle
			else {
				pointer1 = vaddr - firstVirAddress;
				pointer2 = (vaddr + length) - firstVirAddress;
			}
			int firstPhysAddress = Processor.makeAddress(pageTable[i].ppn, pointer1);
			System.arraycopy(data, offset + bytesTransferred, memory, firstPhysAddress, pointer2 - pointer1);
			bytesTransferred += (pointer2 - pointer1);
			pageTable[i].used = pageTable[i].dirty = true;
		}

		return bytesTransferred;
	}

	/**
	 * Load the executable with the specified name into this process, and prepare to
	 * pass it the specified arguments. Opens the executable, reads its header
	 * information, and copies sections and arguments into this process's virtual
	 * memory.
	 *
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the executable was successfully loaded.
	 */
	private boolean load(String name, String[] args) {
		Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");

		OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
		if (executable == null) {
			Lib.debug(dbgProcess, "\topen failed");
			return false;
		}

		try {
			coff = new Coff(executable);
		} catch (EOFException e) {
			executable.close();
			Lib.debug(dbgProcess, "\tcoff load failed");
			return false;
		}

		// make sure the sections are contiguous and start at page 0
		numPages = 0;
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			if (section.getFirstVPN() != numPages) {
				coff.close();
				Lib.debug(dbgProcess, "\tfragmented executable");
				return false;
			}
			numPages += section.getLength();
		}

		// make sure the argv array will fit in one page
		byte[][] argv = new byte[args.length][];
		int argsSize = 0;
		for (int i = 0; i < args.length; i++) {
			argv[i] = args[i].getBytes();
			// 4 bytes for argv[] pointer; then string plus one for null byte
			argsSize += 4 + argv[i].length + 1;
		}
		if (argsSize > pageSize) {
			coff.close();
			Lib.debug(dbgProcess, "\targuments too long");
			return false;
		}

		// program counter initially points at the program entry point
		initialPC = coff.getEntryPoint();

		// next comes the stack; stack pointer initially points to top of it
		numPages += stackPages;
		initialSP = numPages * pageSize;

		// and finally reserve 1 page for arguments
		numPages++;

		if (!loadSections())
			return false;

		// store arguments in last page
		int entryOffset = (numPages - 1) * pageSize;
		int stringOffset = entryOffset + args.length * 4;

		this.argc = args.length;
		this.argv = entryOffset;

		for (int i = 0; i < argv.length; i++) {
			byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
			Lib.assertTrue(writeVirtualMemory(entryOffset, stringOffsetBytes) == 4);
			entryOffset += 4;
			Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) == argv[i].length);
			stringOffset += argv[i].length;
			Lib.assertTrue(writeVirtualMemory(stringOffset, new byte[] { 0 }) == 1);
			stringOffset += 1;
		}

		return true;
	}

	/**
	 * Allocates memory for this process, and loads the COFF sections into memory.
	 * If this returns successfully, the process will definitely be run (this is the
	 * last step in process initialization that can fail).
	 *
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			Lib.debug(dbgProcess, "\tinsufficient physical memory");
			return false;
		}

		// part 1 multiprogramming, acquire the lock for free pages link list for next
		// available PhysPage
		UserKernel.freePagesLock.acquire();
		pageTable = new TranslationEntry[numPages];
		for (int i = 0; i < numPages; ++i) {
			Integer freePageIndex = UserKernel.freePagesList.poll();
			if (freePageIndex == null) {
				UserKernel.freePagesLock.release();
				return false;
			}
			pageTable[i] = new TranslationEntry(i, freePageIndex, true, false, false, false);
		}
		UserKernel.freePagesLock.release();
		
		for (int i = 0; i < coff.getNumSections(); ++i) {
			CoffSection section = coff.getSection(i);
			pageTable[section.getFirstVPN()].readOnly = section.isReadOnly();
		}

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);

			Lib.debug(dbgProcess,
					"\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;

				// for now, just assume virtual addresses=physical addresses
				section.loadPage(i, pageTable[vpn].ppn);
			}
		}

		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		// part 1 multiprogramming, deallocate
		UserKernel.freePagesLock.acquire();
		for (int i = 0; i < numPages; ++i) {
			UserKernel.freePagesList.add(pageTable[i].ppn);
		}
		UserKernel.freePagesLock.release();

		// release resources
		int size = fileDescriptorList.length;
		for (int i = 2; i < size; ++i) {
			OpenFile f = fileDescriptorList[i];
			if (f != null) {
				f.close();
				f = null;
			}
		}
		coff.close();
	}

	/**
	 * Initialize the processor's registers in preparation for running the program
	 * loaded into this process. Set the PC register to point at the start function,
	 * set the stack pointer register to point at the top of the stack, set the A0
	 * and A1 registers to argc and argv, respectively, and initialize all other
	 * registers to 0.
	 */
	public void initRegisters() {
		Processor processor = Machine.processor();

		// by default, everything's 0
		for (int i = 0; i < processor.numUserRegisters; i++)
			processor.writeRegister(i, 0);

		// initialize PC and SP according
		processor.writeRegister(Processor.regPC, initialPC);
		processor.writeRegister(Processor.regSP, initialSP);

		// initialize the first two argument registers to argc and argv
		processor.writeRegister(Processor.regA0, argc);
		processor.writeRegister(Processor.regA1, argv);
	}

	/**
	 * Handle the halt() system call.
	 */
	private int handleHalt() {

		Machine.halt();

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private int handleExit(int status) {

		if (parentProcess != null){
			parentProcess.statusLock.acquire();
			parentProcess.childStatusMap.put(processID, status);
			parentProcess.statusLock.release();
		}
		
		this.unloadSections();
		
		for (UserProcess childProcess : childProcesses ) {
			childProcess.parentProcess = null;
		}
		
		childProcesses.clear();
		if (this.processID == 0) {
			Kernel.kernel.terminate();
		} else {
			KThread.finish();
		}
		return status;
	}

	private int handleCreate(int vaddr) {
		// Check vaddr
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "Invalid virtual address for name disk file");
			return -1;
		}

		String filename = readVirtualMemoryString(vaddr, 256);
		if (filename == null) {
			Lib.debug(dbgProcess, "Illegal Filename");
			return -1;
		}

		int emptyIndex = -1;
		int size = fileDescriptorList.length;
		for (int i = 2; i < size; i++) {
			if (fileDescriptorList[i] == null) {
				emptyIndex = i;
				break;
			}
		}
		if (emptyIndex == -1) {
			Lib.debug(dbgProcess, "No free fileDescriptor available");
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(filename, true);
		if (file == null) {
			Lib.debug(dbgProcess, "Cannot create file");
			return -1;
		} else {
			fileDescriptorList[emptyIndex] = file;
			return emptyIndex;
		}
	}

	private int handleOpen(int vaddr) {
		// Check vaddr
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}
		String filename = readVirtualMemoryString(vaddr, 256);
		if (filename == null) {
			Lib.debug(dbgProcess, "Illegal Filename");
			return -1;
		}

		int emptyIndex = -1;
		int size = fileDescriptorList.length;
		for (int i = 2; i < size; i++) {
			if (fileDescriptorList[i] == null) {
				emptyIndex = i;
				break;
			}
		}
		if (emptyIndex == -1) {
			Lib.debug(dbgProcess, "No free fileDescriptor available");
			return -1;
		}

		OpenFile file = ThreadedKernel.fileSystem.open(filename, false);
		if (file == null) {
			Lib.debug(dbgProcess, "Cannot create file");
			return -1;
		} else {
			fileDescriptorList[emptyIndex] = file;
			return emptyIndex;
		}
	}

	private int handleRead(int fileDescriptor, int buffer, int count) {

		if (fileDescriptor < 0 || fileDescriptor > 15) {
			return -1;
		}

		OpenFile file = fileDescriptorList[fileDescriptor];

		if (file == null) {
			return -1;
		}
		if (count < 0) {
			return -1;
		}

		byte[] newBuffer = new byte[count];
		int bytesRead = file.read(newBuffer, 0, count);
		if (bytesRead == -1) {
			return -1;
		}

		return writeVirtualMemory(buffer, newBuffer, 0, bytesRead);
	}

	private int handleWrite(int fileDescriptor, int buffer, int count) {
		if (fileDescriptor < 0 || fileDescriptor > 15) {
			return -1;
		}

		OpenFile file = fileDescriptorList[fileDescriptor];

		if (file == null) {
			return -1;
		}
		if (count < 0) {
			return -1;
		}
		byte[] newBuffer = new byte[count];

		int bytesWritten = readVirtualMemory(buffer, newBuffer, 0, count);
		int returnAmount = file.write(newBuffer, 0, bytesWritten);

		if (returnAmount != count) {
			return -1;
		}

		return returnAmount;
	}

	private int handleClose(int fileDescriptor) {
		if ((fileDescriptor < 0) || (fileDescriptor > 15) || fileDescriptorList[fileDescriptor] == null) {
			return -1;
		}
		fileDescriptorList[fileDescriptor].close();
		fileDescriptorList[fileDescriptor] = null;
		return 0;
	}

	private int handleUnlink(int vaddr) {
		if (vaddr < 0) {
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}

		String filename = readVirtualMemoryString(vaddr, 256);
		if (filename == null) {
			return -1;
		}
		boolean succeeded = ThreadedKernel.fileSystem.remove(filename);
		if (!succeeded) {
			return -1;
		}
		return 0;
	}

	private int handleExec(int fileVirtualAddress, int argc, int argvAddr) {

		// Check vaddr
		if (fileVirtualAddress < 0) {
			Lib.debug(dbgProcess, "Invalid virtual address");
			return -1;
		}
		String filename = readVirtualMemoryString(fileVirtualAddress, 256);
		if (filename == null) {
			Lib.debug(dbgProcess, "Illegal File name");
			return -1;
		}

		// must include .coff extension
		String[] filenameSplit = filename.split("\\.");
		String extension = filenameSplit[filenameSplit.length - 1];
		if (!extension.equals("coff")) {
			Lib.debug(dbgProcess, "File name must be .coff file");
			return -1;
		}

		// process arguments
		String[] arguments = new String[argc];
		for (int i = 0; i < argc; ++i) {
			byte[] buffer = new byte[4];
			int byteRead = readVirtualMemory(argvAddr + (i * 4), buffer);
			if (byteRead != 4) {
				Lib.debug(dbgProcess, "Pointers are not read correctly");
				return -1;
			}
			int argStringVaddr = Lib.bytesToInt(buffer, 0);
			String argString = readVirtualMemoryString(argStringVaddr, 256);
			if (argString == null) {
				Lib.debug(dbgProcess, "Invalid argument read");
				return -1;
			}
			arguments[i] = argString;
		}

		// spawn child
		UserProcess newChild = newUserProcess();
		if (newChild.execute(filename, arguments)) {
			this.childProcesses.add(newChild);
			newChild.parentProcess = this;
			return newChild.processID;
		} else {
			Lib.debug(dbgProcess, "Cannot execute the problem");
			return -1;
		}
	}

	private int handleJoin(int processID, int statusAddr) {

		UserProcess child = null;
		int childrenSize = this.childProcesses.size();

		// find child process by processID
		for (int i = 0; i < childrenSize; i++) {
			if (this.childProcesses.get(i).processID == processID) {
				child = this.childProcesses.get(i);
				break;
			}
		}

		// Child doesn't exist
		if (child == null) {
			return -1;
		}

		child.userProcessThread.join();

		// drop child
		this.childProcesses.remove(child);
		child.parentProcess = null;

		statusLock.acquire();
		Integer status = childStatusMap.get(child.processID);
		statusLock.release();

		if (status == -2) {
			return 0; 
		}
		
		if (status != null) {
			byte[] buffer = new byte[4];
			Lib.bytesFromInt(buffer, 0, status);
			int bytesWritten = writeVirtualMemory(statusAddr, buffer);
			if (bytesWritten == 4) {
				return 1;
			} else {
				return 0;
			}
		} else {
			return 0;
		}
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2, syscallJoin = 3, syscallCreate = 4,
			syscallOpen = 5, syscallRead = 6, syscallWrite = 7, syscallClose = 8, syscallUnlink = 9;

	/**
	 * Handle a syscall exception. Called by <tt>handleException()</tt>. The
	 * <i>syscall</i> argument identifies which syscall the user executed:
	 *
	 * <table>
	 * <tr>
	 * <td>syscall#</td>
	 * <td>syscall prototype</td>
	 * </tr>
	 * <tr>
	 * <td>0</td>
	 * <td><tt>void halt();</tt></td>
	 * </tr>
	 * <tr>
	 * <td>1</td>
	 * <td><tt>void exit(int status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>2</td>
	 * <td><tt>int  exec(char *name, int argc, char **argv);
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>3</td>
	 * <td><tt>int  join(int pid, int *status);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>4</td>
	 * <td><tt>int  creat(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>5</td>
	 * <td><tt>int  open(char *name);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>6</td>
	 * <td><tt>int  read(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 *								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>8</td>
	 * <td><tt>int  close(int fd);</tt></td>
	 * </tr>
	 * <tr>
	 * <td>9</td>
	 * <td><tt>int  unlink(char *name);</tt></td>
	 * </tr>
	 * </table>
	 * 
	 * @param syscall the syscall number.
	 * @param a0      the first syscall argument.
	 * @param a1      the second syscall argument.
	 * @param a2      the third syscall argument.
	 * @param a3      the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
//		System.out.println(syscall);
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallExit:
			return handleExit(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);
		case syscallCreate:
			return handleCreate(a0);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		default:
			Lib.debug(dbgProcess, "Unknown syscall " + syscall);
			Lib.assertNotReached("Unknown system call!");
		}
		return 0;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>.
	 * The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 *
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0), processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1), processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: " + Processor.exceptionNames[cause]);
			handleExit(-2); // something is wrong, return status with -2
			Lib.assertNotReached("Unexpected exception");
		}
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;
	private int argc, argv;

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';

	// Each process can handle up to 16 open files
	private OpenFile[] fileDescriptorList = new OpenFile[16];
	private OpenFile stdIn = UserKernel.console.openForReading();
	private OpenFile stdOut = UserKernel.console.openForWriting();

	// Multiprogramming
	// process page table, will be remove later for inverted page table
	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	// part 2 syscall process id to keep track
	private static int processIDCounter = 0;
	private int processID;
	private UserProcess parentProcess;
	private LinkedList<UserProcess> childProcesses;
	private HashMap<Integer, Integer> childStatusMap;
	private Lock statusLock;
	private UThread userProcessThread;
}
