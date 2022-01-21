package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class WhitespaceProcessor extends Processor {

	@Override
	protected boolean isValid(String check) {
		for(int i=0; i<check.length(); i++) {
			if (!Character.isWhitespace(check.charAt(i)))
				return false;
		}
		return true;
	}

	@Override
	public boolean lineTerminated() {
		return true;
	}

	@Override
	public Type getType() {
		return Token.Type.SPACE;
	}

}
