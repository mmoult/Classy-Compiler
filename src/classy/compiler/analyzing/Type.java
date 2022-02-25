package classy.compiler.analyzing;

import java.util.HashSet;
import java.util.Set;

public class Type {
	// Default available types
	public static Type any = new Type();
	public static Type number = new Type("Num");
	public static Type bool = new Type("Bool", number);
	
	// Nominal type
	protected String name = null;
	protected Type[] parents;
	// Function type
	protected Type output = null;
	protected Type[] inputs = null;
	
	private Type() {
		// for creating any. All other types get a default parent in construction
		name = "Any";
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
	
	/**
	 * Returns whether this type is a subclass or descendant of the specified parent type.
	 * Internally uses {@link #isa(Type, Set)} to avoid redundant checks.
	 * @param parent the other type that this may be a descendant of
	 * @return whether this type is indeed a descendant of the given parent
	 */
	public boolean isa(Type parent) {
		Set<Type> checked = new HashSet<>();
		return isa(parent, checked);
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
	 * Alternatively, there could be a common ancestor
	 * @param other the other type
	 * @return the intersection of the two types, null if none
	 */
	protected Type intersect(Type other) {
		Set<Type> ones = new HashSet<>();
		Set<Type> twos = new HashSet<>();
		
		// We go through and try to find any intersection between ones, which
		//  is all the ancestors of t1, and twos, all the ancestors of t2
		// t1 goes first
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
