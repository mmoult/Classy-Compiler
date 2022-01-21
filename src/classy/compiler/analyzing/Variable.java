package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.parsing.Expression;
import classy.compiler.parsing.Value;

public class Variable {
	protected String name;
	protected Value value;
	protected Expression source;
	protected List<Expression> references;
	
	public Variable(String name, Value value, Expression source) {
		this.name = name;
		this.value = value;
		this.source = source;
		
		references = new ArrayList<>();
	}
	
	public String getName() {
		return name;
	}
	public Value getValue() {
		return value;
	}
	public Expression getSource() {
		return source;
	}
	public List<Expression> getRef() {
		return references;
	}
	
	public void addRef(Expression referenced) {
		references.add(referenced);
	}

}
