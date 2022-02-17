package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Reference extends Subexpression {
	protected String varName;
	protected Assignment linkedTo;
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
	
	public void setLinkedTo(Assignment asgn) {
		this.linkedTo = asgn;
	}
	public Assignment getLinkedTo() {
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
			if (arguments.getSubexpressions().get(0) instanceof ArgumentList)
				return varName + arguments.pretty(indents);
			return varName + "(" + arguments.pretty(indents) + ")";
		}
	}

}
