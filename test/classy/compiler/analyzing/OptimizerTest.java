package classy.compiler.analyzing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import classy.compiler.lexing.Token;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Parser;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Value;

public class OptimizerTest {
	Optimizer opt;

	@BeforeEach
	protected void setUp() throws Exception {
		opt = new Optimizer();
	}
	
	protected Value makeProgram(List<Token> tokens) {
		Parser parse = new Parser(tokens);
		return parse.getProgram();
	}

	@Test
	void optIf() {
		// We have to make the program, so the best way is to do it via
		//  the regular path. But we can skip lexing
		List<Token> tokens = List.of(
			new Token("if", Token.Type.IF, -1, -1),
			new Token("0", Token.Type.NUMBER, -1, -1),
			new Token("\n", Token.Type.NEW_LINE, -1, -1),
			new Token("6", Token.Type.NUMBER, -1, -1),
			new Token("\n", Token.Type.NEW_LINE, -1, -1),
			new Token("7", Token.Type.NUMBER, -1, -1)
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
		tokens = List.of(
			new Token("if", Token.Type.IF, -1, -1),
			new Token("1", Token.Type.NUMBER, -1, -1),
			new Token("\n", Token.Type.NEW_LINE, -1, -1),
			new Token("6", Token.Type.NUMBER, -1, -1),
			new Token("\n", Token.Type.NEW_LINE, -1, -1),
			new Token("7", Token.Type.NUMBER, -1, -1)
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
			new Token("10", Token.Type.NUMBER, -1, -1),
			new Token("/", Token.Type.SLASH, -1, -1),
			new Token("2", Token.Type.NUMBER, -1, -1),
			new Token("+", Token.Type.PLUS, -1, -1),
			new Token("3", Token.Type.NUMBER, -1, -1),
			new Token("-", Token.Type.MINUS, -1, -1),
			new Token("4", Token.Type.NUMBER, -1, -1)
		);
		Value program = makeProgram(tokens);
		// Unfortunately we have to check this program to enforce proper groupings
		// This is more roundabout, but it will do the same thing in the end
		new Checker(program, true);
		
		// We should get a literal 4 out after optimizations
		List<Subexpression> subs = program.getSubexpressions();
		assertTrue(subs.size() == 1);
		Subexpression only = subs.get(0);
		assertTrue(only instanceof Literal);
		assertEquals("4", ((Literal)only).getToken().getValue());
	}

}
