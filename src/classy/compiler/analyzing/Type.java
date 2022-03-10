package classy.compiler.analyzing;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import classy.compiler.parsing.TypeDefinition;

public class Type {
	// Default available types
	public static Type Any = new Type();
	public static Type Int = new Type("Int");
	public static Type Bool = new Type("Bool");
	
	static {
		// We need to give some attributes to our built-in types
		Variable print = new Variable("..print", null, null);
		print.type = new Type(null, new ParameterType("this", Type.Any));
		Any.methods.put("..print", print);
		Variable printi = new Variable("..print", null, null);
		printi.type = new Type(null, new ParameterType("this", Type.Int));
		printi.setOverrides(print);
		Int.methods.put("..print", printi);
		Variable printb = new Variable("..print", null, null);
		printb.type = new Type(null, new ParameterType("this", Type.Bool));
		printb.setOverrides(print);
		Bool.methods.put("..print", printb);
	}
	
	// Nominal type
	protected String name = null;
	protected Type[] parents;
	protected Map<String, Variable> fields;
	protected Map<String, Variable> methods;
	// Function type
	protected Type output = null;
	protected ParameterType[] inputs = null;
	
	protected TypeDefinition source = null;
	
	private Type() {
		// for creating any. All other types get a default parent in construction
		name = "Any";
		fields = new HashMap<>();
		methods = new HashMap<>();
	}
	public Type(String name) {
		this.name = name;
		fields = new HashMap<>();
		methods = new HashMap<>();
		parents = new Type[]{Any};
	}
	public Type(String name, Type...parents) {
		this(name);
		this.parents = parents;
	}
	
	public Type(Type output, ParameterType...inputs) {
		this.output = output;
		this.inputs = inputs;
	}
	
	/**
	 * Returns whether this type is a subclass or descendant of the specified parent type.
	 * Internally uses {@link #isa(Type, Set)} to avoid redundant checks.
	 * @param parent the other type that this may be a descendant of
	 * @return whether this type is indeed a descendant of the given parent
	 */
	public boolean isa(Type parent) {
		if (!isFunction() && !parent.isFunction()) {
			Set<Type> checked = new HashSet<>();
			return isa(parent, checked);			
		}else if (isFunction() && parent.isFunction()) {
			// For a function, "is a" is more complicated
			// We need to verify that each of the parameters are subtypes of their counterparts
			//  and that the return is a subtype of the return
			boolean is = this.output.isa(parent.output);
			if (!is)
				return false;
			
			// An interesting detail is that if the parent has default parameters, this could
			//  be an instance with fewer arguments given.
			int j = 0;
			for (int i = 0; i < parent.inputs.length; i++) {
				ParameterType pi = parent.inputs[i];
				ParameterType ji = j < inputs.length? inputs[j] : null;
				
				if (ji == null) {
					if (pi.getDefaultValue() != null) {
						j++;
						continue;
					}
					return false;
				}else if (ji.type.isa(pi.type)) {
					j++; // accepted and move on to next
				}else if (pi.getDefaultValue() != null) {
					// continue, but don't move on for this's inputs
				}else
					return false;
			}
			// True if there are no extra parameters
			return j >= inputs.length;
		}
		
		// If one is a function and the other is not, then they cannot be related unless the
		//  parent is any
		return parent.equals(Type.Any);
	}
	/**
	 * The recursive call of {@link #isa(Type)}. Uses a set of checked types to avoid
	 * double checking the same inheritance trees.
	 * @param parent the type to check if this is a descendant of
	 * @param checked the set of types that have already been checked
	 * @return whether this type is indeed a desendant of the given parent
	 */
	protected boolean isa(Type parent, Set<Type> checked) {
		if (this.equals(parent)) // if they are of same type
			return true;
		if (checked.contains(this))
			return false; // we already checked this and all supers
		
		checked.add(this);
		if (parents == null)
			return false;
		for (Type myParent: parents) {
			if (myParent.isa(parent, checked))
				return true;
		}
		return false;
	}
	
	/**
	 * Finds how the two types match. This may be a subclass of other or reverse.
	 * Alternatively, there could be a common ancestor between the two.
	 * @param other the other type
	 * @return the intersection of the two types, null if none
	 */
	protected Type intersect(Type other) {
		Set<Type> ones = new HashSet<>();
		Set<Type> twos = new HashSet<>();
		
		// We go through and try to find any intersection between ones, which
		//  is all the ancestors of this, and twos, all the ancestors of other
		ones.add(this);
		if (ones.contains(other))
			return other;
		twos.add(other);
		
		boolean changed;
		Set<Type> temp = new HashSet<>();
		do {
			changed = false;
			for (Type one: ones) {
				for (Type p1: one.parents) {
					if (twos.contains(p1))
						return p1;
					changed &= temp.add(p1);
				}
			}
			ones.addAll(temp);
			temp.clear();
			
			for (Type two: twos) {
				for (Type p2: two.parents) {
					if (ones.contains(p2))
						return p2;
					changed &= temp.add(p2);
				}
			}
			twos.addAll(temp);
			temp.clear();
		}while (changed);
		
		return null;
	}
	
	public boolean isFunction() {
		return name == null;
	}
	
	public String getName() {
		return name;
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
	
	public void setSource(TypeDefinition source) {
		this.source = source;
	}
	public TypeDefinition getSource() {
		return source;
	}
	
	public Type[] getParents() {
		return parents;
	}
	public Map<String, Variable> getFields() {
		return fields;
	}
	public Map<String, Variable> getMethods() {
		return methods;
	}
	
	public Type getOutput() {
		return output;
	}
	public ParameterType[] getInputs() {
		return inputs;
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
			for (ParameterType input: inputs) {
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
	
	public static class Stub extends Type {
		public Stub(String name) {
			super('-' + name);
		}
	}
}
