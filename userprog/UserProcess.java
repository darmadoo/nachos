package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import java.io.EOFException;
import java.util.HashMap;



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
		// PID = UserKernel.PID;
		// UserKernel.PID++;

		// Increment the number of processes 
		PID = processCount++;
		UserProcess.totalPID++;
		exitProperly = false;
		exitStatus = 0;
		// First two should be for stdin and stdout 
		addToFiles(0, UserKernel.console.openForReading());
		addToFiles(1, UserKernel.console.openForWriting());	
	}

	/**
	 * Allocate and return a new process of the correct class. The class name is
	 * specified by the <tt>nachos.conf</tt> key
	 * <tt>Kernel.processClassName</tt>.
	 * 
	 * @return a new process of the correct class.
	 */
	public static UserProcess newUserProcess() {
		return (UserProcess) Lib.constructObject(Machine.getProcessClassName());
	}

	/**
	 * Execute the specified program with the specified arguments. Attempts to
	 * load the program, and then forks a thread to run it.
	 * 
	 * @param name the name of the file containing the executable.
	 * @param args the arguments to pass to the executable.
	 * @return <tt>true</tt> if the program was successfully executed.
	 */
	public boolean execute(String name, String[] args) {
		if (!load(name, args))
			return false;

		uthread = new UThread(this);
		uthread.setName(name).fork();
		return true;
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
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
	 * most <tt>maxLength + 1</tt> bytes from the specified address, search for
	 * the null terminator, and convert it to a <tt>java.lang.String</tt>,
	 * without including the null terminator. If no null terminator is found,
	 * returns <tt>null</tt>.
	 * 
	 * @param vaddr the starting virtual address of the null-terminated string.
	 * @param maxLength the maximum number of characters in the string, not
	 * including the null terminator.
	 * @return the string read, or <tt>null</tt> if no null terminator was
	 * found.
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
	 * @param data the array where the data will be stored.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data) {
		return readVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from this process's virtual memory to the specified array.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to read.
	 * @param data the array where the data will be stored.
	 * @param offset the first byte to write in the array.
	 * @param length the number of bytes to transfer from virtual memory to the
	 * array.
	 * @return the number of bytes successfully transferred.
	 */
	public int readVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		// Get the physical memory of the MIPS machine
		byte[] memory = Machine.processor().getMemory();

		// Check if virtual address is valid
		if (vaddr < 0 || vaddr >= memory.length) { return -1; }

		// Return the number of bytes transferred 
		return (readWriteHelper(vaddr, data, offset, length, memory, false) - vaddr);
	}

	// Helper method for read and write virtual memory
	private int readWriteHelper(int vaddr, byte[] data, int offset, int length, byte[] memory, boolean isWrite){
		// save the current virtual address
		int currentAddress = vaddr;

		// While there is still data to transfer
		while(length > 0){
			// Get the virtual page from the current address 
			int vpn             = Machine.processor().pageFromAddress(currentAddress);
			// Also get the offset of the current address 
			int vpnOffset       = Machine.processor().offsetFromAddress(currentAddress);
			// The virtual to physical page translation 
			TranslationEntry et = (isWrite) ? getWriteTE(vpn) : getReadTE(vpn);

			if(et == null){ return currentAddress; }

			// get the physical address 
			int physicalAddress = Machine.processor().makeAddress(et.ppn, vpnOffset);

			// Get the amount transfered 
		    int amount = Math.min(length, pageSize - vpnOffset);

		    // If the isWrite flag is set, then we are writing virtual mem, else we are reading virtual mem
		    if(isWrite){
		    	// writeVirtualMem
				System.arraycopy(data, offset, memory, physicalAddress, amount);
		    } else {
		    	// ReadVirtualMem
				System.arraycopy(memory, physicalAddress, data, offset, amount);
		    }

		    // subtract transfered data
		    length = length - amount;
		    // Add the amount read
		    offset = offset + amount;
		    currentAddress = currentAddress + amount;
		}

		return currentAddress;
	}

	private TranslationEntry getWriteTE(int vpn){
		if (vpn < 0 || vpn >= numPages || pageTable[vpn] == null){ 
			Lib.debug(dbgProcess, "MMM: getWriteTE null " + " vpn: " + vpn);
			return null;
		}
		
		TranslationEntry translationEntry = pageTable[vpn];
		translationEntry.used = true;
		translationEntry.dirty = true;

		if (translationEntry.readOnly){ return null; }
		return translationEntry;
	}

	private TranslationEntry getReadTE(int vpn){
		if (vpn < 0 || vpn >= numPages || pageTable[vpn] == null){
			Lib.debug(dbgProcess, "MMM: getReadTE null " + " vpn: " + vpn);
			return null;
		}
		TranslationEntry translationEntry = pageTable[vpn];
		translationEntry.used = true;
		return translationEntry;
	}

	/**
	 * Transfer all data from the specified array to this process's virtual
	 * memory. Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @return the number of bytes successfully transferred.
	 */
	public int writeVirtualMemory(int vaddr, byte[] data) {
		return writeVirtualMemory(vaddr, data, 0, data.length);
	}

	/**
	 * Transfer data from the specified array to this process's virtual memory.
	 * This method handles address translation details. This method must
	 * <i>not</i> destroy the current process if an error occurs, but instead
	 * should return the number of bytes successfully copied (or zero if no data
	 * could be copied).
	 * 
	 * @param vaddr the first byte of virtual memory to write.
	 * @param data the array containing the data to transfer.
	 * @param offset the first byte to transfer from the array.
	 * @param length the number of bytes to transfer from the array to virtual
	 * memory.
	 * @return the number of bytes successfully transferred.
	 */

	public int writeVirtualMemory(int vaddr, byte[] data, int offset, int length) {
		Lib.assertTrue(offset >= 0 && length >= 0 && offset + length <= data.length);
		// Get the physical memory of the MIPS machine	
		byte[] memory = Machine.processor().getMemory();

		// Check if virtual address is valid.
		if (vaddr < 0 || vaddr >= memory.length) { return -1; }

		// Return the number of bytes transferred 
		return readWriteHelper(vaddr, data, offset, length, memory, true) - vaddr;
	}

	/**
	 * Load the executable with the specified name into this process, and
	 * prepare to pass it the specified arguments. Opens the executable, reads
	 * its header information, and copies sections and arguments into this
	 * process's virtual memory.
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
		}
		catch (EOFException e) {
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
	 * Allocates memory for this process, and loads the COFF sections into
	 * memory. If this returns successfully, the process will definitely be run
	 * (this is the last step in process initialization that can fail).
	 * 
	 * @return <tt>true</tt> if the sections were successfully loaded.
	 */
	protected boolean loadSections() {
		// Not enough memory
		if (numPages > Machine.processor().getNumPhysPages()) {
			coff.close();
			return false;
		}

		// Get physicall page allocation.
		int[] physicalPageNums = UserKernel.allocatePages(numPages);

		// Return false if there is no physicall memory left.
		if(physicalPageNums == null){
			coff.close();
			return false;
		}
		
		// Initallize pageTable.
		pageTable = new TranslationEntry[numPages];

		// load sections
		for (int s = 0; s < coff.getNumSections(); s++) {
			CoffSection section = coff.getSection(s);
			for (int i = 0; i < section.getLength(); i++) {
				int vpn = section.getFirstVPN() + i;
				// Check if the index is within the physical page range
				if(vpn < physicalPageNums.length){
					int ppn = physicalPageNums[vpn];
					pageTable[vpn] = new TranslationEntry(vpn, ppn, true, section.isReadOnly(), false, false);
					section.loadPage(i, ppn);
				}
				else{
					// Array out of bounds
					// Lib.debug(dbgProcess, "MMM: array out of bounds");
					return false;
				}
			}
		}

		// Allocate the free pages 
		for (int i = numPages - stackPages - 1; i < numPages; i++) {
			pageTable[i] = new TranslationEntry(i, physicalPageNums[i], true, false, false, false);
		}
		
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		coff.close();
		// Give back the list of free pages 
		UserKernel.releasePages(pageTable, numPages);
		pageTable = null;
	}

	/**
	 * Initialize the processor's registers in preparation for running the
	 * program loaded into this process. Set the PC register to point at the
	 * start function, set the stack pointer register to point at the top of the
	 * stack, set the A0 and A1 registers to argc and argv, respectively, and
	 * initialize all other registers to 0.
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

		if(PID == 0){
			Machine.halt();			
		}

		Lib.assertNotReached("Machine.halt() did not halt machine!");
		return 0;
	}

	private static final int syscallHalt = 0, syscallExit = 1, syscallExec = 2,
			syscallJoin = 3, syscallCreate = 4, syscallOpen = 5,
			syscallRead = 6, syscallWrite = 7, syscallClose = 8,
			syscallUnlink = 9;

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
	 * 								</tt></td>
	 * </tr>
	 * <tr>
	 * <td>7</td>
	 * <td><tt>int  write(int fd, char *buffer, int size);
	 * 								</tt></td>
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
	 * @param a0 the first syscall argument.
	 * @param a1 the second syscall argument.
	 * @param a2 the third syscall argument.
	 * @param a3 the fourth syscall argument.
	 * @return the value to be returned to the user.
	 */
	public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
		switch (syscall) {
		case syscallHalt:
			return handleHalt();
		case syscallWrite:
			return handleWrite(a0, a1, a2);
		case syscallOpen:
			return handleOpen(a0);
		case syscallRead:
			return handleRead(a0, a1, a2);
		case syscallClose:
			return handleClose(a0);
		case syscallUnlink:
			return handleUnlink(a0);
		case syscallExit:
			return handleExit(a0);
		case syscallCreate:
			return handleCreat(a0);
		case syscallExec:
			return handleExec(a0, a1, a2);
		case syscallJoin:
			return handleJoin(a0, a1);

		default:
			Lib.debug(dbgProcess, "Unknown syscall ");
			Lib.assertNotReached("Unknown system call! " + syscall);
		}
		return 0;
	}

	/*
		==== NEW FUNCTION FOR PROJECT 2 =======
	*/
	protected int handleWrite(int fileDescriptor, int buffer, int count){
		Lib.debug(dbgProcess, "MMM: write buffer: " + buffer);
		// need to check if fd is valid 
		if(fileDescriptor >= maxFileCount || fileDescriptor < 0){ return -1; }

		if(count < 0 || buffer < 0){ return -1; }

		// Get current file and check if its valid
		OpenFile openFile = getFile(fileDescriptor);
		if(openFile == null){ return -1; }

		// Allocate a byte buffer and check if its valid
		byte dataBuffer[] = new byte[count];
		int r = readVirtualMemory(buffer, dataBuffer, 0, count);
		if(r == -1){ return -1;	}

		return openFile.write(dataBuffer, 0, r);
	}

	protected int handleOpen(int fileIndex){
		// Get the string name
		String fileName = readVirtualMemoryString(fileIndex, maxStringLength);

		if(fileName == null){return -1; }

		OpenFile tempOpenFile = UserKernel.fileSystem.open(fileName, false);

		// File is invalid 
		if(tempOpenFile == null){ return -1; }

		return addToFiles(tempOpenFile);

	}

	protected int handleRead(int fileDescriptor, int buffer, int count) {
		// Check file descriptor, count, and buffer
		if(fileDescriptor < 0 || fileDescriptor >= files.length || count < 0 || buffer < 0){ return -1; }

		// Open the file and check if it is valid
		OpenFile openFile = getFile(fileDescriptor);
		if(openFile == null){ return -1; }

		// Get length and check if its valid
		byte buf[] = new byte[count];
		int length = openFile.read(buf, 0, count);
		if (length < 0) { return -1; }

		return writeVirtualMemory(buffer, buf, 0, length);
	}

	protected int handleClose(int fileDescriptor) {
		// Invalid file descriptor
		if(fileDescriptor < 0 || fileDescriptor >= files.length) { return -1; }
		
		// Open the file 
		OpenFile openFile = getFile(fileDescriptor);
			
		// File descriptor doesn't exist
		if(openFile == null) { return -1; }
		// Clear	
		files[fileDescriptor] = null;
		openFile.close();
		return 0;
	}

	protected int handleUnlink(int name) {
		String fileName = readVirtualMemoryString(name, maxStringLength);

		// Filename is invaild
		if (fileName == null) { return -1; }

		// Try to remove file.
		if (!UserKernel.fileSystem.remove(fileName)){ return -1; }

		return 0;
	}

	protected int handleExit(int status) {
		exitStatus = status;
		exitProperly = true;
		unloadSections();

		// Start at 2 because the first 2 are stdin and stdout  
		for (int i = 2; i < maxFileCount; i++){
			if(files[i] != null){
			 handleClose(i); 
			}
		}

		// Last proccess
		if(--UserProcess.totalPID == 0){
			Kernel.kernel.terminate();	
		}

		uthread.finish();

		return 0;
	}

	protected int handleCreat(int name) {
		String fileName = readVirtualMemoryString(name, maxStringLength);

		// Failed to open file
		if (fileName == null) { return -1; }

		// Open file 
		OpenFile file = UserKernel.fileSystem.open(fileName, true);

		// File does not exist
		if (file == null) { return -1; }

		return addToFiles(file);
	}

	protected int handleExec(int file, int argc, int argv) {
		String fileName = readVirtualMemoryString(file, maxStringLength);

		// Check for invalid file name and proper arguments.
		if (fileName == null || !fileName.endsWith(".coff") || (argc < 0) ) { 
			Lib.debug(dbgProcess, "MMM: The filename is not valid and there are invalid arguments");
			return -1; 
		}

		// Store values.
		String[] args = new String[argc];
		for (int i = 0; i < argc; i++) {
			byte[] buffer = new byte[4];
			if(readVirtualMemory(argv + i * 4, buffer) < 0){ 
				Lib.debug(dbgProcess, "MMM: readVirtualMemory returns invalid number");
				return -1;
			}

			int value = Lib.bytesToInt(buffer, 0, 4);
			args[i] = readVirtualMemoryString(value, maxStringLength);
			if(args[i] == null){ return -1; }

		}

		// Start new child process.
		UserProcess child = newUserProcess();
		childProcesses.put(child.PID, child);
		if(!child.execute(fileName, args)) { return -1; }

		return child.PID;
	}

	protected int handleJoin(int processID, int status) {
		if (!childProcesses.containsKey(processID)) { return -1; }

		UserProcess child = childProcesses.get(processID);

		if (child == null) { return -1; }

		if(!child.exitProperly){ child.uthread.join(); }

		if (child.exitStatus < 0){ return 0; }

		writeVirtualMemory(status, Lib.bytesFromInt(child.exitStatus));

		return 1;
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionSyscall:
			int result = handleSyscall(processor.readRegister(Processor.regV0),
					processor.readRegister(Processor.regA0),
					processor.readRegister(Processor.regA1),
					processor.readRegister(Processor.regA2),
					processor.readRegister(Processor.regA3));
			processor.writeRegister(Processor.regV0, result);
			processor.advancePC();
			break;

		default:
			Lib.debug(dbgProcess, "Unexpected exception: "
					+ Processor.exceptionNames[cause]);
			Lib.assertNotReached("Unexpected exception");
		}
	}


	private int addToFiles(OpenFile openFile){
		for(int index = 0; index < maxFileCount; index++){
			if(files[index] == null){
				return addToFiles(index, openFile);
			}
		}
		return -1;
	}
	private int addToFiles(int index, OpenFile openFile){
		if(index < 0 || index >= maxFileCount){
			return -1;
		}

		// Save the file if space is avaiable.
		if(files[index] == null) {
			files[index] = openFile;
			return index;
		} else {
			return -1;
		}
	}

	public OpenFile getFile(int fileDescriptor) {
		if (fileDescriptor < 0 || fileDescriptor >= maxFileCount)
			return null;
		return files[fileDescriptor];
	}

	/** The program being run by this process. */
	protected Coff coff;

	/** This process's page table. */
	protected TranslationEntry[] pageTable;

	/** The number of contiguous pages occupied by the program. */
	protected int numPages;

	/** The number of pages in the program's stack. */
	protected final int stackPages = 8;

	private int initialPC, initialSP;

	private int argc, argv;

	private static final int pageSize = Processor.pageSize;

	private static final char dbgProcess = 'a';

	private OpenFile files[] = new OpenFile[maxFileCount];

	// SELF DEFINED VARIBALES 
	public boolean exitProperly;
	public int exitStatus;
	public UThread uthread;
	// globally unique positive integer
	public int PID;
	public static int processCount = 0;
	public static int totalPID = 0;
	private HashMap<Integer, UserProcess> childProcesses = new HashMap<Integer, UserProcess>();
	protected static final int maxFileCount = 16;
	protected static final int maxStringLength = 256;
}