package classy.compiler.translation;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import classy.compiler.analyzing.Variable;
import classy.compiler.parsing.ArgumentList;
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
import classy.compiler.parsing.Void;

public class Translator {
	private int varNum = 1;
	private int inFunction = 0;
	protected LinePlacer lines;
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
			if (var.getSource() != null) {
				namesUsed.add(useName);
				varMangle.put(var.getSource(), useName);
			}
		}
		
		// Now begin the translation process
		translate(program);
	}
	
	public void translate(Value program) {		
		// insert the printi.ll file, which is needed to print the result of main
		Scanner scan = null;
		try {
			List<String> topLines = new ArrayList<>();
			scan = new Scanner(new File("libs/printi.ll"));
			while (scan.hasNextLine())
				topLines.add(scan.nextLine());
			scan.close();
			
			lines = new LinePlacer(topLines);
			
			lines.addLine("define dso_local i32 @main() {");
			lines.deltaIndent(1);
			// We want to put the return in varNum
			int retAt = allocate();
			//
			for (Expression e: program.getSubexpressions()) {
				translate(e, ""+retAt);
			}
			//
			int ret = load(""+retAt);
			lines.addLine("call void @printi(i32 %", Integer.toString(ret), ")");
			lines.addLine("ret i32 0");
			lines.deltaIndent(-1);
			lines.addLine("}");
		} catch (FileNotFoundException e1) {
			throw new RuntimeException("Could not find requisite file: \"libs/itos.ll\"!");
		}
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
			lines.addLine("%", Integer.toString(compare), " = icmp eq i32 %", Integer.toString(loaded),", 0");
			String tbranch = "then" + Integer.toString(compare);
			String fbranch = "else" + Integer.toString(compare);
			String next = "next" + Integer.toString(compare);
			// branch to either the true or false case
			lines.addLine("br i1 %", Integer.toString(compare), ", label %", fbranch, ", label %", tbranch);
			lines.addLine();
			
			lines.addLabel(tbranch);
			translate(if_.getThen(), retAt);
			lines.addLine("br label %", next);
			
			lines.addLabel(fbranch);
			translate(if_.getElse(), retAt);
			lines.addLine("br label %", next);
			
			lines.addLabel(next);
		}else if (e instanceof Literal) {
			// store in our return the literal we find
			String number = ((Literal)e).getToken().getValue();
			//addLine("store i32 ", number, ", i32* %", retAt, ", align 4");
			store(number, retAt);
		}else if (e instanceof Assignment) {
			Assignment asgn = (Assignment)e;
			// We want to find what we should save this variable name as
			String name = varMangle.get(asgn);
			
			// First, we must find if this is a value assignment or a function assignment
			if (asgn.getParamList() == null) {
				allocate(name);
				translate(asgn.getValue(), name);
			}else {
				// We are going to make a function declaration, which needs to be on the top
				//  level. Thus, we start at the top scope, saving our old location to revert
				//  back after
				int prevVarNum = this.varNum;
				this.varNum = 1;
				this.inFunction++;
				LinePlacer.State oldState = lines.getTop();
				
				String[] lineCmps = new String[4 + (2 * asgn.getParamList().size())];
				lineCmps[0] = "define dso_local i32 @";
				lineCmps[1] = name;
				lineCmps[2] = "(";
				int i = 3;
				for (String param: asgn.getParamList()) {
					if (i > 3)
						lineCmps[i++] = ", i32 %";
					else
						lineCmps[i++] = "i32 %";
					lineCmps[i++] = param;
				}
				lineCmps[i++] = ") {";
				lines.addLine(lineCmps);
				lines.deltaIndent(1);
				int fRet = allocate();
				
				translate(asgn.getValue(), fRet+"");
				
				int loaded = load(fRet+"");
				lines.addLine("ret i32 %", Integer.toString(loaded));
				lines.deltaIndent(-1);
				lines.addLine("}");
				lines.addLine();
				
				// then the function declaration is done. Restore the state
				lines.revertState(oldState);
				this.varNum = prevVarNum;
				this.inFunction--;
			}
		}else if (e instanceof Reference) {
			// Reference needs to copy from the variable to the return address
			Reference ref = (Reference)e;
			String name; // the name used
			if (varMangle.containsKey(ref.getLinkedTo()))
				name = varMangle.get(ref.getLinkedTo());
			else // function params will not be in mangle since they are local
				name = ref.getVarName();
			if (ref.getArgument() == null) {
				// Regular reference
				if (inFunction == 0) {
					// This requires a load and then a store
					int value = load(name);
					store("%"+value, retAt);
				}else {
					// if we are in a function, we can just use the const argument
					store("%"+name, retAt);
				}
			}else {
				// Function call
				Value argument = ref.getArgument();
				Value[] args;
				if (argument.getSubexpressions().get(0) instanceof ArgumentList) {
					ArgumentList ls = (ArgumentList)argument.getSubexpressions().get(0);
					args = ls.getArgs().toArray(new Value[] {});
				}else if (argument.getSubexpressions().get(0) instanceof Void)
					args = new Value[] {};
				else
					args = new Value[] {argument};
				
				int[] argsAt = new int[args.length];
				int i=0;
				for (Value arg: args) {
					int alloc = allocate();
					translate(arg, alloc+"");
					argsAt[i] = load(alloc+"");
					i++;
				}
				
				int returned = varNum++;
				String[] callComps = new String[6 + 2*args.length];
				callComps[0] = "%";
				callComps[1] = Integer.toString(returned);
				callComps[2] = " = call i32 @";
				callComps[3] = ref.getVarName();
				callComps[4] = "(";
				int j = 5;
				for (i=0; i<args.length; i++) {
					if (j == 5)
						callComps[j++] = "i32 %";
					else
						callComps[j++] = ", i32 %";
					callComps[j++] = Integer.toString(argsAt[i]);
				}
				callComps[j] = ")";
				lines.addLine(callComps);
				store("%"+returned, retAt);
			}
			
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
				boolean typical = true;
				if (op instanceof BinOp.Addition)
					operation = "add nsw";
				else if (op instanceof BinOp.Subtraction)
					operation = "sub nsw";
				else if (op instanceof BinOp.Multiplication)
					operation = "mul nsw";
				else if (op instanceof BinOp.Division)
					operation = "sdiv";
				else if (op instanceof BinOp.Modulus)
					operation = "srem";
				else {
					typical = false;
					// All of these operations are binary that include an int extension
					if (op instanceof BinOp.Equal)
						operation = "eq";
					else if (op instanceof BinOp.NEqual)
						operation = "ne";
					else if (op instanceof BinOp.LessThan)
						operation = "slt";
					else if (op instanceof BinOp.LessEqual)
						operation = "sle";
					else if (op instanceof BinOp.GreaterThan)
						operation = "sgt";
					else if (op instanceof BinOp.GreaterEqual)
						operation = "sge";
					
					int compare = result;
					lines.addLine("%", Integer.toString(compare), " = icmp ", operation, " i32 ", lhs, ", ", rhs);
					result = varNum++;
					lines.addLine("%", Integer.toString(result), " = zext i1 %", Integer.toString(compare), " to i32");
				}
				
				if (typical)
					lines.addLine("%", Integer.toString(result), " = ", operation, " i32 ", lhs, ", ", rhs);
				store("%"+result, retAt);
			}else {
				int result = varNum++;
				if (op instanceof Operation.Negation)
					// subtract the value from 0
					lines.addLine("%", Integer.toString(result), " = ", "sub nsw i32 0, ", rhs);
				else if (op instanceof Operation.Not) {
					int compare = result;
					result = varNum++;
					lines.addLine("%", Integer.toString(compare), " = icmp eq i32 ", rhs, ", 0");
					lines.addLine("%", Integer.toString(result), " = zext i1 %", Integer.toString(compare), " to i32");
				}
				store("%"+result, retAt);
			}
		}
	}
	
	protected int allocate() {
		int retAt = varNum++;
		lines.addLine("%", Integer.toString(retAt), " = alloca i32, align 4");
		return retAt;
	}
	protected void allocate(String name) {
		lines.addLine("%", name, " = alloca i32, align 4");
	}
	protected void store(String what, String at) {
		lines.addLine("store i32 ", what, ", i32* %", at, ", align 4");
	}
	protected int load(String from) {
		int retAt = varNum++;
		lines.addLine("%", Integer.toString(retAt), " = load i32, i32* %", from, ", align 4");
		return retAt;
	}
	
	public List<String> getOutLines() {
		return lines.getOutLines();
	}
}
