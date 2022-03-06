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
	protected String functionName;
	protected Map<String, Variable> locals;
	protected Map<String, Type> types;
	protected Set<Reference> externalities;
	
	public Frame(String fxName) {
		this.fxFrame = fxName != null;
		this.functionName = fxName;
		locals = new HashMap<>();
		types = new HashMap<>();
		// We don't need to save type externalities, since types are never passed directly.
		// Thus, this externality set only saves variables
		externalities = new HashSet<>();
	}
	
	public boolean isFunction() {
		return fxFrame;
	}
	
	public void allocate(Variable set) {
		locals.put(set.name, set);
	}
	public void makeType(Type set) {
		types.put(set.name, set);
	}
	
	public Variable varDefined(String varName) {
		return locals.get(varName);
	}
	public Type typeDefined(String typeName) {
		return types.get(typeName);
	}
	
	public void addExternality(Reference ref) {
		externalities.add(ref);
	}
	
	public String getFunctionName() {
		return functionName;
	}
	
}
