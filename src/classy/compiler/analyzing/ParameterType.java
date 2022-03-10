package classy.compiler.analyzing;

import classy.compiler.parsing.Value;

public class ParameterType {
	protected String name;
	protected Type type;
	protected Value defaultValue = null;
	protected boolean implicit = false;

	public ParameterType(String name, Type type) {
		this.name = name;
		this.type = type;
	}
	
	public void setDefaultValue(Value defaulted) {
		this.defaultValue = defaulted;
	}
	public Value getDefaultValue() {
		return defaultValue;
	}
	
	public void setImplicit(boolean implicit) {
		this.implicit = implicit;
	}
	public boolean getImplicit() {
		return implicit;
	}
	
	public String getName() {
		return name;
	}
	public Type getType() {
		return type;
	}

}
