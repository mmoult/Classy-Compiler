package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.analyzing.Type;
import classy.compiler.lexing.Token;

public class TypeDefinition extends Expression {
	protected Block parent;
	protected String typeName;
	protected List<Parameter> fieldList;
	protected Tuple supers;
	protected Type sourced = null;
	
	public TypeDefinition(Block parent) {
		this.parent = parent;
		fieldList = new ArrayList<>();
	}

	@Override
	public void parse(TokenIterator it, int end) {
		// Syntax: type typeName [isa parent [, ...]] = paramList
		// where all in [] are optional.
		if (!it.match(Token.Type.TYPE, end))
			throw new ParseException("Type definition must begin with \"type\" keyword! ", it.token(), 
					" found instead.");
		it.next(end);
		
		if (!it.match(Token.Type.IDENTIFIER, end))
			throw new ParseException("Type definition must have identifier following \"type\" keyword! ",
					it.token(), " found instead.");
		typeName = it.token().getValue();
		it.next(end);

		if (it.match(Token.Type.ISA, end)) {
			it.next(end);
			if (it.match(Token.Type.VOID, end))
				// Even though the super is a tuple, it may *not* be void!
				throw new ParseException("List of parents in type definition for \"", typeName,
						"\" may not be void! Some list of super types must follow the \"isa\" keyword. ",
						it.token(), " found instead.");
			
			supers = new Tuple(null);
			// Each parent is a labeled value. Typically it will simply be a type name.
			//  Other times, it will be some expression that results to a default value.
			//  Alternatively, it may be a labeled value in the form: Type = default
			//   But if it is labeled, there must be some parenthesization
			int stop;
			if (it.match(Token.Type.OPEN_PAREN, end)) {
				it.next(end);
				stop = it.find(Token.Type.CLOSE_PAREN, end);
			}else
				stop = it.find(Token.Type.ASSIGN, end);
			supers.parse(it, stop);
			if (it.index != stop)
				throw new ParseException("Unexpected token ", it.token(),
						" found in super list for the definition of type \"", typeName, "\"!");
			it.next(end);
			if (it.tokens.get(stop).getType() == Token.Type.CLOSE_PAREN) {
				// If the super list was aborted by a close parenthesis, we still need to see an assign.
				if (!it.match(Token.Type.ASSIGN, end))
					throw new ParseException("Type definition of \"", typeName,
							"\" must have '=' following the type name! ", it.token(), " found instead.");
				it.next(end);
			}
		}else {
			if (!it.match(Token.Type.ASSIGN, end))
				throw new ParseException("Type definition of \"", typeName,
						"\" must have '=' following the type name! ", it.token(), " found instead.");
			it.next(end);
		}
		
		// Here is where the field list begins.
		// There can be a single field, in which case parentheses are unnecessary.
		// But otherwise we will need to see some parentheses.
		if (it.match(Token.Type.OPEN_PAREN, end))
			fieldList = Parameter.parseParamList(it, end);
		else if (it.match(Token.Type.VOID, end))
			it.next(end);
		else {
			Parameter param = new Parameter();
			param.parse(it, end);
			fieldList.add(param);
		}
		
		// We need to see a semicolon next (since this does not end in a value, we want to verify that
		//  we don't have any extra tokens hiding in the same line.)
		if (!it.match(Token.Type.SEMICOLON, end))
			throw new ParseException("Unexpected token: ", it.token(), " found after end of type definition!");
	}
	
	public void setSourced(Type t) {
		this.sourced = t;
	}
	public Type getSourced() {
		return sourced;
	}
	
	public String getTypeName() {
		return typeName;
	}
	
	public List<Parameter> getFieldList() {
		return fieldList;
	}
	public Tuple getSupers() {
		return supers;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("type ");
		buf.append(typeName);
		if (supers != null) {
			buf.append(" is ");
			buf.append(supers.pretty(indents + 1));
		}
		buf.append(" = ");
		if (fieldList.size() > 1)
			buf.append('(');
		boolean first = true;
		for (Parameter field: fieldList) {
			if (first)
				first = false;
			else {
				buf.append(", ");
			}
			buf.append(field.pretty(indents + 1));
		}
		if (fieldList.size() > 1)
			buf.append(')');
		else if (fieldList.isEmpty())
			buf.append("void");
		return buf.toString();
	}

}
