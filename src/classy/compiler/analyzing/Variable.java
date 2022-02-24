package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.parsing.NameBinding;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Value;

public class Variable {
	protected String name;
	protected Value value;
	protected NameBinding source;
	protected List<Reference> references;
	protected Type type;
	
	public Variable(String name, Value value, NameBinding source) {
		this.name = name;
		this.value = value;
		if (source != null)
			source.setSourced(this);
		this.source = source;
		
		references = new ArrayList<>();
	}
	
	public String getName() {
		return name;
	}
	public Value getValue() {
		return value;
	}
	public NameBinding getSource() {
		return source;
	}
	public List<Reference> getRef() {
		return references;
	}
	
	public void addRef(Reference referenced) {
		references.add(referenced);
	}
	
	public void setType(Type type) {
		this.type = type;
	}
	public Type getType() {
		return type;
	}
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append("variable \"");
		buf.append(name);
		buf.append("\"");
		return buf.toString();
	}

}
