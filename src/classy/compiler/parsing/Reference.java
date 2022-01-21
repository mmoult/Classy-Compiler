package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Reference extends Expression {
	protected String varName;
	
	public Reference(NestingExpression parent) {
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
	public String pretty(int indents) {
		return varName;
	}

}
