package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Reference extends Subexpression {
	protected String varName;
	
	public Reference(Value parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!it.match(Token.Type.IDENTIFIER, end))
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
	
	@Override
	public String pretty(int indents) {
		return varName;
	}

}
