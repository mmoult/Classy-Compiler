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
		// basic
		COMMENT,
		OPEN_BRACE,
		NEW_LINE,
		SPACE,
		// expressions
		CLOSE_BRACE,
		IDENTIFIER,
		PERIOD,
		COMMA,
		NUMBER,
		OPEN_PAREN,
		CLOSE_PAREN,
		SEMICOLON,
		VOID,
		COLON,
		// operations
		PLUS,
		MINUS,
		STAR,
		PERCENT,
		SLASH,
		// logical operations
		TRUE,
		FALSE,
		NEQUAL,
		EQUAL,
		LESS_THAN,
		LESS_EQUAL,
		GREATER_THAN,
		GREATER_EQUAL,
		BANG,
		AMPERSAND,
		BAR,
		// constructs
		LET,
		ASSIGN,
		IF,
		ELSE,
		LAMBDA,
		SELF,
		TYPE,
		ISA
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
		buf.append(getLocation());
		buf.append(")");
		return buf.toString();
	}

	public static boolean isNonSyntaxType(Type curr) {
		return curr == Type.COMMENT || curr == Type.SEMICOLON || curr == Type.NEW_LINE ||
				curr == Type.SPACE;
	}
	
	public String getLocation() {
		return lineNo + ":" + colNo;
	}

}
