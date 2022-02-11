package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;

public class Parser {
	protected Value program;
	
	public Parser(List<Token> tokens) {
		parse(tokens);
	}
	
	public void parse(List<Token> tokens) {
		Block topLevel = new Block(null, true);
		TokenIterator it = new TokenIterator(tokens, 0);
		int end = tokens.size();
		topLevel.parse(it, end);
		program = new Value(null, topLevel);
		topLevel.parent = program;
		topLevel.reduce(); // If we can reduce the top level, we should
			
		// We hope that the block went to the end of the token list. If it halted
		// prematurely, we have a syntax error.
		boolean atEnd = false;
		try {
			it.match(Token.Type.PERIOD, end);
		}catch(ParseException endOfFile) {
			atEnd = true;
		}
		if (!atEnd)
			throw new ParseException("Implied block at top file level terminated prematurely!");
	}
	
	public static List<Expression> parse(TokenIterator it, int end, Block parent) {
		List<Expression> expressions = new ArrayList<>();
		
		while(it.index < end) {
			// We want to find the next expression.
			// If the next token is "let", then it is an assignment.
			// Otherwise, it is a value
			Expression found;
			if (it.match(Token.Type.LET, end))
				found = new Assignment(parent);
			else
				found = new Value();
			
			found.parse(it, end); // then we extract it proper
			// TODO default value optimizations
			// For the sake of optimization, we may want to externalize the default value.
			// If the value is a complex expression, we don't want it computed each time
			//  it is used. Thus, we assign the default value to a new variable, the
			//  reference for which may be used.
			// If it turns out that the default value was only used once, optimization
			//  will remove the redundant variable.
			
			expressions.add(found);
			// There may be a semicolon here, which should be skipped
			// And we want to skip any non-syntax tokens
			if (it.index < end && it.match(Token.Type.SEMICOLON, end)) {
				it.next(end);
				// skip to the next non-semicolon token now
				if (it.index < end)
					it.match(Token.Type.PERIOD, end);
			}
		}
		return expressions;
	}
	
	public Value getProgram() {
		return program;
	}
}
