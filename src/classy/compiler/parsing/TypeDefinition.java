package classy.compiler.parsing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.analyzing.Type;
import classy.compiler.lexing.Token;

public class TypeDefinition extends Expression {
	protected Block parent;
	protected String typeName;
	protected List<Parameter> fieldList;
	protected List<Parameter> supers;
	protected Type sourced = null;
	
	public TypeDefinition(Block parent) {
		this.parent = parent;
		fieldList = new ArrayList<>();
		supers = new ArrayList<>();
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
			// TODO inheritance of type
		}
		
		if (!it.match(Token.Type.ASSIGN, end))
			throw new ParseException("Type definition of \"", typeName,
					"\" must have '=' following the type name! ", it.token(), " found instead.");
		it.next(end);
		
		// Here is where the field list begins.
		// There can be a single field, in which case parentheses are unnecessary.
		// But otherwise we will need to see some parentheses.
		if (it.match(Token.Type.OPEN_PAREN, end)) {
			fieldList = Parameter.parseParamList(it, end);
		}else {
			Parameter param = new Parameter();
			param.parse(it, end);
			fieldList.add(param);
		}
	}
	
	public String getTypeName() {
		return typeName;
	}
	
	@Override
	public String pretty(int indents) {
		StringBuffer buf = new StringBuffer("type ");
		buf.append(typeName);
		// TODO inheritance print here
		buf.append(" = ");
		if (fieldList.size() > 1)
			buf.append('(');
		boolean first = true;
		for (Parameter field: fieldList) {
			if (first)
				first = false;
			else {
				buf.append(",");
				buf.append(getIndents(indents + 1));
			}
			buf.append(field.pretty(indents + 1));
		}
		if (fieldList.size() > 1) {
			buf.append(",");
			buf.append(getIndents(indents));
			buf.append(')');
		}
		return buf.toString();
	}

}
