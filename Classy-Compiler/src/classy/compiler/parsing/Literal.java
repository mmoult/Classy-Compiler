package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Literal extends Expression {
	protected Token token;
	
	public Literal(NestingExpression parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// There can be several types of literals.
		// Right now, we are just accounting for one
		it.match(Token.Type.NUMBER, end);
		token = it.token();
		it.next(end);
	}
	
	public Token getToken() {
		return token;
	}
	
	@Override
	public String pretty(int indents) {
		return token.getValue();
	}
}
