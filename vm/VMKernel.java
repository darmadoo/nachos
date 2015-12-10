package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.LinkedList;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {
	/**
	 * Allocate a new VM kernel.
	 */
	public VMKernel() {
		super();
	}

	/**
	 * Initialize this kernel.
	 */
	public void initialize(String[] args) {
		super.initialize(args);

		swapFile = ThreadedKernel.fileSystem.open("swap.swp", true); 
		invertedTable = new VMProcess[Machine.processor().getNumPhysPages()];
		processLock = new Lock();
	
		memoryLock = new Lock();	
		for (int ppn=0; ppn<Machine.processor().getNumPhysPages(); ppn++){
		    freePages.add(new Integer(ppn));
	    }
	}

	public static void getSwapPage(int pagePPN, int ppn){
		int saddr = pagePPN * pageSize;
		byte[] memory = Machine.processor().getMemory();
		int paddr = ppn * pageSize;

		swapFile.read(saddr, memory, paddr, pageSize);
		freeSwapPages.add(pagePPN);

		return;
	}

	/**
	 * Test this kernel.
	 */
	public void selfTest() {
		super.selfTest();
	}

	/**
	 * Start running user programs.
	 */
	public void run() {
		super.run();
	}

	/**
	 * Terminate this kernel. Never returns.
	 */
	public void terminate() {
		swapFile.close();
		ThreadedKernel.fileSystem.remove("swap.swp");
		super.terminate();
	}

	// dummy variables to make javac smarter
	private static VMProcess dummy1 = null;

	private static final char dbgVM = 'v';

	public static LinkedList freePages = new LinkedList();

    /** Guards access to process data: lists, exit status tables, etc. */
	public static Lock processLock;

	/** Guards access to the physical page free list. */
    public static Lock memoryLock;

    // The swap file 
    private static OpenFile swapFile;

  	private static LinkedList freeSwapPages = new LinkedList();

    private static final int pageSize = Processor.pageSize;

    public static VMProcess[] invertedTable;


}
