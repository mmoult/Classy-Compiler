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
	protected Type type;
	
	protected List<Reference> references;	
	protected boolean overridden = true;
	protected List<Variable> overrides;
	
	
	public Variable(String name, Value value, NameBinding source) {
		this.name = name;
		this.value = value;
		if (source != null)
			source.setSourced(this);
		this.source = source;
		this.overrides = new ArrayList<>();
		
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
	
	public boolean isOverridden() {
		return overridden;
	}
	public List<Variable> getOverrides() {
		return overrides;
	}
	public void setOverrides(Variable superImpl) {
		// If superImpl is itself overriding something, then this should override that,
		//  not superImpl. We override the top parent
		while (!superImpl.overridden)
			superImpl = superImpl.overrides.get(0);
		// We found the top parent
		superImpl.overrides.add(this);
		// If we were overridden, then all need to override the new top
		if (this.overridden) {
			this.overridden = false;
			for (Variable overridesUs: this.overrides) {
				overridesUs.overrides.remove(this);
				overridesUs.overrides.add(superImpl);
			}
		}
		this.overrides.add(superImpl);
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
