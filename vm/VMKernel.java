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
		makeInvertedTable();
		processLock = new Lock();
		unpinnedPage = new Condition(processLock);
	
		memoryLock = new Lock();	
		for (int ppn=0; ppn<Machine.processor().getNumPhysPages(); ppn++){
		    freePages.add(new Integer(ppn));
	    }
	}

	public void makeInvertedTable(){
		invertedTable = new frame[Machine.processor().getNumPhysPages()];

		for(int i = 0; i < Machine.processor().getNumPhysPages(); i++){
			invertedTable[i] = new frame(new TranslationEntry(), null, false, false);
		}
	}

	public static void getSwapPage(int spn, int ppn){
		int saddr = spn * pageSize;
		byte[] memory = Machine.processor().getMemory();
		int paddr = ppn * pageSize;

		swapFile.read(saddr, memory, paddr, pageSize);
		freeSwapPages.add(spn);

		return;
	}

	public static int replacementAlgorithm(){
		int ptr = currentHand;
		int toEvict;
		boolean allPinned = false;

		while(true){
			if(invertedTable[ptr].entry.used){
				invertedTable[ptr].entry.used = false;
				ptr++;
			}
			else{
				if(invertedTable[ptr].pinned){

				}
				else{
					currentHand = ptr;
					ptr++;
					break;
				}
			}

			// Corner case where all pages are pinned 
			if(!allPinned && ptr > Machine.processor().getNumPhysPages()){
				// need a condition variable to sleep
				unpinnedPage.sleep();
				// Need to call condVar.wake() at unpinPage();
			}
		}

		return currentHand;
	}

	public static void insertInvertEntry(int ppn, int vpn, VMProcess cur){
		frame temp = invertedTable[ppn];

		temp.entry.ppn = ppn;
		temp.entry.vpn = vpn;
		temp.entry.valid = true;
		temp.entry.used = true;


		temp.vmproc = cur;
		temp.isSet = true;
	}

	public static void findInvertSpace(int ppn, int vpn){
		int oldVpn = invertedTable[ppn].entry.vpn;
		VMProcess oldProc = invertedTable[ppn].vmproc;

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

    public static frame[] invertedTable;

   	private static int currentHand = 0;

   	private static Condition unpinnedPage;
}
