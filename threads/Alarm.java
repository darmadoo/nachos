package nachos.threads;
import java.util.PriorityQueue;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */
public class Alarm {

	private class Pair implements Comparable<Pair>{
		public KThread kthread;
		public long timeStamp;

		public Pair(KThread kthread, long timeStamp){
			this.kthread = kthread;
			this.timeStamp = timeStamp;
		}

		@Override
		public int compareTo(Pair that) {
			if (this.timeStamp < that.timeStamp) return -1;
        	if (this.timeStamp > that.timeStamp) return 1;
        	return 0;
			// return Long.signum(wakeTime - that.wakeTime);
		}
	}

	private PriorityQueue<Pair> priorityQueue = new PriorityQueue<Pair>();

	 
	/**
	 * Allocate a new Alarm. Set the machine's timer interrupt handler to this
	 * alarm's callback.
	 * 
	 * <p>
	 * <b>Note</b>: Nachos will not function correctly with more than one alarm.
	 */
	public Alarm() {
		Machine.timer().setInterruptHandler(new Runnable() {
			public void run() {
				timerInterrupt();
			}
		});
	}

	/**
	 * The timer interrupt handler. This is called by the machine's timer
	 * periodically (approximately every 500 clock ticks). Causes the current
	 * thread to yield, forcing a context switch if there is another thread that
	 * should be run.
	 */
	public void timerInterrupt() {

		// Begin interrupt 
		boolean intStatus = Machine.interrupt().disable();

		//KThread.currentThread().yield();

		// Grab top element from queue, but dont remove it.
		Pair pair = priorityQueue.peek();

		// Only work with the queue if its not empty
		if(!priorityQueue.isEmpty()){

			// Get current time and compare to the top element of the queue.
			long x = Machine.timer().getTime();
			if( x >= pair.timeStamp){
				// Remove top element from queue.
				priorityQueue.poll();

				// Read up the thread.
				pair.kthread.ready();
			}
		}

		// End interrupt
		Machine.interrupt().restore(intStatus);
		KThread.yield();     
	}

	/**
	 * Put the current thread to sleep for at least <i>x</i> ticks, waking it up
	 * in the timer interrupt handler. The thread must be woken up (placed in
	 * the scheduler ready set) during the first timer interrupt where
	 * 
	 * <p>
	 * <blockquote> (current time) >= (WaitUntil called time)+(x) </blockquote>
	 * 
	 * @param x the minimum number of clock ticks to wait.
	 * 
	 * @see nachos.machine.Timer#getTime()
	 */
	public void waitUntil(long x) {
		
		// Begin interrupt 
		boolean intStatus = Machine.interrupt().disable();

		// Grab timestamp
		long wakeTime = Machine.timer().getTime() + x;

		// Wrap current thread and timestamp into a pair object and add it to the priotity queue
		Pair p = new Pair(KThread.currentThread(), wakeTime);
		priorityQueue.add(p);
		KThread.sleep();

		// End interrupt
		Machine.interrupt().restore(intStatus);

		// Old code
		//while (wakeTime > Machine.timer().getTime())
		//	KThread.yield();
		
	}

	// Test Case
	public static void selfTest() {
	    KThread t1 = new KThread(new Runnable() {
	        public void run() {
	            long time1 = Machine.timer().getTime();
	            int waitTime = 20000;
	            System.out.println("Thread calling wait at time:" + time1);
	            ThreadedKernel.alarm.waitUntil(waitTime);
	            System.out.println("Thread woken up after:" + (Machine.timer().getTime() - time1));
	            Lib.assertTrue((Machine.timer().getTime() - time1) > waitTime, " thread woke up too early.");
	            
	        }
	    });
	    t1.setName("T1");
	    t1.fork();
	    t1.join();

		for(int x = 0; x < 5; x++){
			KThread t2 = new KThread(new Runnable() {
		        public void run() {
		            long time1 = Machine.timer().getTime();
		            
		            int Max = 10000000;
		            int Min = 0;
		            int waitTime = Min + (int)(Math.random() * ((Max - Min) + 1));

		            System.out.println("      Thread calling wait at time:" + time1);
		            ThreadedKernel.alarm.waitUntil(waitTime);

		            long currentTime = (Machine.timer().getTime() - time1);
		            boolean status = (currentTime > waitTime);
		            System.out.println(status +" - Thread woken up after: " + currentTime +  " WaitTime: " + waitTime);
		            Lib.assertTrue((Machine.timer().getTime() - time1) > waitTime, " thread woke up too early.");
		            System.out.println("");
		            
		        }
	    	});
		    t2.setName("T1");
		    t2.fork();
		    t2.join();
		}

	}

}
