package classy.compiler.analyzing;

public class Type {
	// Default available types
	public static Type any = new Type();
	static { // give any the parent of any. 
		any.parents = new Type[] {any}; 
	}
	public static Type number = new Type("Num");
	
	// Nominal type
	protected String name = null;
	protected Type[] parents;
	// Function type
	protected Type output = null;
	protected Type[] inputs = null;
	
	private Type() {
		// for creating any. All other types get a default parent in construction
	}
	public Type(String name) {
		this.name = name;
		parents = new Type[]{any};
	}
	public Type(String name, Type...parents) {
		this(name);
		this.parents = parents;
	}
	
	public Type(Type output, Type...inputs) {
		this.output = output;
		this.inputs = inputs;
	}
	
	public boolean isa(Type parent) {
		
		return false;
	}
	
	public boolean isFunction() {
		return output != null;
	}
	
	public boolean equals(Object other) {
		if (this == other)
			return true;
		if (other == null)
			return false;
		if (!(other instanceof Type))
			return false;
		Type o = (Type) other;
		// Here we branch. Both need to be either function or nominal.
		if (o.name != null && name != null) {
			// We can compare on the name since the checker will verify that
			//  no two distinct types have the same name
			return o.name.equals(name);
		}else if (o.output != null && output != null) {
			// We need to assert that all inputs and outputs match to be the same type here
			if (!o.output.equals(output))
				return false;
			for (int i = 0; i < inputs.length; i++) {
				if (!inputs[i].equals(o.inputs[i]))
					return false;
			}
			return inputs.length == o.inputs.length;
		}
		// Otherwise, this is not exact
		return false;
	}
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer("type ");
		if (!isFunction()) {
			buf.append("\"");
			buf.append(name);
			buf.append("\"");
		}else {
			buf.append("(");
			boolean first = true;
			for (Type input: inputs) {
				if (first)
					first = false;
				else
					buf.append(", ");
				buf.append(input);
			}
			buf.append(" -> ");
			buf.append(output);
			buf.append(")");
		}
		return buf.toString();
	}
}
