package classy.compiler.parsing;

import classy.compiler.analyzing.Variable;
import classy.compiler.lexing.Token;

public class Reference extends Subexpression {
	protected String varName;
	protected Variable linkedTo;
	protected Value arguments;
	
	public Reference(Value parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!(it.match(Token.Type.IDENTIFIER, end) || it.match(Token.Type.SELF, end)))
			throw new ParseException("Missing identifier token in reference! ", it.token(),
					" found instead.");
		this.varName = it.token().getValue();
		it.next(end);
	}
	
	public String getVarName() {
		return varName;
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
	
	public void setArgument(Value val) {
		this.arguments = val;
	}
	public Value getArgument() {
		return this.arguments;
	}
	
	@Override
	public String pretty(int indents) {
		if (arguments == null)
			return varName;
		else {
			if (arguments.getSubexpressions().get(0) instanceof Tuple)
				return varName + arguments.pretty(indents);
			return varName + "(" + arguments.pretty(indents) + ")";
		}
	}
	
	public Reference clone() {
		Reference cloned = new Reference(parent);
		cloned.varName = varName;
		if (arguments != null)
			cloned.arguments = arguments.clone();
		cloned.linkedTo = linkedTo; // referencing the same variable, so no cloning here
		if (linkedTo != null)
			linkedTo.addRef(cloned);
		return cloned;
	}

}
