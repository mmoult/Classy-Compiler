package classy.compiler.analyzing;

import static classy.compiler.util.ParsingUtil.makeProgram;
import static classy.compiler.util.ParsingUtil.mockToken;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import classy.compiler.lexing.Token;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Value;


public class OptimizerTest {
	Optimizer opt;

	@BeforeEach
	protected void setUp() throws Exception {
		opt = new Optimizer();
	}
	
	List<Token> makeList(Token...tokens) {
		ArrayList<Token> ls = new ArrayList<>(tokens.length);
		for (Token t: tokens)
			ls.add(t);
		return ls;
	}

	@Test
	void optIf() {
		// We have to make the program, so the best way is to do it via
		//  the regular path. But we can skip lexing
		List<Token> tokens = makeList(
			mockToken(Token.Type.IF),
			mockToken("false", Token.Type.FALSE),
			mockToken(Token.Type.SEMICOLON),
			mockToken("6", Token.Type.NUMBER),
			mockToken(Token.Type.SEMICOLON),
			mockToken("7", Token.Type.NUMBER)
		);
		Value program = makeProgram(tokens);
		
		opt.optimize(List.of(), program);
		// We want to verify that the end result is a literal 7, which is the else case
		List<Subexpression> subs = program.getSubexpressions();
		assertTrue(subs.size() == 1);
		Subexpression only = subs.get(0);
		assertTrue(only instanceof Literal);
		assertEquals("7", ((Literal)only).getToken().getValue());
		
		// Now we want to check that the then case works too
		tokens = makeList(
			mockToken(Token.Type.IF),
			mockToken("true", Token.Type.TRUE),
			mockToken(Token.Type.SEMICOLON),
			mockToken("6", Token.Type.NUMBER),
			mockToken(Token.Type.SEMICOLON),
			mockToken("7", Token.Type.NUMBER)
		);
		program = makeProgram(tokens);
		opt.optimize(List.of(), program);
		subs = program.getSubexpressions();
		assertTrue(subs.size() == 1);
		only = subs.get(0);
		assertTrue(only instanceof Literal);
		assertEquals("6", ((Literal)only).getToken().getValue());
	}
	
	@Test
	void optOperations() {
		List<Token> tokens = List.of(
			mockToken("10", Token.Type.NUMBER),
			mockToken(Token.Type.SLASH),
			mockToken("2", Token.Type.NUMBER),
			mockToken(Token.Type.PLUS),
			mockToken("3", Token.Type.NUMBER),
			mockToken(Token.Type.MINUS),
			mockToken("4", Token.Type.NUMBER)
		);
		Value program = makeProgram(tokens);
		// Unfortunately we have to check this program to enforce proper groupings
		// This is more roundabout, but it will do the same thing in the end
		opt = new Optimizer(new Checker(program), program);
		
		// We should get a literal 4 out after optimizations
		List<Subexpression> subs = program.getSubexpressions();
		assertTrue(subs.size() == 1);
		Subexpression only = subs.get(0);
		assertTrue(only instanceof Literal);
		assertEquals("4", ((Literal)only).getToken().getValue());
	}

}
