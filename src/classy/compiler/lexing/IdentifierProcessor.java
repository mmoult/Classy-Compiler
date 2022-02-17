package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class IdentifierProcessor extends Processor {
	
	protected boolean isValidChar(char c) {
		return Character.isAlphabetic(c) || c == '_';
	}

	@Override
	public Type getType() {
		//check for keywords. If none match, then this is an identifier
		switch(soFar) {
		case "let":
			return Token.Type.LET;
		case "if":
			return Token.Type.IF;
		case "else":
			return Token.Type.ELSE;
		case "lambda":
			return Token.Type.LAMBDA;
		case "void":
			return Token.Type.VOID;
		case "self":
			return Token.Type.SELF;
		}
		return Token.Type.IDENTIFIER;
	}

	@Override
	public String add(String check) {
		int i=0;
		for (; i<check.length(); i++) {
			char c = check.charAt(i);
			if (!isValidChar(c)) {
				// if this is not the first character, we have an augmented set of rules
				if (i > 0) {
					if (!Character.isDigit(c))
						break;
				}else
					break;
			}
		}
		soFar += check.substring(0, i);
		return check.substring(i);
	}

	@Override
	public String description() {
		return "identifier";
	}

}
