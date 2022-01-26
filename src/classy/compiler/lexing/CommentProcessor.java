package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class CommentProcessor extends Processor {
	protected int nestLevel = 0;

	@Override
	public Type getType() {
		return Token.Type.COMMENT;
	}
	
	@Override
	public void clear() {
		super.clear();
		nestLevel = 0;
	}

	@Override
	public String add(String check) {
		// We are going to attempt to add all chars in check
		int i=0;
		// A comment must start with either # or #|
		if (nestLevel == 0) {
			if (charAt(check, 0) != '#')
				return check;
			
			i++;
			if (charAt(check, 1) == '|') {
				nestLevel++;
				i++; // we are now checking char at index 2
			}else
				nestLevel = -1; // indicating a single-line comment
		}
		
		for (; i<check.length(); i++) {
			char c = check.charAt(i);
			
			if (nestLevel != -1 && c == '#' && charAt(check, i + 1) == '|') {
				// increase a level
				nestLevel++;
				i++;
			}else if (nestLevel != -1 && c == '|' && charAt(check, i + 1) == '#') {
				// decrease a level
				if (nestLevel == 0)
					throw new LexException("Excess nested string terminations in comment, \"",
							(soFar + check.substring(0, i + 1)), "\"!");
				nestLevel--;
				i++;
			}else if (c == '\n' && nestLevel <= 0)
				break; // a new line signals a ready-to-terminate comment is finished
		}
		soFar += check.substring(0, i);
		return check.substring(i);
	}
	
	protected char charAt(String check, int index) {
		if (index >= check.length() || index < 0)
			// Since we just need this character for matching, we don't care if we go
			//  out of bounds with this check.
			return '\0';
		return check.charAt(index);
	}

	@Override
	public String description() {
		return "comment";
	}

}
