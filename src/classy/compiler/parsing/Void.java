package classy.compiler.parsing;

import classy.compiler.lexing.Token;

public class Void extends Subexpression {

	public Void(Value parent) {
		super(parent);
	}

	@Override
	public boolean isLink() {
		return false;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (!it.match(Token.Type.VOID, end))
			throw new ParseException("Unexpected token ", it.token(), " founnd where void token expected!");
		it.next(end);
	}
	
	@Override
	public String pretty(int indents) {
		return "()";
	}

}
