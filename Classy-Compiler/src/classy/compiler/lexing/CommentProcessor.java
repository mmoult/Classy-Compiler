package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class CommentProcessor extends Processor {
	protected int nestLevels = 0;

	@Override
	public boolean isValid(String check) {
		if(check.equals("#")) {
			if (!soFar.isEmpty() && soFar.charAt(soFar.length() - 1) == '|') {
				nestLevels--;
				if (nestLevels < 0)
					throw new LexException("Excess nested string terminations in comment!");
			}
			return true;
		}if(check.equals("|") && !soFar.isEmpty()) {
			if (soFar.charAt(soFar.length() - 1) == '#')
				nestLevels++; //open a new level
			return true;
		}
		
		if (soFar.startsWith("#"))
			return true;
		return false;
	}

	@Override
	public Type getType() {
		return Token.Type.COMMENT;
	}
	
	@Override
	public boolean lineTerminated() {
		boolean terminated = nestLevels == 0;
		if (!terminated)
			soFar += '\n';
		
		return terminated;
	}
	
	@Override
	public void clear() {
		super.clear();
		nestLevels = 0;
	}

}
