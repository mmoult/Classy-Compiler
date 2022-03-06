package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Literal extends Subexpression {
	protected Token token;
	
	public Literal(Value parent) {
		super(parent);
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// There can be several types of literals.
		// Two that are currently supported are numbers and bool literals
		
		if (it.match(Token.Type.NUMBER, end) || it.match(Token.Type.TRUE, end) ||
				it.match(Token.Type.FALSE, end)) {			
			it.match(Token.Type.NUMBER, end);
			token = it.token();
			it.next(end);
		}else
			throw new ParseException("Unknown literal token ", it.token(), "!");
	}
	
	public Token getToken() {
		return token;
	}
	
	@Override
	public boolean isLink() {
		return false;
	}
	
	@Override
	public String pretty(int indents) {
		return token.getValue();
	}
	
	public Literal clone() {
		Literal cloned = new Literal(parent);
		cloned.token = token;
		return cloned;
	}
}
