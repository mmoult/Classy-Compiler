package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class IdentifierProcessor extends Processor {

	@Override
	public boolean isValid(String str) {
		if(soFar.isEmpty()) {
			// This means that we have not processed anything yet (successfully)
			// Check the first character, which must be a symbol or a letter
			char first = str.charAt(0);
			if (Character.isDigit(first) || !isValidChar(first))
				return false;
		}
		
		for (int i=0; i<str.length(); i++) {
			char c = str.charAt(i);
			
			if (!isValidChar(c))
				return false;
		}
		return true;
	}
	
	protected boolean isValidChar(char c) {
		if (c == '{' || c == '}' || c == '(' || c == ')' || c == '.' || c == ',' || 
				c == '=' || Character.isWhitespace(c))
			return false;
		return true;
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
		}
		return Token.Type.IDENTIFIER;
	}
	
	@Override
	public boolean lineTerminated() {
		return true;
	}

}
