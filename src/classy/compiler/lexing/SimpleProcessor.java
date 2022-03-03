package classy.compiler.lexing;

import classy.compiler.lexing.Lexer.Processor;
import classy.compiler.lexing.Token.Type;

public class SimpleProcessor extends Processor {
	protected Token.Type found = null;

	@Override
	public Type getType() {
		return found;
	}
	
	@Override
	public void clear() {
		super.clear();
		found = null;
	}

	@Override
	public String add(String check) {
		int i = 0;
		
		char c = check.charAt(i);
		if (c == '.')
			found = Token.Type.PERIOD;
		else if (c == ',')
			found = Token.Type.COMMA;
		else if (c == ';')
			found = Token.Type.SEMICOLON;
		else if (c == '(')
			found = Token.Type.OPEN_PAREN;
		else if (c == ')')
			found = Token.Type.CLOSE_PAREN;
		else if (c == '{')
			found = Token.Type.OPEN_BRACE;
		else if (c == '}')
			found = Token.Type.CLOSE_BRACE;
		else if (c == '=') {
			char after = charAt(check, i + 1);
			if (after == '=') {
				found = Token.Type.EQUAL;
				i++;
			}else
				found = Token.Type.ASSIGN;
		}else if (c == ':')
			found = Token.Type.COLON;
		else if (c == '+')
			found = Token.Type.PLUS;
		else if (c == '-')
			found = Token.Type.MINUS;
		else if (c == '*')
			found = Token.Type.STAR;
		else if (c == '/')
			found = Token.Type.SLASH;
		else if (c == '!')
			found = Token.Type.BANG;
		else if (c == '%')
			found = Token.Type.PERCENT;
		else if (c == '&')
			found = Token.Type.AMPERSAND;
		else if (c == '|')
			found = Token.Type.BAR;
		else if (c == '>') {
			i++;
			char after = charAt(check, i);
			if (after == '=') {
				found = Token.Type.GREATER_EQUAL;
			}else {
				found = Token.Type.GREATER_THAN;
				i--;
			}
		}else if (c == '<') {
			i++;
			char after = charAt(check, i);
			if (after == '=') {
				found = Token.Type.LESS_EQUAL;
			}else if (after == '>')
				found = Token.Type.NEQUAL;
			else {
				found = Token.Type.LESS_THAN;
				i--;
			}
		}
		if (found == null)
			return check;
		
		soFar += check.substring(0, i + 1);
		return check.substring(i + 1);
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
		return "miscellaneous";
	}

}
