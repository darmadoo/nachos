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

    /* OURS */
    public boolean[] pinned;
    public boolean[] lRU;

	/**
	 * Allocate a new VM kernel.
	 */
	public VMProcess() {
		super();
	}

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {

		for (int i = 0; i < Machine.processor().getTLBSize(); i++) {
		 	TranslationEntry curEntry = Machine.processor().readTLBEntry(i);
		    pageTable[curEntry.vpn].dirty = curEntry.dirty;
		    pageTable[curEntry.vpn].used = curEntry.used;
		    curEntry.valid = false;
		    Machine.processor().writeTLBEntry(i, curEntry);
		}
    }

    protected int pinVirtualPage(int vpn, boolean userWrite) {

		if (vpn < 0 || vpn >= pageTable.length) { return -1; }

		TranslationEntry entry = pageTable[vpn];

		if (!entry.valid || entry.vpn != vpn || (userWrite && entry.readOnly)) {
			return -1;
		} else if (userWrite) {
		    entry.dirty = true;
			return entry.ppn;
	    } else {
			pinned[vpn] = true;
			entry.used = true;
			lRU[vpn] = true;
			return entry.ppn;
	    }
    }
	    
    protected void unpinVirtualPage(int vpn) {
    	/* it's so easy when you read the instructions */
		pinned[vpn] = false;
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
		//super.restoreState();
    }

    /**
     * Initializes page tables for this process so that the executable can be
     * demand-paged.
     *
     * @return	<tt>true</tt> if successful.
     */
    protected boolean loadSections() {
		UserKernel.lock.acquire();

		pageTable = new TranslationEntry[numPages];

		int i = 0;
        pinned = new boolean[numPages];
        lRU = new boolean[numPages];
		while (i < numPages) {
		    pageTable[i] = new TranslationEntry(i, -1, false, false, false, false);
            pinned[i] = false;
            lRU[i] = false;
            i++;
		}

		UserKernel.lock.release();

		return true;
    }

    protected void unloadSections() {

    	/* should be done */
        for (int i = 0; i < pageTable.length; i++) {
		    if (pageTable[i] != null) {
		    	if (pageTable[i].valid) {
			        UserKernel.freePages.add(new Integer(pageTable[i].ppn));
		    	}
		    }
		}
    }

    public boolean evictableVPN(int vpn){
    	return !pinned[vpn] && !lRU[vpn];
    }

    public boolean getDirty(int vpn){
		return pageTable[vpn].dirty;
    }

    public int getPPN(int vpn){
		return pageTable[vpn].ppn;
    }

    private int determineEviction() {
		// no eviction; eviction
		if (VMKernel.freePages.size() > 0) {
		    return ((Integer)VMKernel.freePages.removeFirst()).intValue();
		} else {
		    return VMKernel.replacementAlgorithm();
		}
    }

    private void handleCoff(CoffSection s, int vpn) {

	    for (int i = 0; i < s.getLength(); i++) {
			if (s.isReadOnly() && (s.getFirstVPN() + i) == vpn) {
			    pageTable[vpn].readOnly = s.isReadOnly();
			    s.loadPage(i, pinVirtualPage(vpn, false));
			    unpinVirtualPage(vpn);
			    return;
			}
	    }
    }

    public void vpnNotRecentlyUsed(int x){
		lRU[x] = false;
    }

    protected void handleFault(int vpn){

    	int ppn = determineEviction();
		pageTable[vpn] = new TranslationEntry(vpn, ppn, true, false, false, false);
		int numCoffSections = coff.getNumSections();
		byte[] data;

		VMKernel.addPPN(pageTable[vpn].ppn, vpn, (UserProcess)this);

		for (int j = 0; j < numCoffSections; j++) {
		    handleCoff(coff.getSection(j), vpn);
		}

		int particularSwapPage = VMKernel.getParticularSwapPage((UserProcess)this, vpn);
        if (particularSwapPage >= 0) {
		    data = new byte[pageSize];
		    VMKernel.swapFile.read(particularSwapPage * pageSize, data, 0, pageSize);
		    System.arraycopy(data, 0, Machine.processor().getMemory(), ppn * pageSize, pageSize);
        }
    }

    public void handleTLBMiss() {

    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
	        case Processor.exceptionTLBMiss:

			    int vaddr = Machine.processor().readRegister(Processor.regBadVAddr);
			    int vpn = Processor.pageFromAddress(vaddr);

			    UserKernel.lock.acquire();

			    if (pageTable[vpn] == null || !(pageTable[vpn].valid)) {
					handleFault(vpn);
			    }

			    UserKernel.lock.release();

			    Machine.processor().writeTLBEntry((int)(Machine.processor().getTLBSize() * Math.random()), pageTable[vpn]);

	            break;
			default:
			    super.handleException(cause);
			    break;
		}
    }
}
