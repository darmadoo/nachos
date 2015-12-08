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

	public VMProcess() {
		super();
	}

	/** * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>. */
	public void saveState() {
		super.saveState();
	}

	/** * Restore the state of this process after a context switch. Called by
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
                offendingAddress = Machine.processor().readRegister(Processor.regBadVAddr);
                virtualPageNumber = Processor.pageFromAddress(offendingAddress);
                if (!addressIsValid(virtualPageNumber)) {
	                initializePage(VMKernel.getAvailablePage(this, virtualPageNumber), virtualPageNumber);
	            }
	            break;
			default:
				super.handleException(cause);
				break;
		}
	}

    public boolean addressIsValid(int virtualPageNumber) {
    	return false;
    }
}
