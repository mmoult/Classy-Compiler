package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Literal extends Subexpression {
	
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
			startToken = it.token();
			it.next(end);
		}else
			throw new ParseException("Unknown literal token ", it.token(), "!");
	}
	
	public Token getToken() {
		return startToken;
	}
	
	@Override
	public boolean isLink() {
		return false;
	}
	
	@Override
	public String pretty(int indents) {
		return startToken.getValue();
	}
	
	public Literal clone() {
		Literal cloned = new Literal(parent);
		cloned.startToken = startToken;
		return cloned;
	}
}
