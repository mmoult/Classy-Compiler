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

import classy.compiler.analyzing.Checker;
import classy.compiler.analyzing.Type;
import classy.compiler.analyzing.Variable;
import classy.compiler.parsing.Tuple;
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.BinOp;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Operation;
import classy.compiler.parsing.Parameter;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Subexpression;
import classy.compiler.parsing.Value;

public class Translator {
	protected int varNum = 1;
	protected int inFunction = 0;
	protected LinePlacer lines;
	protected Map<Variable, String> varMangle;
	protected Map<Type, String> typeMangle;
	private Checker checker = new Checker();

	
	public Translator(Value program, List<Variable> vars, List<Type> types) {
		// We are going to want to map out all variables and to what new name they will receive.
		//  We don't want any name conflicts (even if they are shadowed) so we will mangle the
		//  names to make some new name.
		// Both assignments and references will be mapped to that new name.
		varMangle = new HashMap<>();
		typeMangle = new HashMap<>();
		Set<String> namesUsed = new HashSet<>();
		
		// Some default types need to be created
		namesUsed.add(Type.Int.getName());
		typeMangle.put(Type.Int, Type.Int.getName());
		namesUsed.add(Type.Bool.getName());
		typeMangle.put(Type.Bool, Type.Bool.getName());
		
		for (Variable var: vars) {
			String name = var.getName();
			String useName = cleanIdentifier(name);
			if (namesUsed.contains(name)) {
				// There was a collision, so we try another
				int cnt = 0;
				while (namesUsed.contains(name + cnt)) {
					cnt++;
				}
				useName = name + cnt;
			}
			// Great! we found a name we can use.
			namesUsed.add(useName);
			varMangle.put(var, useName);
		}
		
		// Do the same thing for types
		for (Type type: types) {
			String name = type.getName();
			if (name == null)
				continue;
			
			String useName = cleanIdentifier(name);
			if (namesUsed.contains(name)) {
				// There was a collision, so we try another
				int cnt = 0;
				while (namesUsed.contains(name + cnt)) {
					cnt++;
				}
				useName = name + cnt;
			}
			// Great! we found a name we can use.
			namesUsed.add(useName);
			typeMangle.put(type, useName);
		}
		
		// Now begin the translation process
		translate(program, types);
	}
	
	protected String cleanIdentifier(String dirty) {
		// remove all spaces or non-numeric or alphabetic characters
		StringBuffer clean = new StringBuffer();
		for (int i=0; i<dirty.length(); i++) {
			char c = dirty.charAt(i);
			if ((c >= '0' && c <= '9') || c == '_' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z'))
				clean.append(c);
		}
		return clean.toString();
	}
	
	public void translate(Value program, List<Type> types) {		
		// insert the printi.ll file, which is needed to print the result of main
		Scanner scan = null;
		try {
			lines = new LinePlacer(new ArrayList<>());
			lines.addLine("define dso_local i32 @main() {");
			lines.deltaIndent(1);
			// We want to put the return in retAt
			// But that requires us to know the type that the return will be
			Type result = check(program);
			int retAt = allocate(result);
			//
			for (Expression e: program.getSubexpressions()) {
				translate(e, ""+retAt);
			}
			//
			int ret = load(""+retAt);
			// If the result type is a number, then print it out
			if (result.isa(Type.Int))
				lines.addLine("call void @printi(i32 %", Integer.toString(ret), ")");
			// TODO: We want to have some print for other types too
			lines.addLine("ret i32 0");
			lines.deltaIndent(-1);
			lines.addLine("}");
			
			// Lastly, define all the types that we used
			LinePlacer.State oldState = lines.getTop();
			// Define the default types:
			// TODO here last
			for (Type t: types) {
				// create the struct with the name that mangling decided
				// %struct.Bar = type { %struct.Foo, %struct.Foo }
				lines.addLine("%", typeMangle.get(t), " = type { ", /* give types their fields */ "}");
			}
			lines.revertState(oldState);
			
			// Functions do not need to be declared before usage in LLVM, so we include the printi
			//  function at the very bottom (so during debugging, the file is easier to read)
			scan = new Scanner(new File("libs/printi.ll"));
			while (scan.hasNextLine())
				lines.addLine(scan.nextLine());
			scan.close();
		} catch (FileNotFoundException e1) {
			throw new RuntimeException("Could not find requisite file: \"libs/itos.ll\"!");
		}
	}
	
	protected void translate(Expression e, String retAt) {
		if (e instanceof Value) {
			translate(((Value)e).getSubexpressions().get(0), retAt);
		}else if (e instanceof Block) {
			Block block = (Block)e;
			for (Expression be : block.getBody())
				translate(be, retAt);
		}else if (e instanceof If) {
			If if_ = (If)e;
			// We want to find the result of the condition, then jump from there
			int cond = allocate(check(if_.getCondition()));
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
			String name = varMangle.get(asgn.getSourced());
			if (name == null)
				throw new RuntimeException("Assignment encountered without a name in translation!");
			
			// First, we must find if this is a value assignment or a function assignment
			if (asgn.getParamList() == null) {
				allocate(check(asgn.getValue()), name);
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
				for (Parameter parameter: asgn.getParamList()) {
					String param = varMangle.get(parameter.getSourced());
					if (i > 3)
						lineCmps[i++] = ", i32 %";
					else
						lineCmps[i++] = "i32 %";
					lineCmps[i++] = param;
				}
				lineCmps[i++] = ") {";
				lines.addLine(lineCmps);
				lines.deltaIndent(1);
				int fRet = allocate(check(asgn.getValue()));
				
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
			String name = varMangle.get(ref.getLinkedTo());
			if (name == null)
				throw new RuntimeException("Reference encountered without a name in translation!");
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
				if (argument.getSubexpressions().get(0) instanceof Tuple) {
					Tuple ls = (Tuple)argument.getSubexpressions().get(0);
					args = ls.getArgs().toArray(new Value[] {});
				}else
					args = new Value[] {argument};
				
				int[] argsAt = new int[args.length];
				int i=0;
				for (Value arg: args) {
					int alloc = allocate(check(arg));
					translate(arg, alloc+"");
					argsAt[i] = load(alloc+"");
					i++;
				}
				
				int returned = varNum++;
				String[] callComps = new String[6 + 2*args.length];
				callComps[0] = "%";
				callComps[1] = Integer.toString(returned);
				callComps[2] = " = call i32 @";
				callComps[3] = name;
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
			// We need to handle 'or' and 'and' first, since they could short circuit
			if (e instanceof BinOp.And || e instanceof BinOp.Or) {
				BinOp bop = (BinOp)e;
				boolean bothNeeded = e instanceof BinOp.And;
				// We evaluate the left first, and then we could short circuit and dodge the second
				String lhs;
				List<Subexpression> opLhs = bop.getLHS().getSubexpressions();
				if (opLhs.size() == 1 && opLhs.get(0) instanceof Literal) {
					// We can optimize if it is an int literal by a direct output
					lhs = ((Literal)opLhs.get(0)).getToken().getValue();
					// We want to find the truth of the literal
					boolean firstTrue = Integer.parseInt(lhs) != 0;
					if (!bothNeeded && firstTrue) {
						store("1", retAt);
						return; // we can quit early, since the operation has been decided
					}else if (bothNeeded && !firstTrue) {
						store("0", retAt);
						return; 
					}
				}else {
					int la = allocate(check(bop.getLHS()));
					translate(bop.getLHS(), la+"");
					int ll = load(la+"");
					lhs = "%" + ll;
				}
				
				// If we got here, then we cannot know the result of this expression until
				// runtime. That means that we need a branching mechanism
				int cmp = varNum++;
				lines.addLine("%", Integer.toString(cmp), " = icmp ne i32 ", lhs, ", ", 0+"");
				//br i1 %6, label %7, label %8
				int truth = varNum++;
				int falsity = varNum++;
				lines.addLine("br i1 %", cmp+"", ", label %", truth+"", ", label %", falsity+"");
				lines.addLabel(truth+"");
				if (bothNeeded) {
					// We must verify that rhs is also true
					List<Subexpression> opRhs = bop.getLHS().getSubexpressions();
					if (opRhs.size() == 1 && opRhs.get(0) instanceof Literal) {
						// We can optimize if it is an int literal by a direct output
						String rhs = ((Literal)opRhs.get(0)).getToken().getValue();
						// We want to find the truth of the literal
						boolean secondTrue = Integer.parseInt(rhs) != 0;
						if (secondTrue)
							store("1", retAt);
					}else {
						int ra = allocate(check(bop.getRHS()));
						translate(bop.getRHS(), ra+"");
						int rr = load(ra+"");
						String rhs = "%" + rr;
						// If rhs != 0, then the whole expression is true
						int res = varNum++;
						lines.addLine("%", Integer.toString(res), " = icmp ne i32 ", rhs, ", ", 0+"");
						int expand = varNum++;
						lines.addLine("%", Integer.toString(expand), " = zext i1 %", Integer.toString(res), " to i32");
						store("%"+expand, retAt);
					}
				}else 
					// We know the whole expression is true since we don't need both
					store("1", retAt);
				// Lastly, go to the continuation label
				lines.addLine("br label %sc", cmp+"");
				lines.addLabel(falsity+"");
				if (bothNeeded)
					// We already had one failure, so the whole expression is false
					store("0", retAt);
				else {
					List<Subexpression> opRhs = bop.getLHS().getSubexpressions();
					if (opRhs.size() == 1 && opRhs.get(0) instanceof Literal) {
						// We can optimize if it is an int literal by a direct output
						String rhs = ((Literal)opRhs.get(0)).getToken().getValue();
						// We want to find the truth of the literal
						boolean secondTrue = Integer.parseInt(rhs) != 0;
						if (secondTrue)
							store("1", retAt);
					}else {
						int ra = allocate(check(bop.getRHS()));
						translate(bop.getRHS(), ra+"");
						int rr = load(ra+"");
						String rhs = "%" + rr;
						// If rhs != 0, then the whole expression is true
						int res = varNum++;
						lines.addLine("%", Integer.toString(res), " = icmp ne i32 ", rhs, ", ", 0+"");
						int expand = varNum++;
						lines.addLine("%", Integer.toString(expand), " = zext i1 %", Integer.toString(res), " to i32");
						store("%"+expand, retAt);
					}
				}
				// Lastly, go to the continuation label
				lines.addLine("br label %sc", cmp+"");
				// Finally, put the continuation label
				lines.addLabel("sc" + cmp);
				return; // We don't want to go to regular processing after
			}
			
			Operation op = (Operation)e;
			String rhs;
			List<Subexpression> opRhs = op.getRHS().getSubexpressions();
			if (opRhs.size() == 1 && opRhs.get(0) instanceof Literal) {
				// We can optimize if it is an int literal by a direct output
				rhs = ((Literal)opRhs.get(0)).getToken().getValue();
			}else {
				int ra = allocate(check(op.getRHS()));
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
					int la = allocate(check(bop.getLHS()));
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
	
	protected int allocate(Type t) {
		int retAt = varNum++;
		allocate(t, retAt+"");
		return retAt;
	}
	protected void allocate(Type t, String name) {
		lines.addLine("%", name, " = alloca ", typeMangle.get(t), ", align 4");
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
	
	private Type check(Expression e) {
		return checker.check(e, List.of());
	}
}
