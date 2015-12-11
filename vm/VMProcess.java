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

	private static Lock lock;

	public VMProcess() {
		super();
		lock = new Lock();
	}

	/** 
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>. */
	public void saveState() {
		// Invalidate all TLB entries
		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
			TranslationEntry curEntry = Machine.processor().readTLBEntry(i);
			if(curEntry.valid){
				curEntry.valid = false;
				pageTable[curEntry.vpn].dirty = curEntry.dirty;     
				pageTable[curEntry.vpn].used = curEntry.used;
				VMKernel.invertedTable[curEntry.ppn].entry.used=curEntry.used;  
				// TODO
				//When to sync TLB entry bits back to page table ? 
				// Before evicting a (valid) TLB entry
				// Before flushing a (valid) TLB entry
				// Before evicting a physical page 

				// Need to write entries back to the page table if necessary
				// When the pageTable's valid bit is not updated
				if(pageTable[curEntry.vpn].valid){
					Machine.processor().writeTLBEntry(i, curEntry);
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

		/*
		lock.acquire();
		// initialize page table with an array of TranslationEntry (TE)
		pageTable = new TranslationEntry[numPages];
		for(int i = 0; i < pageTable.length; i++){
			// intialize vpn with no physical mapping and valid false;
			pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
		}

		lock.release();
		return true;
		*/

		return super.loadSections();
	}

	/** * Release any resources allocated by <tt>loadSections()</tt>. */
	protected void unloadSections() {
		super.unloadSections();
	}

	public int allocateTLBEntry(int vpn){
		// Index for the invalid entry 
		int invalidEntry = -1;

		// Go through the TLB 
		for(int i = 0; i < (Machine.processor().getTLBSize()) && (invalidEntry == -1); i++){
			TranslationEntry entry = Machine.processor().readTLBEntry(i);
			if(!entry.valid){
				// This is the invalid entry 
				invalidEntry = i;
			}
		}

		// If there are no invalid entries, we need to evict one page.
		if(invalidEntry == -1){
			invalidEntry = (int)Lib.random();
		}

		return invalidEntry;
	}

	public void updateTLBEntry(int index){
		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);

		TranslationEntry newEntry = new TranslationEntry(pageTable[vpn]);

		TranslationEntry toBeReplaced = Machine.processor().readTLBEntry(index);

		Machine.processor().writeTLBEntry(index, newEntry); 
	}

	public void handleTLBMiss(){
		// System.out.println("inside handleTBLMiss()");

		int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
		int vpn = Processor.pageFromAddress(vaddr);
		VMProcess curProcess = this;

		if(vpn < 0 || vpn >= pageTable.length){
			//VMKernel.terminate();
			// out of bounds 
		}

		// Get page table entry from VPN 
		TranslationEntry entry = pageTable[vpn];

		// Check if valid 
		if(!entry.valid){
			print("NOT VALID");

			// Get ppn
			int ppn = allocatePPN(vpn);

			// If there is a entry in the table already evict one, else place it in
			if(VMKernel.invertedTable[ppn].isSet){
				VMKernel.findInvertSpace(ppn, vpn);
			}
			
			// Place the ppn into the inverted table
			VMKernel.insertInvertEntry(ppn, vpn, curProcess);

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

		// Allocate a TLB entry and get the page to be evicted
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
			// ==== NO FREE PAGES IN THE FREE PAGE LIST ==== // 

			// incase invalid
			ppn = -1;

			VMKernel.memoryLock.acquire();
			ppn = VMKernel.replacementAlgorithm();



			VMKernel.memoryLock.release();
		}
		return ppn;
	}

	public void print(String x){
		System.out.println(x);
	}

/*
	protected int pinVirtualPage(int vpn, boolean userWrite)
	{
		return 2;
	}
	protected void unpinVirtualPage(int vpn){
	}
*/
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
