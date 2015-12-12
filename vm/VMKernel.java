package nachos.vm;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;
import nachos.vm.*;

import java.util.*;

/**
 * A kernel that can support multiple demand-paging user processes.
 */
public class VMKernel extends UserKernel {

    private static VMProcess dummy1 = null;
    private static final char dbgVM = 'v';
    private static LinkedList freeSwapPages = new LinkedList();
    public static OpenFile swapFile;
    private static final int pageSize = Processor.pageSize;

    /* OUR THINGS */
    private static Condition unpinnedPage;
    private static Iterator i;

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

        unpinnedPage = new Condition(UserKernel.lock);
      	swapFile = ThreadedKernel.fileSystem.open("swap.swap", true);
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
      	super.terminate();
    }

    public static int replacementAlgorithm() {

        int victimPPN = findVictim();
        ProcessHelper victim = mmmo.get(victimPPN);
        VMProcess p = (VMProcess)victim.process;

        if (p.getDirty(victim.vpn) && !p.readOnlyVPN(victim.vpn)) {
            handleSwap(victimPPN);
        }

        p.invalidateVPN(victim.vpn);
        UserKernel.removePPN(victimPPN);
        return victimPPN;
    }

    private static int findVictim() {

        int ppn;
        for (ppn = -1; ppn < 0; ) {
            for(int j = 0; j < mmmo.size(); j++){
                ProcessHelper jPData = mmmo.get(new Integer(j));
                VMProcess p = (VMProcess)jPData.process;
                int vpn = jPData.vpn;

                if (!p.evictableVPN(vpn)) {
                    p.vpnNotRecentlyUsed(vpn);
                } else {
                    ppn = p.getPPN(vpn);
                    j = mmmo.size(); // basically first loop again
                }
            }
            if (ppn < 0){ unpinnedPage.sleep(); }
        }
        return ppn;
    }

    private static int determineSwapPage(int victimPPN) {

        int res;
        ProcessHelper victim = mmmo.get(victimPPN);
        VMProcess p = (VMProcess)victim.process;

        if (!(getParticularSwapPage(p, victim.vpn) != -1 && freeSwapPages.size() > 0)) {
            res = swapFile.length() / pageSize + 1;
            freeSwapPages.add(new Integer(res));
        } else if (getParticularSwapPage(p, victim.vpn) != -1) {
            res = getParticularSwapPage(p, victim.vpn);
        } else {
            res = ((Integer)freeSwapPages.removeFirst()).intValue();
        }

        return res;
    }

    /* give focus for grade*/
    private static void handleSwap(int victimPPN) {

        ProcessHelper victim = mmmo.get(victimPPN);
        VMProcess p = (VMProcess)victim.process;
        byte[] data = new byte[pageSize];
        int page = determineSwapPage(victimPPN);
        int size = page * pageSize;

        if (getParticularSwapPage(p, victim.vpn) == -1) {
            swapper.put(victim, new Integer(victimPPN));
        }

        System.arraycopy(Machine.processor().getMemory(), victimPPN * pageSize, data, 0, pageSize);
        swapFile.write(size, data, 0, pageSize);
    }

    public static int getParticularSwapPage(UserProcess process, int target) {

        i = swapper.keySet().iterator(); 
        while (true) {
            if (!i.hasNext()) {
                return -1;
            } else {
                ProcessHelper next = (ProcessHelper)i.next(); 
                if (next.process == process && next.vpn == target) {
                    return swapper.get(next).intValue();
                }
            }
        }
    }
}
