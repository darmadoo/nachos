package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';

	private static int currentHand = 0;

	public VMProcess() {
		super();
	}

	/** 
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>. */
	public void saveState() {
		// Invalidate all TLB entries
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if(entry.valid){
				entry.valid = false;
				pageTable[entry.vpn].dirty = entry.dirty;     
				pageTable[entry.vpn].used = entry.used;

				// TODO
				//When to sync TLB entry bits back to page table ? 
				// Before evicting a (valid) TLB entry
				// Before flushing a (valid) TLB entry
				// Before evicting a physical page 

				// Need to write entries back to the page table if necessary
				// When the pageTable's valid bit is not updated
				if(pageTable[entry.vpn].valid){
					Machine.processor().writeTLBEntry(i, entry);
				}
			}
			
			

		}
		super.saveState();
	}

	/** 
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>. */
	public void restoreState() {
		//super.restoreState();
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return super.loadSections();
	}

	/** * Release any resources allocated by <tt>loadSections()</tt>. */
	protected void unloadSections() {
		super.unloadSections();
	}

	public int allocateTLBEntry(int vpn){
		// print("inside allocateTLBEntry()");
		// Index for the invalid entry 
		int invalidEntry = -1;

		// print("the size is " + Machine.processor().getTLBSize());

		// Go through the TLB 
		for(int i = 0; i < (Machine.processor().getTLBSize()) && (invalidEntry == -1); i++){
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if(!entry.valid){
				// print("it stopped at " + i);
				// This is the invalid entry 
				invalidEntry = i;
			}
		}

		// If there are no invalid entries, we need to evict one page.
		if(invalidEntry == -1){
			invalidEntry = (int)Lib.random();
			// print("the new invalid entry is " + invalidEntry);
		}

		// print("index is " + invalidEntry);
		//Machine.processor().writeTLBEntry(invalidEntry, pageTable[vpn]);
		return invalidEntry;
	}

	public void updateTLBEntry(int index){
		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);

		Machine.processor().writeTLBEntry(index,pageTable[vpn]); 
	}

	public void handleTLBMiss(){

		// System.out.println("inside handleTBLMiss()");

		// Get page table entry from VPN 
		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);

		if(vpn < 0 || vpn >= pageTable.length){
			//VMKernel.terminate();
			// out of bounds 
		}

		// for(int i = 0; i < pageTable.length; i++){
		// 	print("Page table " + i + " is " + pageTable[i].vpn + " and " + pageTable[i].ppn);
		// }
		// System.out.println("ACHOO " + vpn);
		TranslationEntry entry = pageTable[vpn];

		// Check if valid 
		if(!entry.valid){
			print("NOT VALID");

			// Get ppn
			int ppn = allocatePPN(vpn);

			// Check if the page is dirty 
			if(pageTable[vpn].dirty){
				// if dirty, swap 
				VMKernel.getSwapPage(pageTable[vpn].ppn,ppn);
			}

			pageTable[vpn].ppn = ppn;
			pageTable[vpn].valid = true;
			pageTable[vpn].used = true;
			pageTable[vpn].vpn = vpn;
		}

		// Allocate a TLB entry 
		int index = allocateTLBEntry(vpn);

		// Update TLB entry 
		updateTLBEntry(index);

	}

	public int allocatePPN(int vpn){
		int ppn;
		// if free page is not empty, get a ppn
		if(!(VMKernel.freePages).isEmpty()){
			print("Shadow Fiend");
			// allocate physical page 
			ppn = ((Integer)VMKernel.freePages.removeFirst()).intValue();
		}
		else{
			// incase invalid
			ppn = -1;

			VMKernel.memoryLock.acquire();
			// Fill the inverted table 
			fillInvertedTable();
			ppn = replacementAlgorithm();

			VMKernel.memoryLock.release();
		}
		return ppn;
	}

	public void fillInvertedTable(){
		for(int i = 0; i < Machine.processor().getTLBSize(); i++){
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if(entry.vpn == pageTable[entry.vpn].vpn){
				if(entry.valid){
	                VMKernel.invertedTable[entry.ppn].pageTable[entry.vpn].used = entry.used;
					VMKernel.invertedTable[entry.ppn].pageTable[entry.vpn].dirty = entry.dirty;
				}
			}
		}
	}

	public int replacementAlgorithm(){
		int ptr = currentHand;

		int pnum = VMKernel.invertedTable.length;
		VMProcess curProcess = VMKernel.invertedTable[ptr];
		TranslationEntry curEntry = curProcess.pageTable[ptr];

		while(curEntry.used == true){
			curEntry.used = false;
			ptr = (ptr + 1) % pnum;
		}

		int toEvict = ptr;
		ptr = (ptr + 1) % pnum;

		return toEvict;
	}

	public void print(String x){
		System.out.println(x);
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause the user exception that occurred.
	 */
	public void handleException(int cause) {
		int offendingAddress;
		int virtualPageNumber;
		Processor processor = Machine.processor();

		switch (cause) {
			/*
	        case Processor.exceptionTLBMiss:
                offendingAddress = Machine.processor().readRegister(Processor.regBadVAddr);
                virtualPageNumber = Processor.pageFromAddress(offendingAddress);
                if (!addressIsValid(virtualPageNumber)) {
	                initializePage(VMKernel.getAvailablePage(this, virtualPageNumber), virtualPageNumber);
	            }
	            break;
	            */
            case Processor.exceptionTLBMiss:            
   				handleTLBMiss();            
   				break;   

			default:
				super.handleException(cause);
				break;
		}
	}

    public boolean addressIsValid(int virtualPageNumber) {
    	return pageTable[virtualPageNumber].valid;
    }
}
