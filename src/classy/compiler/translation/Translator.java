package classy.compiler.translation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import classy.compiler.analyzing.Variable;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.BinOp;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Operation;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Value;

public class Translator {
	private List<String> outLines;
	private int indentation = 0;
	private int varNum = 1;
	private Map<Expression, String> varMangle;

	
	public Translator(Value program, List<Variable> vars) {
		// We are going to want to map out all variables and to what new name they will receive.
		//  We don't want any name conflicts (even if they are shadowed) so we will mangle the
		//  names to make some new name.
		// Both assignments and references will be mapped to that new name.
		varMangle = new HashMap<>();
		Set<String> namesUsed = new HashSet<>();
		
		for (Variable var: vars) {
			String name = var.getName();
			String useName = name;
			if (namesUsed.contains(name)) {
				// There was a collision, so we try another
				int cnt = 0;
				while (namesUsed.contains(name + cnt)) {
					cnt++;
				}
				useName = name + cnt;
			}
			// Great! we found a name we can use.
			varMangle.put(var.getSource(), useName);
			for (Expression ref: var.getRef())
				varMangle.put(ref, useName);
		}
		
		// Now begin the translation process
		translate(program);
	}
	
	public void translate(Value program) {
		outLines = new ArrayList<>();
		addLine("define dso_local i32 @main() #0 {");
		deltaIndent(1);
		// We want to put the return in varNum
		int retAt = allocate();
		//
		for (Expression e: program.getSubexpressions()) {
			translate(e, ""+retAt);
		}
		//
		int ret = load(""+retAt);
		addLine("ret i32 %", Integer.toString(ret));
		deltaIndent(-1);
		addLine("}");
	}
	
	protected void translate(Expression e, String retAt) {
		if (e instanceof Value) {
			Value val = (Value)e;
			for (Expression se : val.getSubexpressions())
				translate(se, retAt);
		}else if (e instanceof Block) {
			Block block = (Block)e;
			for (Expression be : block.getBody())
				translate(be, retAt);
		}else if (e instanceof If) {
			If if_ = (If)e;
			// We want to find the result of the condition, then jump from there
			int cond = allocate();
			translate(if_.getCondition(), ""+cond);
			// since literals allocate it, we want to get our value back through a load
			int loaded = load(""+cond);
			int compare = varNum++;
			addLine("%", Integer.toString(compare), " = icmp eq i32 %", Integer.toString(loaded),", 0");
			String tbranch = "then" + Integer.toString(compare);
			String fbranch = "else" + Integer.toString(compare);
			String next = "next" + Integer.toString(compare);
			// branch to either the true or false case
			addLine("br i1 %", Integer.toString(compare), ", label %", fbranch, ", label %", tbranch);
			addLine();
			
			addLabel(tbranch);
			translate(if_.getThen(), retAt);
			addLine("br label %", next);
			
			addLabel(fbranch);
			translate(if_.getElse(), retAt);
			addLine("br label %", next);
			
			addLabel(next);
		}else if (e instanceof Literal) {
			// store in our return the literal we find
			String number = ((Literal)e).getToken().getValue();
			//addLine("store i32 ", number, ", i32* %", retAt, ", align 4");
			store(number, retAt);
		}else if (e instanceof Assignment) {
			// We want to find what we should save this variable name as
			Assignment asgn = (Assignment)e;
			String name = varMangle.get(asgn);
			allocate(name);
			translate(asgn.getValue(), name);
		}else if (e instanceof Reference) {
			// Reference needs to copy from the variable to the return address
			Reference ref = (Reference)e;
			String name = varMangle.get(ref);
			// This requires a load and then a store
			int value = load(name);
			store("%"+value, retAt);
		}else if (e instanceof Operation) {
			Operation op = (Operation)e;
			String rhs;
			List<Subexpression> opRhs = op.getRHS().getSubexpressions();
			if (opRhs.size() == 1 && opRhs.get(0) instanceof Literal) {
				// We can optimize if it is an int literal by a direct output
				rhs = ((Literal)opRhs.get(0)).getToken().getValue();
			}else {
				int ra = allocate();
				translate(op.getRHS(), ra+"");
				int rl = load(ra+"");
				rhs = "%" + rl;
			}
			
			if (op instanceof BinOp) {
				BinOp bop = (BinOp)op;
				String lhs;
				List<Subexpression> opLhs = bop.getLHS().getSubexpressions();
				if (opLhs.size() == 1 && opLhs.get(0) instanceof Literal) {
					// We can optimize if it is an int literal by a direct output
					lhs = ((Literal)opLhs.get(0)).getToken().getValue();
				}else {
					int la = allocate();
					translate(bop.getLHS(), la+"");
					int ll = load(la+"");
					lhs = "%" + ll;
				}
				int result = varNum++;
				String operation = "add";
				if (op instanceof BinOp.Addition)
					operation = "add nsw";
				else if (op instanceof BinOp.Subtraction)
					operation = "sub nsw";
				else if (op instanceof BinOp.Multiplication)
					operation = "mul nsw";
				else if (op instanceof BinOp.Division)
					operation = "sdiv";
				addLine("%", Integer.toString(result), " = ", operation, " i32 ", lhs, ", ", rhs);
				store("%"+result, retAt);
			}else {
				int result = varNum++;
				if (op instanceof Operation.Negation)
					// subtract the value from 0
					addLine("%", Integer.toString(result), " = ", "sub nsw i32 0, ", rhs);
				else if (op instanceof Operation.Not) {
					int compare = result;
					int xor = varNum++;
					result = varNum++;
					addLine("%", Integer.toString(compare), " = icmp ne i32 ", rhs, ", 0");
					addLine("%", Integer.toString(xor), " = xor i1 %", Integer.toString(compare), ", true");
					addLine("%", Integer.toString(result), " = zext i1 %", Integer.toString(xor), " to i32");
				}
				store("%"+result, retAt);
			}
		}
	}
	
	protected int allocate() {
		int retAt = varNum++;
		addLine("%", Integer.toString(retAt), " = alloca i32, align 4");
		return retAt;
	}
	protected void allocate(String name) {
		addLine("%", name, " = alloca i32, align 4");
	}
	protected void store(String what, String at) {
		addLine("store i32 ", what, ", i32* %", at, ", align 4");
	}
	protected int load(String from) {
		int retAt = varNum++;
		addLine("%", Integer.toString(retAt), " = load i32, i32* %", from, ", align 4");
		return retAt;
	}
	
	protected void addLine(String... line) {
		outLines.add(indent(line));
	}
	protected void addLabel(String label) {
		indentation--;
		addLine(label, ":");
		indentation++;
	}
	
	protected String indent(String... line) {
		StringBuffer buf = new StringBuffer();
		for (int i=0; i<indentation; i++)
			buf.append("  ");
		for (int i=0; i<line.length; i++)
			buf.append(line[i]);
		return buf.toString();
	}
	protected void deltaIndent(int indent) {
		this.indentation += indent;
	}
	
	public List<String> getOutLines() {
		return outLines;
	}
}
