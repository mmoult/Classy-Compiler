package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class NumberProcessor extends Processor {

	@Override
	public Type getType() {
		return Token.Type.NUMBER;
	}

	@Override
	public String add(String check) {
		// We can only see one decimal in the number
		byte decimal = 0;
		int i=0;
		boolean numFound = false;
		if (check.charAt(i) == '-')
			i++;
		
		for (; i < check.length(); i++) {
			char c = check.charAt(i);
			
			if (c == '.') {
				if (decimal == 1) {
					// this means that the decimal was the last character.
					i--; // include neither
					break;
				}else if (decimal == 2) {
					// If the decimal has already been found
					break;
				}
				decimal = 1;
			}else if (Character.isDigit(c)) {
				// We can include this character
				numFound = true;
				if (decimal == 1)
					decimal = 2; // go to the next state
			}else
				break;
		}
		if (!numFound)
			return check; // if we didn't find a number, then don't accept any
		
		soFar += check.substring(0, i);
		return check.substring(i);
	}

	@Override
	public String description() {
		return "number";
	}

}
