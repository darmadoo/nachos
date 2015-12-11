package nachos.vm;

import nachos.machine.*;

public class frame{
	
	public frame(){	
		this.entry = null;
		this.vmproc = null;
		this.pinned = false;
		this.isSet = false;
	}

	public frame(TranslationEntry e, VMProcess v, boolean p, boolean set){
		entry = e;
		pinned = p;
		vmproc = v;
		isSet = set;
	}

	public TranslationEntry entry;
	public boolean pinned;
	public boolean isSet;
	public VMProcess vmproc; 
}