package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

/* USE THIS CLASS FOR PROCESS INFORMATION THANK YOU BASED JOE */
public class ProcessHelper {
    public UserProcess process;
    public int vpn;

	public ProcessHelper(UserProcess process, int vpn){
		setProcess(process);
		setVPN(vpn);
	}

	public void setVPN(int x) { vpn = x; }

	public void setProcess(UserProcess p) {
		process = p;
	}

	public int getVPN() { return vpn; }
	public UserProcess getProcess() { return process; }
}