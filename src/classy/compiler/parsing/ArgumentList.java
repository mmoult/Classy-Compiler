package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;

public class ArgumentList extends Subexpression {
	protected List<Value> args = new ArrayList<>();

	public ArgumentList(Value parent) {
		super(parent);
	}
	
	public void addArg(Value val) {
		args.add(val);
	}
	
	public List<Value> getArgs() {
		return args;
	}

	@Override
	public boolean isLink() {
		return false;
	}

	@Override
	public void parse(TokenIterator it, int end) {
		if (it.match(Token.Type.VOID, end)) {
			it.next(end);
			return;
		}
		// I think that this is where we need to look for more arguments
		// Comma is not an operator the same way as + or -. It is outside
		// precedence, since in a way, it is ordered absolutely last
		
		// Parse a value between here and the next comma
		// Continue while there is a comma immediately after the next value
		boolean commaFound = true;
		while (commaFound) {
			commaFound = false;
			int comma = it.find(Token.Type.COMMA, end);
			if (comma == -1)
				comma = end;
			Value arg = new Value();
			arg.parse(it, comma);
			addArg(arg); // add it to the list
			
			// If the next token is a comma, and it is the comma found earlier,
			// then we continue the list
			if (comma < end && it.match(Token.Type.COMMA, end) && it.index == comma) {
				commaFound = true;
				it.next(end);
			}
		}
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("(");
		boolean first = true;
		for (Value arg: args) {
			if (first)
				first = false;
			else
				buf.append(", ");
			buf.append(arg.pretty(indents));
		}
		buf.append(")");
		
		return buf.toString();
	}

}
