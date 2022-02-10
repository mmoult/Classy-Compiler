package classy.compiler.analyzing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import classy.compiler.parsing.Reference;

/**
 * A class to represent a stack frame, used as a block
 * frame or a function frame.
 */
public class Frame {
	protected boolean fxFrame;
	protected Map<String, Variable> locals;
	protected Set<Reference> externalities;
	
	public Frame(boolean fxFrame) {
		this.fxFrame = fxFrame;
		locals = new HashMap<>();
		externalities = new HashSet<>();
	}
	
	public boolean isFunction() {
		return fxFrame;
	}
	
	public void allocate(String varName, Variable set) {
		locals.put(varName, set);
	}
	
	public Variable defined(String varName) {
		return locals.get(varName);
	}
	
	public void addExternality(Reference ref) {
		externalities.add(ref);
	}
	
}
