package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class SingletonProcessor extends Processor {

	@Override
	public boolean isValid(String c) {
		if (!soFar.isEmpty())
			return false;
		if (c.equals(".") || c.equals(",") || c.equals(";") || 
				c.equals("{") || c.equals("}") || c.equals("(") || c.equals(")") ||
				c.equals("=")) {
			return true;
		}
		return false;
	}

	@Override
	public Type getType() {
		if(soFar.isEmpty())
			return null;
		switch(soFar) {
		case ".":
			return Token.Type.PERIOD;
		case ",":
			return Token.Type.COMMA;
		case ";":
			return Token.Type.SEMICOLON;
		case "{":
			return Token.Type.OPEN_BRACE;
		case "}":
			return Token.Type.CLOSE_BRACE;
		case "(":
			return Token.Type.OPEN_PAREN;
		case ")":
			return Token.Type.CLOSE_PAREN;
		case "=":
			return Token.Type.ASSIGN;
		}
		return null;
	}

	@Override
	public boolean lineTerminated() {
		return true;
	}

}
