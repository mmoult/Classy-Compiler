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
import classy.compiler.parsing.Assignment;
import classy.compiler.parsing.Block;
import classy.compiler.parsing.Expression;
import classy.compiler.parsing.If;
import classy.compiler.parsing.Literal;
import classy.compiler.parsing.Parameter;
import classy.compiler.parsing.Reference;
import classy.compiler.parsing.Tuple;
import classy.compiler.parsing.TypeDefinition;
import classy.compiler.parsing.Value;

public class Translator {
	protected int varNum = 1;
	protected int globalNum = 1;
	
	protected int inFunction = 0;
	// Two different write locations: in main function and outside
	protected LinePlacer main;
	protected LinePlacer outer;
	
	protected Map<Variable, String> varNames;
	protected Map<Type, String> typeMangle;
	private Checker checker = new Checker();
	Set<String> namesUsed = new HashSet<>();
	
	// to prevent magic numbers / strings
	private String voidPtr = "i8*";

	
	public Translator(Value program, List<Variable> vars, List<Type> types) {
		// Variables will receive a new name as they are assigned
		varNames = new HashMap<>();
		
		// We are going to want to map out all types and to what new name they will receive.
		//  We don't want any name conflicts (even if they are shadowed) so we will mangle the
		//  names to make some new name.
		typeMangle = new HashMap<>();
		namesUsed = new HashSet<>();
		
		for (Type type: types) {
			String name = type.getName();
			if (name == null)
				continue;
			
			String useName = mangle(name);
			namesUsed.add(useName);
			typeMangle.put(type, useName);
		}
		
		// Now begin the translation process
		translate(program, types);
	}
	
	private String mangle(String name) {
		String useName = cleanIdentifier(name);
		if (namesUsed.contains(name)) {
			// There was a collision, so we try another
			int cnt = 0;
			while (namesUsed.contains(name + cnt))
				cnt++;
			useName = name + cnt;
		}
		return useName;
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
		main = new LinePlacer(new ArrayList<>());
		outer = new LinePlacer(new ArrayList<>());
		main.deltaIndent(1);
		
		// Previously we could allocate space for the return before we continued.
		//  This is not possible with the inheritance tree we set up, since subclasses
		//  require more space than the super. Thus, the new translation model
		//  requires each step to return the pointer location of the return.
		String retAt = null;
		for (Expression e: program.getSubexpressions()) {
			retAt = translate(e);
		}
		
		// We need to understand the type of the result in order to print it
		Type result = check(program);
		// TODO: We will have to use a dynamic dispatch of toString, since we won't necessarily
		//  know statically that the variable is an int even if it is.
		main.addLine("call void @IntPrint(i8* ", retAt, ")");
		main.addLine("ret i32 0");
		main.deltaIndent(-1);
		main.addLine("}");
		
		// Lastly, add the function declaration top
		main.getTop();
		main.addLine("define dso_local i32 @main() {");
		
		// Define all the types that we used
		main.getTop();
		// Define the default types:
		for (Type t: types) {
			// create the struct with the name that mangling decided
			// %struct.Bar = type { %struct.Foo, %struct.Foo }
			StringBuilder typeLine = new StringBuilder("%");
			typeLine.append(typeMangle.get(t));
			typeLine.append(" = type { ");
			typeLine.append(voidPtr);
			// Now we append all the types that are parents of this type
			for (int i=0; t.getParents() != null && i < t.getParents().length; i++) {
				if (t.getParents()[i].equals(Type.Any))
					continue;
				typeLine.append(", %");
				typeLine.append(typeMangle.get(t.getParents()[i]));
				typeLine.append("*");
			}
			// Now we append all the fields of this type
			if (t.getFields() != null) {					
				for (String fieldName: t.getFields().keySet()) {
					typeLine.append(", %");
					typeLine.append(typeMangle.get(t.getFields().get(fieldName).getType()));
					typeLine.append("*");
				}
			}
			// If the field is a built-in, then we have some fields to add directly
			if (t.equals(Type.Int)) {
				typeLine.append(", ");
				typeLine.append("i32");
			}else if (t.equals(Type.Bool)) {
				typeLine.append(", ");
				typeLine.append("i1");
			}
			typeLine.append(" }");
			main.addLine(typeLine.toString());
		}
		main.addLine("");
		
		// Functions do not need to be declared before usage in LLVM, so we include the printi
		//  function at the very bottom (so during debugging, the file is easier to read)
		outer.addLine();
		loadLibrary("puts.ll");
		loadLibrary("printi.ll");
		loadLibrary("defprint.ll");
	}
	
	protected void loadLibrary(String libName) {
		Scanner scan = null;
		try {
			scan = new Scanner(new File("libs/" + libName));
			while (scan.hasNextLine())
				outer.addLine(scan.nextLine());
		} catch (FileNotFoundException e1) {
			throw new RuntimeException("Could not find requisite file: \"libs/" + libName + "!");
		} finally {
			if (scan != null)
				scan.close();
		}
	}
	
	/*
	 * Translates the expression and returns the pointer location of the result.
	 */
	protected String translate(Expression e) {
		LinePlacer lines;
		if (inFunction > 0)
			lines = outer;
		else
			lines = main;
		
		if (e instanceof Value) {
			return translate(((Value)e).getSubexpressions().get(0));
		}else if (e instanceof Block) {
			Block block = (Block)e;
			String retAt = null;
			for (Expression be : block.getBody())
				retAt = translate(be);
			return retAt;
		}else if (e instanceof If) {
			If if_ = (If)e;
			String retAt;
			// We want to find the result of the condition, then jump from there
			String cond = translate(if_.getCondition());
			// TODO We need to get the boolean value from the return.
			// We must find the boolean dynamically. There is no other way.
			// For now we punt and assume the condition equals a Bool.
			String tName = typeMangle.get(Type.Bool);
			String fromBool = ""+varNum++;
			lines.addLine("%", fromBool, " = getelementptr inbounds %", tName, ", %", tName, "* ", cond, ", i32 0, i32 1");
			int loaded = load(fromBool, "i1", lines);
			
			//int compare = varNum++;
			//lines.addLine("%", Integer.toString(compare), " = icmp eq i32 %", Integer.toString(loaded),", 0");
			String tbranch = "then" + Integer.toString(loaded);
			String fbranch = "else" + Integer.toString(loaded);
			String next = "next" + Integer.toString(loaded);
			// branch to either the true or false case
			lines.addLine("br i1 %", Integer.toString(loaded), ", label %", fbranch, ", label %", tbranch);
			lines.addLine();
			
			lines.addLabel(tbranch);
			retAt = translate(if_.getThen());
			lines.addLine("br label %", next);
			
			lines.addLabel(fbranch);
			retAt = translate(if_.getElse());
			lines.addLine("br label %", next);
			
			lines.addLabel(next);
			return retAt;
		}else if (e instanceof Literal) {
			// Literals can live statically. We want to make it global so that it has
			//  infinite scope, (since we don't know how it will be used).
			return setGlobalLiteral((Literal)e);
		} else if (e instanceof TypeDefinition) {
			return null;
		}else if (e instanceof Assignment) {
			Assignment asgn = (Assignment)e;
			
			// First, we must find if this is a value assignment or a function assignment
			if (asgn.getParamList() == null) {
				//allocate(check(asgn.getValue()), name);
				// We will get the location of the value at got
				String got = translate(asgn.getValue());
				// Then we need to save that we are at got
				varNames.put(asgn.getSourced(), got);
			}else {
				// We are going to make a function declaration, which needs to be on the top
				//  level. Thus, we start at the top scope, saving our old location to revert
				//  back after
				int prevVarNum = this.varNum;
				this.varNum = 1;
				this.inFunction++;
				LinePlacer.State oldState = outer.getTop();
				
				// We want to mangle the function name to make sure there are no overlaps
				String name = mangle(asgn.getVarName());
				varNames.put(asgn.getSourced(), name);
				
				String[] lineCmps = new String[4 + (2 * asgn.getParamList().size())];
				lineCmps[0] = "define dso_local " + voidPtr + " @";
				lineCmps[1] = name;
				lineCmps[2] = "(";
				int i = 3;
				for (Parameter parameter: asgn.getParamList()) {
					String param = varNames.get(parameter.getSourced());
					if (i > 3)
						lineCmps[i++] = ", " + voidPtr + " %";
					else
						lineCmps[i++] = voidPtr + " %";
					lineCmps[i++] = param;
				}
				lineCmps[i++] = ") {";
				outer.addLine();
				outer.addLine(lineCmps);
				outer.deltaIndent(1);
				
				String fRet = translate(asgn.getValue());
				outer.addLine("ret ", voidPtr, " ", fRet);
				outer.deltaIndent(-1);
				outer.addLine("}");
				
				// then the function declaration is done. Restore the state
				outer.revertState(oldState);
				this.varNum = prevVarNum;
				this.inFunction--;
			}
			return null;
		}else if (e instanceof Reference) {
			// Reference needs to give the return address of the location
			Reference ref = (Reference)e;
			String name = varNames.get(ref.getLinkedTo());
			if (name == null)
				throw new RuntimeException("Reference encountered without a name in translation!");
			if (ref.getArgument() == null) {
				// Regular reference
				return name;
			}/*else {
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
			} */
		/*
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
			*/
		}
		// If it was not one of those types, through an error
		throw new RuntimeException("Expression " + e.toString() + " could not be translated!");
	}
	
	protected String setGlobalLiteral(Literal lit) {
		String name = "@l" + globalNum++;
		LinePlacer.State oldMain = main.getTop();
		main.deltaIndent(1);
		LinePlacer.State oldOuter = outer.getTop();
		
		// Switch on the type of literal we are allocating here
		Type litType;
		String irName;
		switch (lit.getToken().getType()) {
		case NUMBER:
			litType = Type.Int;
			irName = "i32";
			break;
		case TRUE:
		case FALSE:
			litType = Type.Bool;
			irName = "i1";
			break;
		default:
			throw new RuntimeException("Unrecognized literal type!");
		}
		
		String tName = typeMangle.get(litType);
		// Create the literal in global scope of outer
		// global %Int zeroinitializer, align 8
		outer.addLine(name, " = global %", tName, " zeroinitializer, align 8");
		
		// Initialize the literal at the beginning of main
		//%5 = getelementptr inbounds %struct.Foo, %struct.Foo* %2, i32 0, i32 1
		String atIndex = "%li" + globalNum;
		// We assume here that the first index of all literal types is the actual value
		main.addLine(atIndex, " = getelementptr inbounds %", tName, ", %", tName, "* ", name, ", i32 0, i32 1");
		store(lit.getToken().getValue()+"", irName, atIndex, main);
		// to back to where we were
		outer.revertState(oldOuter);
		main.revertState(oldMain);
		
		// For translation purposes, we need to make the type of the return generic
		String gName = "%" + varNum++;
		LinePlacer curr = inFunction == 0 ? main : outer;
		curr.addLine(gName, " = bitcast %", tName, "* ", name, " to ", voidPtr);
		return gName;
	}
	
	/*
	protected int allocate(Type t) {
		int retAt = varNum++;
		allocate(t, retAt+"");
		return retAt;
	}
	protected void allocate(Type t, String name) {
		lines.addLine("%", name, " = alloca %", typeMangle.get(t), ", align 4");
	}
	*/
	
	protected void store(String what, String type, String at, LinePlacer lines) {
		lines.addLine("store ", type, " ", what, ", ", type, "* ", at, ", align 4");
	}
	protected int load(String from, String type, LinePlacer lines) {
		int retAt = varNum++;
		lines.addLine("%", Integer.toString(retAt), " = load ", type, ", ", type, "* ", from, ", align 4");
		return retAt;
	}
	
	public List<String> getOutLines() {
		List<String> lines = main.getOutLines();
		lines.add("");
		lines.addAll(outer.getOutLines());
		return lines;
	}
	
	private Type check(Expression e) {
		return checker.check(e, new ArrayList<>());
	}
}
