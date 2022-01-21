package classy.compiler.lexing;

public class Token {
	protected String value;
	protected Type type;
	protected int lineNo;
	protected int colNo;
	
	public Token(String value, Type type, int lineNo, int colNo) {
		this.value = value;
		this.type = type;
		this.lineNo = lineNo;
		this.colNo = colNo;
	}
	
	public enum Type {
		COMMENT,
		OPEN_BRACE,
		CLOSE_BRACE,
		IDENTIFIER,
		PERIOD,
		COMMA,
		ASSIGN,
		SPACE,
		NEW_LINE,
		NUMBER,
		OPEN_PAREN,
		CLOSE_PAREN,
		SEMICOLON,
		
		LET,
		IF,
		ELSE,
		LAMBDA,
	}
	
	public String getValue() {
		return value;
	}
	
	public Type getType() {
		return type;
	}
	
	public int getLineNo() {
		return lineNo;
	}
	public int getColNo() {
		return colNo;
	}
	
	@Override
	public String toString() {
		StringBuffer buf = new StringBuffer("Token(");
		buf.append(type);
		buf.append(" \"");
		String val = value.replace("\n", "\\n");
		buf.append(val);
		buf.append("\" at ");
		buf.append(lineNo);
		buf.append(":");
		buf.append(colNo);
		buf.append(")");
		return buf.toString();
	}

	public static boolean isNonSyntaxType(Type curr) {
		return curr == Type.COMMENT || curr == Type.SEMICOLON || curr == Type.NEW_LINE ||
				curr == Type.SPACE;
	}

}