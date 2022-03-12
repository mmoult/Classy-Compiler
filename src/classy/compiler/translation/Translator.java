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

import classy.compiler.analyzing.ParameterType;
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
	protected LinePlacer lines;
	
	protected Map<Variable, String> varNames;
	protected Map<Type, OutType> outTypes;
	//private Checker checker = new Checker();
	Set<String> namesUsed = new HashSet<>();
	
	// to prevent magic numbers / strings
	private String voidPtr = "i8*";
	private String tagType = "i32";

	
	public Translator(Value program, List<Variable> vars, List<Type> types) {
		// Variables will receive a new name as they are assigned
		varNames = new HashMap<>();
		
		// We are going to want to map out all types and to what new name they will receive.
		//  We don't want any name conflicts (even if they are shadowed) so we will mangle the
		//  names to make some new name.
		outTypes = new HashMap<>();
		namesUsed = new HashSet<>();
		int typeNum = 0;
		
		for (Type type: types) {
			String name = type.getName();
			if (name == null)
				continue;
			
			String useName = mangle(name);
			OutType outType = new OutType(type, useName, ++typeNum);
			outTypes.put(type, outType);
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
		namesUsed.add(useName);
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
		lines = new LinePlacer(new ArrayList<>());
		lines.addLine();
		lines.addLine("define dso_local i32 @main() {");
		lines.deltaIndent(1);
		
		// Previously we could allocate space for the return before we continued.
		//  This is not possible with the inheritance tree we set up, since subclasses
		//  require more space than the super. Thus, the new translation model
		//  requires each step to return the pointer location of the return.
		String retAt = null;
		for (Expression e: program.getSubexpressions()) {
			retAt = translate(e);
		}
		
		// TODO: We will have to use a dynamic dispatch of toString, since we won't necessarily
		//  know statically that the variable is an int even if it is.
		lines.addLine("call void @..print(i8* ", retAt, ")");
		lines.addLine("ret i32 0");
		lines.deltaIndent(-1);
		lines.addLine("}");
		
		// Define all the types that we used
		LinePlacer.State old = lines.getTop();
		// Define the default types:
		for (Type t: types) {
			// create the struct with the name that mangling decided
			// %struct.Bar = type { %struct.Foo, %struct.Foo }
			StringBuilder typeLine = new StringBuilder("%");
			OutType type = outTypes.get(t);
			typeLine.append(type.mangledName);
			typeLine.append(" = type { ");
			typeLine.append(tagType);
			// Now we append all the types that are parents of this type
			for (int i=0; t.getParents() != null && i < t.getParents().length; i++) {
				if (t.getParents()[i].equals(Type.Any))
					continue;
				typeLine.append(", %");
				typeLine.append(outTypes.get(t.getParents()[i]).mangledName);
				typeLine.append("*");
			}
			// Now we append all the fields of this type
			if (t.getFields() != null) {
				Map<String, Variable> fields = t.getFields();
				for (String fieldName: t.getFields().keySet()) {
					typeLine.append(", %");
					typeLine.append(outTypes.get(fields.get(fieldName).getType()).mangledName);
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
			lines.addLine(typeLine.toString(), "; Type ID = ", type.typeNum+"");
		}
		
		// We must process all of the types before any dynamic dispatch methods
		for (Type t: types) {
			// We also want to translate all the methods of the type
			Map<String, Map<String, List<String>>> typeLibrary = new HashMap<>();
			if (t.getMethods() != null) {
				varNum = 1;
				Map<String, Variable> methods = t.getMethods();
				for (String methodName: methods.keySet()) {
					Variable method = methods.get(methodName);
					// If this method overrides another, then don't print it
					if (!method.isOverridden())
						continue;
					// We will print the dynamic dispatch of this method and all overrides
					
					StringBuffer decl = new StringBuffer();
					Type fxType = method.getType();
					decl.append("define dso_local ");
					if (fxType.getOutput() != null)
						decl.append(voidPtr);
					else
						decl.append("void");
					decl.append(" @");
					decl.append(methodName);
					decl.append("(");
					boolean first = true;
					for (ParameterType ptype: fxType.getInputs()) {
						if (first)
							first = false;
						else
							decl.append(", ");
						decl.append(voidPtr);
						decl.append(" %");
						decl.append(ptype.getName());
					}
					decl.append(") {");
					lines.addLine(decl.toString());
					lines.deltaIndent(1);
					
					// Here is where we want to print the dynamic dispatch part
					// If the calling type does not match any of our options,
					//  then it falls through to this implementation
					String casted = "%" + varNum++;
					lines.addLine(casted, " = bitcast i8* %this to %Any*");
					String tagAt = "%" + varNum++;
					lines.addLine(tagAt, " = getelementptr inbounds %Any, %Any* ", casted, ", i32 0, i32 0");
					String tag = "%" + load(tagAt, "i32", "4");
					
					for (Variable override : method.getOverrides()) {
						// Get the type number for this variable
						Type thisType = override.getType().getInputs()[0].getType();
						OutType outType = outTypes.get(thisType);
						
						String cmp = "%" + varNum++;
						lines.addLine(cmp, " = icmp eq i32 ", tag, ", " + outType.typeNum);
						//br i1 %6, label %7, label %8
						String match = "is" + outType.mangledName;
						String next = "next" + varNum;
						lines.addLine("br i1 ", cmp, ", label %", match, ", label %" + next);
						lines.addLabel(match);
						
						// Translate the override
						translateOverride(override, typeLibrary);
						lines.addLabel(next);
					}
					
					// Translate method.value
					translateOverride(method, typeLibrary);
					lines.deltaIndent(-1);
					lines.addLine("}");
				}
			}
		}
		lines.revertState(old);
		lines.addLine();
	}
	
	protected void translateOverride(Variable override, Map<String, Map<String, List<String>>> typeLibrary) {
		if (override.getValue() != null) {
			String retAt = translate(override.getValue());
			lines.addLine("ret ", voidPtr, retAt);
		}else { // If it is null, then we assume it is saved as a built-in library
			Type t = override.getType().getInputs()[0].getType();
			if (!typeLibrary.containsKey(t.getName()))
				typeLibrary.put(t.getName(), loadLibrary(t.getName() + ".ll"));
			Map<String, List<String>> forType = typeLibrary.get(t.getName());
			if (!forType.containsKey(override.getName()))
				throw new RuntimeException("Missing library function of " + 
						override.getName() + " for class " + t.getName() + "!");
			
			List<String> thisMethod = forType.get(override.getName());
			lines.deltaIndent(-1);
			Map<String, String> fixLabels = new HashMap<>();
			for (String str : thisMethod) {
				// One little complication is that we need to intercept register values
				//  and print out fixed ones instead. The implementation has no idea what
				//  number is next, so any numbered registers will be wrong
				int percentAt = str.indexOf('%', 0);
				while (percentAt != -1) {
					int start = percentAt;
					// Find if this is a numbered label
					boolean numbered = false;
					while(++percentAt < str.length()) {
						char c = str.charAt(percentAt);
						if (c == ' ' || c == ',' || c == ')')
							break;
						if (c >= '0' && c <= '9')
							numbered = true;
						else {
							numbered = false;
							break;
						}
					}
					if (numbered) {
						String register = str.substring(start, percentAt);
						String fixed;
						if (fixLabels.containsKey(register))
							fixed = fixLabels.get(register);
						else {
							fixed = "%" + varNum++;
							fixLabels.put(register, fixed);
						}
						str = str.substring(0, start) + fixed + str.substring(percentAt);
					}
					
					percentAt = str.indexOf('%', start + 1);
				}
				lines.addLine(str);
			}
			lines.deltaIndent(1);
		}
	}
	
	protected Map<String, List<String>> loadLibrary(String libName) {
		Map<String, List<String>> library = new HashMap<>();
		Scanner scan = null;
		try {
			scan = new Scanner(new File("libs/" + libName));
			// All lines that are not in a function we want to print out immediately.
			// However, some lines may be bound to a function, which we want to save until that
			//  function is being translated.
			boolean inFunction = false;
			List<String> currFunction = null;
			String fxName = null;
			LinePlacer.State old = lines.getTop();
			while (scan.hasNextLine()) {
				String line = scan.nextLine();
				if (inFunction) {
					if (line.indexOf("}") != -1) {
						// end of the function
						inFunction = false;
						library.put(fxName, currFunction);
					}else {
						currFunction.add(line);
					}
				}else {					
					if (line.indexOf("FUNCTION") == 0) {
						// We are starting a function definition
						fxName = line.substring(9, line.indexOf(' ', 10));
						currFunction = new ArrayList<>();
						inFunction = true;
					}else
						lines.addLine(line);
				}
			}
			lines.revertState(old);
		} catch (FileNotFoundException e1) {
			throw new RuntimeException("Could not find requisite file: \"libs/" + libName + "!");
		} finally {
			if (scan != null)
				scan.close();
		}
		return library;
	}
	
	/*
	 * Translates the expression and returns the pointer location of the result.
	 */
	protected String translate(Expression e) {
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
			// We want to find the result of the condition, then jump from there
			String cond = translate(if_.getCondition());
			// TODO We need to get the boolean value from the return.
			// We must find the boolean dynamically. There is no other way.
			// For now we punt and assume the condition equals a Bool.
			String tName = outTypes.get(Type.Bool).mangledName;
			int toBool = varNum++;
			// cast to what we need (bool) before use
			lines.addLine("%" + toBool, " = bitcast ", voidPtr, " ", cond, " to %", tName, "*");
			String inBool = "%"+varNum++;
			lines.addLine(inBool, " = getelementptr inbounds %", tName, ", %", tName, "* %" + toBool, ", i32 0, i32 1");
			int loaded = load(inBool, "i1", "4");
			
			//int compare = varNum++;
			//lines.addLine("%", Integer.toString(compare), " = icmp eq i32 %", Integer.toString(loaded),", 0");
			String tbranch = "then" + Integer.toString(loaded);
			String fbranch = "else" + Integer.toString(loaded);
			String next = "next" + Integer.toString(loaded);
			// branch to either the true or false case
			lines.addLine("br i1 %", Integer.toString(loaded), ", label %", tbranch, ", label %", fbranch);
			lines.addLine();
			
			lines.addLabel(tbranch);
			String thenAt = translate(if_.getThen());
			lines.addLine("br label %", next);
			
			lines.addLabel(fbranch);
			String elseAt = translate(if_.getElse());
			lines.addLine("br label %", next);
			
			lines.addLabel(next);
			String retAt = "%" + varNum++;
			lines.addLine(retAt, " = phi ", voidPtr, " [ ", thenAt, ", %", tbranch,
					" ], [ ", elseAt, ", %", fbranch, " ]");
			return retAt;
		}else if (e instanceof Literal) {
			// Literals can live statically. We want to make it global so that it has
			//  infinite scope, (since we don't know how it will be used).
			return setGlobalLiteral((Literal)e);
		} else if (e instanceof TypeDefinition) {
			return null;
		}else if (e instanceof Assignment) {
			Assignment asgn = (Assignment)e;
			Variable asgnVar = asgn.getSourced();
			
			// First, we must find if this is a value assignment or a function assignment
			if (!asgnVar.getType().isFunction()) {
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
				LinePlacer.State oldState = lines.getTop();
				
				// We want to mangle the function name to make sure there are no overlaps
				String name = mangle(asgn.getVarName());
				varNames.put(asgn.getSourced(), name);
				
				String[] lineCmps = new String[4 + (2 * asgn.getParamList().size())];
				lineCmps[0] = "define dso_local " + voidPtr + " @";
				lineCmps[1] = name;
				lineCmps[2] = "(";
				int i = 3;
				for (Parameter parameter: asgn.getParamList()) {
					String paramName = mangle(parameter.getName());
					varNames.put(parameter.getSourced(), "%" + paramName);
					if (i > 3)
						lineCmps[i++] = ", " + voidPtr + " %";
					else
						lineCmps[i++] = voidPtr + " %";
					lineCmps[i++] = paramName;
				}
				lineCmps[i++] = ") {";
				lines.addLine();
				lines.addLine(lineCmps);
				lines.deltaIndent(1);
				
				String fRet = translate(asgn.getValue());
				lines.addLine("ret ", voidPtr, " ", fRet);
				lines.deltaIndent(-1);
				lines.addLine("}");
				
				// then the function declaration is done. Restore the state
				lines.revertState(oldState);
				this.varNum = prevVarNum;
				this.inFunction--;
			}
			return null;
		}else if (e instanceof Reference) {
			// Reference needs to give the return address of the location
			Reference ref = (Reference)e;
			String name = varNames.get(ref.getLinkedTo());
			if (name == null)
				throw new RuntimeException("Reference \"" + name + "\" encountered without a name in translation!");
			if (ref.getArgument() == null) {
				// Regular reference
				return name;
			}else {
				// Function call
				Value argument = ref.getArgument();
				Value[] args;
				if (argument.getSubexpressions().get(0) instanceof Tuple) {
					Tuple ls = (Tuple)argument.getSubexpressions().get(0);
					args = ls.getArgs().toArray(new Value[] {});
				}else
					args = new Value[] {argument};
				
				String[] argsAt = new String[args.length];
				int i=0;
				for (Value arg: args) {
					argsAt[i] = translate(arg);
					i++;
				}
				
				int returned = varNum++;
				String[] callComps = new String[6 + 2*args.length];
				callComps[0] = "%";
				callComps[1] = Integer.toString(returned);
				callComps[2] = " = call " + voidPtr + " @";
				callComps[3] = name;
				callComps[4] = "(";
				int j = 5;
				for (i=0; i<args.length; i++) {
					if (j == 5) // first
						callComps[j++] = voidPtr + " ";
					else
						callComps[j++] = ", " + voidPtr + " ";
					callComps[j++] = argsAt[i];
				}
				callComps[j] = ")";
				lines.addLine(callComps);
				return "%" + returned;
			}
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
	
	protected String constructObj(OutType type, String fromGlobal) {
		if (fromGlobal == null) {
			//%4 = alloca %struct.Foo*, align 8
			fromGlobal = "%" + allocate(type);
		}
		// Set the type of the object
		String atIndex = "%" + varNum++;
		// The type will be stored at the 0th index of the struct
		lines.addLine(atIndex, " = getelementptr inbounds %", type.mangledName, ", %",
				type.mangledName, "* ", fromGlobal, ", i32 0, i32 0");
		store(type.typeNum+"", tagType, "4", atIndex); // store the number as the tag id
		
		return fromGlobal;
	}
	
	protected String setGlobalLiteral(Literal lit) {
		String name = "@l" + globalNum++;
		LinePlacer.State oldState = lines.getTop();
		
		// Switch on the type of literal we are allocating here
		Type litType;
		String irName;
		String alignment = "4";
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
		
		OutType outType = outTypes.get(litType);
		String tName = outType.mangledName;
		// Create the literal in global scope of outer
		// global %Int zeroinitializer, align 8
		lines.addLine(name, " = global %", tName, " zeroinitializer, align 8");
		
		// Initialize the literal before use
		// go back to where we were
		lines.revertState(oldState);
		
		constructObj(outType, name); // sets up its type id flag and what not
		//%5 = getelementptr inbounds %struct.Foo, %struct.Foo* %2, i32 0, i32 1
		String atIndex = "%" + varNum++;
		// We assume here that the first index of all literal types is the actual value
		lines.addLine(atIndex, " = getelementptr inbounds %", tName, ", %", tName, "* ", name, ", i32 0, i32 1");
		store(lit.getToken().getValue()+"", irName, alignment, atIndex);
		
		// For translation purposes, we need to make the type of the return generic
		String gName = "%" + varNum++;
		lines.addLine(gName, " = bitcast %", tName, "* ", name, " to ", voidPtr);
		return gName;
	}
	
	protected int allocate(OutType t) {
		int retAt = varNum++;
		allocate(t, retAt+"");
		return retAt;
	}
	protected void allocate(OutType type, String saveAt) {
		lines.addLine("%", saveAt, " = alloca %", type.mangledName, ", align ", ""+type.alignment);
	}
	
	protected void store(String what, OutType type, String at) {
		store(what, type.mangledName, type.alignment+"", at);
	}
	protected void store(String what, String type, String alignment, String at) {
		lines.addLine("store ", type, " ", what, ", ", type, "* ", at, ", align ", alignment);
	}
	
	protected int load(String from, OutType type) {
		return load(from, type.mangledName, type.alignment+"");
	}
	protected int load(String from, String type, String alignment) {
		int retAt = varNum++;
		lines.addLine("%", Integer.toString(retAt), " = load ", type, ", ", type, "* ", from, ", align ", alignment);
		return retAt;
	}
	
	public List<String> getOutLines() {
		return lines.getOutLines();
	}
}
