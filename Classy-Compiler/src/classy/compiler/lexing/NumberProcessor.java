package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class NumberProcessor extends Processor {
	protected boolean decimal = false;

	@Override
	protected boolean isValid(String check) {
		//The - can be included, but must be first
		if (soFar.isEmpty() && check.equals("-"))
			return true;
		if (!decimal && check.equals(".")) {
			decimal = true;
			return true;
		}
		if (check.length() == 1 && Character.isDigit(check.charAt(0)))
			return true;
		
		return false;
	}

	@Override
	public boolean lineTerminated() {
		return true;
	}

	@Override
	public Type getType() {
		return Token.Type.NUMBER;
	}
	
	@Override
	public void clear() {
		super.clear();
		decimal = false;
	}

}
