package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class WhitespaceProcessor extends Processor {

	@Override
	public Type getType() {
		return Token.Type.SPACE;
	}

	@Override
	public String add(String check) {
		int i=0;
		for (; i<check.length(); i++) {
			char c = check.charAt(i);
			if (c == '\n' || !Character.isWhitespace(c))
				break;
		}
		soFar += check.substring(0, i);
		return check.substring(i);
	}

	@Override
	public String description() {
		return "white space";
	}

}
