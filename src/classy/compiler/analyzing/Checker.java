package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import classy.compiler.lexing.Token;
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

public class Checker {
	protected List<Variable> variables = new ArrayList<>();
	
	public Checker(Value program, boolean optimize) {
		check(program, optimize);
	}
	
	public void check(Value program, boolean optimize) {
		// We need to check variable references and reuse:
		//  There cannot be two variables with the same name in the same scope.
		//  If there is only one usage of a variable, it can be replaced with the value.
		
		List<Map<String, Variable>> environment = new ArrayList<>();
		environment.add(new TreeMap<String, Variable>());
		check(program, environment, optimize);
		
		if (!optimize)
			return;
		
		List<Variable> removeList = new ArrayList<>();
		for (Variable var: variables) {
			if (var.references.isEmpty())
				removeList.add(var);
			// If there is only one usage, and the variable is set (not a function param)
			else if (var.references.size() == 1 && var.value != null && var.source.getParamList() == null) {
				// Replace the reference with the value
				Reference ref = var.references.get(0);
				List<Subexpression> subList = ref.getParent().getSubexpressions();
				int replaceAt = subList.indexOf(ref);
				subList.remove(replaceAt);
				subList.addAll(replaceAt, var.value.getSubexpressions());
				
				// get rid of the assignment for an unused variable
				removeList.add(var);
			}
		}
		for (Variable var: removeList) {
			// We need to delete it at its source first
			Block parent = var.source.getParent();
			parent.getBody().remove(var.source);
			parent.reduce();
			// then we can remove it from the variables list to complete the change
			variables.remove(var);
		}
	}
	
	protected void check(Expression e, List<Map<String, Variable>> env, boolean optimize) {
		// dispatch on the appropriate checking function
		if (e instanceof Assignment)
			check((Assignment)e, env, optimize);
		if (e instanceof Reference)
			check((Reference)e, env, optimize);
		if (e instanceof Block)
			check((Block)e, env, optimize);
		if (e instanceof If)
			check((If)e, env, optimize);
		if (e instanceof Operation)
			check((Operation)e, env, optimize);
		if (e instanceof Value)
			check((Value)e, env, optimize);
		
		// Optimizations begin after checking
		if (optimize) {
			if (e instanceof Operation)
				optimize((Operation)e);
		}
	}
	
	public List<Variable> getVariables() {
		return variables;
	}
	
	protected void check(Assignment asgn, List<Map<String, Variable>> env, boolean optimize) {
		// There cannot be two variables with the same name in the same scope
		// TODO we will need to modify this for different signatures...
		Map<String, Variable> curScope = env.get(env.size() - 1);
		String name = asgn.getVarName();
		if (curScope.containsKey(name)) {
			Expression firstInstance = curScope.get(name).source;
			throw new CheckException("Multiple definitions of same variable name \"",
					name, " found in the same scope! First instance: ", firstInstance,
					" and second instance: ", asgn);
		}
		
		// If the parameter list is null, then this is simply a variable. Otherwise, this is
		// a function definition. 
		// Function definitions are special since the value can have references to itself.
		// Also, it needs to add the parameters in a scope that contains only the value
		if (asgn.getParamList() != null) {
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.put(name, var);
			env.add(new TreeMap<>());
			Map<String, Variable> fxScope = env.get(env.size() - 1);
			// We cannot optimize away parameters, and thus they have null parent
			for (String param: asgn.getParamList()) {
				Variable paramVar = new Variable(param, null, null);
				variables.add(paramVar);
				fxScope.put(param, paramVar);
			}
			// Now that the function scope is made, use it for the value
			check(asgn.getValue(), env, optimize);
			env.remove(env.size() - 1);
		}else {
			check(asgn.getValue(), env, optimize);
			
			Variable var = new Variable(name, asgn.getValue(), asgn);
			variables.add(var);
			curScope.put(name, var);
		}
	}
	
	protected void check(Reference ref, List<Map<String, Variable>> env, boolean optimize) {
		String name = ref.getVarName();
		// We must find some variable that matches the name given
		Variable referenced = null;
		for(int i=env.size()-1; i>=0; i--) {
			Map<String, Variable> search = env.get(i);
			if (search.containsKey(name)) {
				referenced = search.get(name);
				break;
			}
		}
		if (referenced == null)
			throw new CheckException("Reference ", ref, " to an undeclared variable \"", name, "\"!");
		
		referenced.addRef(ref);
		ref.setLinkedTo(referenced.getSource());
		
		// We will know if there are necessary arguments based on whether the assignment was a function
		if (referenced.getSource() != null) {
			Assignment source = referenced.getSource();
			if (source.getParamList() != null) {
				// We need to connect the next token to the ref
				List<Subexpression> subexp = ref.getParent().getSubexpressions();
				int refAt = subexp.indexOf(ref);
				if (refAt == -1 || refAt + 1 >= subexp.size())
					throw new CheckException("Missing argument list for function call ", ref, "!");
				Subexpression argLs = subexp.remove(refAt + 1);
				
				// We want to type check on the number of arguments the function needs
				if (source.getParamList().size() > 1) {
					// If there is more than 1 parameter, then we need an argument list
					if (argLs instanceof ArgumentList) {
						ArgumentList list = (ArgumentList)argLs;
						int expected = source.getParamList().size();
						int found = list.getArgs().size();
						if (expected != found)
							throw new CheckException("Mismatch number of arguments given for function ",
									ref.getVarName(), "! ", Integer.toString(found),
									" arguments found whereas ", Integer.toString(expected), " expected.");
					}else if (source.getParamList().size() != 1)
						throw new CheckException("Mismatch number of arguments given for function ",
								ref.getVarName(), "! 1 argument found whereas ",
								Integer.toString(source.getParamList().size()), " expected.");
				}
				
				// Wrap the subexpression in a value
				Value args = new Value(null, argLs);
				ref.setArgument(args);
			}
		}
	}
	
	protected void check(Block block, List<Map<String, Variable>> env, boolean optimize) {
		env.add(new TreeMap<>());
		// check all children
		for (Expression e: block.getBody())
			check(e, env, optimize);
		env.remove(env.size() - 1);
		// If all in the body passed the check, then we can try to reduce
		if (optimize)
			block.reduce();
	}
	
	protected void check(If ife, List<Map<String, Variable>> env, boolean optimize) {
		check(ife.getCondition(), env, optimize);
		check(ife.getThen(), env, optimize);
		check(ife.getElse(), env, optimize);
	}
	
	protected void check(Value val, List<Map<String, Variable>> env, boolean optimize) {
		for (Subexpression e: val.getSubexpressions())
			check(e, env, optimize);
	}
	
	protected void check(Operation op, List<Map<String, Variable>> env, boolean optimize) {
		check(op.getRHS(), env, optimize);
		if (op instanceof BinOp)
			check(((BinOp)op).getLHS(), env, optimize);
	}
	
	protected void check(ArgumentList list, List<Map<String, Variable>> env, boolean optimize) {
		for (Value arg: list.getArgs())
			check(arg, env, optimize);
	}
	
	protected void optimize(Operation op) {
		// If both of the arguments to the operation are integer literals, we can collapse this.
		// We had to check after the children were checked since they could have been in blocks
		//  that would now be reduced.
		Value rhs = op.getRHS();
		if (rhs.getSubexpressions().size() == 1 && rhs.getSubexpressions().get(0) instanceof Literal) {
			int right = Integer.parseInt(((Literal)rhs.getSubexpressions().get(0)).getToken().getValue());
			int result = 0;
			
			if (op instanceof BinOp) {
				BinOp bop = (BinOp)op;
				Value lhs = bop.getLHS();
				if (lhs.getSubexpressions().size() == 1 &&
						lhs.getSubexpressions().get(0) instanceof Literal) {
					int left = Integer.parseInt(((Literal)lhs.getSubexpressions().get(0)).getToken().getValue());

					if (op instanceof BinOp.Addition)
						result = left + right;
					else if (op instanceof BinOp.Subtraction)
						result = left - right;
					else if (op instanceof BinOp.Multiplication)
						result = left * right;
					else if (op instanceof BinOp.Division)
						result = left / right;
					else if (op instanceof BinOp.Modulus)
						result = left % right;
					else if (op instanceof BinOp.Equal)
						result = (left == right? 1: 0);
					else if (op instanceof BinOp.NEqual)
						result = (left == right? 0: 1);
					else if (op instanceof BinOp.LessThan)
						result = (left < right? 1: 0);
					else if (op instanceof BinOp.LessEqual)
						result = (left <= right? 1: 0);
					else if (op instanceof BinOp.GreaterThan)
						result = (left > right? 1: 0);
					else if (op instanceof BinOp.GreaterEqual)
						result = (left >= right? 1: 0);
					else
						throw new CheckException("Unoptimized Operation Type! ", op);
				}
			} else if (op instanceof Operation.Negation)
				result = -right;
			else if (op instanceof Operation.Not)
				result = (right == 0? 1: 0);
			else
				throw new CheckException("Unoptimized Operation Type! ", op);
			
			
			// If we made it here, we assume that the simplification was successful
			// remove self from the parent and replace with a literal
			Value parent = op.getParent();
			int found = parent.getSubexpressions().indexOf(op);
			if (found != -1) {
				parent.getSubexpressions().remove(found);
				class OpenLiteral extends Literal {
					public OpenLiteral(Value parent, Token t) {
						super(parent);
						this.token = t;
					}
				}
				Literal lit = new OpenLiteral(parent, new Token(""+result, Token.Type.NUMBER, -1, -1));					
				parent.getSubexpressions().add(found, lit);
			}
		}
	}

}
