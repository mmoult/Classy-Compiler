package classy.compiler.parsing;

import classy.compiler.analyzing.Type;
import classy.compiler.analyzing.Variable;
import classy.compiler.lexing.Token;

public class Reference extends Subexpression {
	protected String varName;
	protected Variable linkedTo;
	protected Value arguments;
	protected Type type;
	
	protected boolean member = false;
	protected Value location; // where this is a reference in. Only applicable if member==true
	
	
	public Reference(Value parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// If this is a self reference, it cannot be a member
		if (!it.match(Token.Type.SELF, end)) {
			if (it.match(Token.Type.PERIOD, end)) {
				member = true;
				it.next(end);
			}
			
			if (!(it.match(Token.Type.IDENTIFIER, end)))
				throw new ParseException("Missing identifier token in reference! ", it.token(),
						" found instead.");
		}
		
		startToken = it.token();
		this.varName = startToken.getValue();
		it.next(end);
	}
	
	public String getVarName() {
		return varName;
	}
	public void setVarName(String varName) {
		this.varName = varName;
	}
	
	@Override
	public boolean isLink() {
		return false;
	}
	
	public Value getParent() {
		return parent;
	}
	
	public void setLinkedTo(Variable var) {
		this.linkedTo = var;
	}
	public Variable getLinkedTo() {
		return linkedTo;
	}
	
	public boolean isMember() {
		return member;
	}
	public void setMember(boolean member) {
		this.member = member;
	}
	public Value getLocation() {
		return location;
	}
	public void setLocation(Value location) {
		this.location = location;
	}
	
	public void setArgument(Value val) {
		this.arguments = val;
	}
	public Value getArgument() {
		return this.arguments;
	}
	
	public void setType(Type type) {
		this.type = type;
	}
	public Type getType() {
		return type;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer();
		if (location != null && member)
			buf.append(location.pretty(indents));
		if (member)
			buf.append('.');
		buf.append(varName);
		
		if (arguments != null) {
			if (arguments.getSubexpressions().get(0) instanceof Tuple)
				buf.append(arguments.pretty(indents));
			else {
				buf.append('(');
				buf.append(arguments.pretty(indents));
				buf.append(')');
			}
		}
		return buf.toString();
	}
	
	public Reference clone() {
		Reference cloned = new Reference(parent);
		cloned.varName = varName;
		if (arguments != null)
			cloned.arguments = arguments.clone();
		cloned.linkedTo = linkedTo; // referencing the same variable, so no cloning here
		if (linkedTo != null)
			linkedTo.addRef(cloned);
		if (cloned.location != null)
			cloned.location = location.clone();
		cloned.member = member;
		cloned.type = type;
		return cloned;
	}

}
