package classy.compiler.util;

import java.util.List;

import classy.compiler.lexing.Token;
import classy.compiler.parsing.Parser;
import classy.compiler.parsing.Value;

public class ParsingUtil {
	
	public static Token mockToken(Token.Type type) {
		return new Token(null, type, -1, -1);
	}
	
	public static Token mockToken(String value, Token.Type type) {
		return new Token(value, type, -1, -1);
	}
	
	public static Value makeProgram(List<Token> tokens) {
		Parser parse = new Parser(tokens);
		return parse.getProgram();
	}

}
