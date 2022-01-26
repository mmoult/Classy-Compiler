package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Value;

public class Variable {
	protected String name;
	protected Value value;
	protected Assignment source;
	protected List<Reference> references;
	
	public Variable(String name, Value value, Assignment source) {
		this.name = name;
		this.value = value;
		this.source = source;
		
		references = new ArrayList<>();
	}
	
	public String getName() {
		return name;
	}
	public Value getValue() {
		return value;
	}
	public Assignment getSource() {
		return source;
	}
	public List<Reference> getRef() {
		return references;
	}
	
	public void addRef(Reference referenced) {
		references.add(referenced);
	}

}
