package classy.compiler.analyzing;

import java.util.ArrayList;
import java.util.List;

import classy.compiler.lexing.Token;
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

public class Optimizer {
	
	public void optimize(List<Variable> variables, Value program) {
		// Attempt to replace any unneeded variables with their values
		// Optimization is an interesting process since different parts may be dependent on others.
		//  If we do any one part first, then we could be missing out. Thus, we keep looping through
		//  until no more changes are made.
		boolean changesMade;
		do {
			changesMade = false;
			List<Variable> removeList = new ArrayList<>();
			for (Variable var: variables) {
				// We try to optimize the value of the variable
				if (var.value != null)
					optimize(var.value);
				
				if (var.references.isEmpty())
					removeList.add(var);
				// If there is only one usage, and the variable is set (not a function param)
				else if (var.references.size() == 1 && var.value != null) {
					// Replace the reference with the value
					// TODO: implement replacement of function values
					if (((Assignment)var.source).getParamList() == null) {
						Reference ref = var.references.get(0);
						List<Subexpression> subList = ref.getParent().getSubexpressions();
						int replaceAt = subList.indexOf(ref);
						subList.remove(replaceAt);
						subList.addAll(replaceAt, var.value.getSubexpressions());
					}
					
					// get rid of the assignment for an unused variable
					removeList.add(var);
				}
			}
			for (Variable var: removeList) {
				// We need to delete it at its source first
				if (var.source instanceof Parameter) {
					System.out.println("Warning: Unused parameter \"" + var.name + "\".");
				}else {
					Block parent = ((Assignment)var.source).getParent();
					parent.getBody().remove(var.source);
					parent.reduce();
					// then we can remove it from the variables list to complete the change
					variables.remove(var);	
					changesMade = true;
				}
			}
		} while(changesMade);
		
		// Then finally we make a run through the entire program
		optimize(program);
	}
	
	protected void optimize(Expression e) {
		if (e instanceof Operation)
			optimize((Operation)e);
		if (e instanceof Value)
			optimize((Value)e);
		if (e instanceof Block)
			optimize((Block)e);
		if (e instanceof If)
			optimize((If)e);
	}
	
	protected void optimize(Operation op) {
		// If both of the arguments to the operation are integer literals, we can collapse this.
		// We had to check after the children were checked since they could have been in blocks
		//  that would now be reduced.
		Value rhs = op.getRHS();
		optimize(rhs);
		if (rhs.getSubexpressions().size() == 1 && rhs.getSubexpressions().get(0) instanceof Literal) {
			int right = Integer.parseInt(((Literal)rhs.getSubexpressions().get(0)).getToken().getValue());
			int result = 0;
			
			if (op instanceof BinOp) {
				BinOp bop = (BinOp)op;
				Value lhs = bop.getLHS();
				optimize(lhs);
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
					else if (op instanceof BinOp.And)
						result = left != 0? (right != 0? 1: 0): 0;
					else if (op instanceof BinOp.Or)
						result = left != 0? 1: (right != 0? 1: 0);
					else
						throw new CheckException("Unoptimized Operation Type! ", op);
				}else {
					// Could not optimize since one side was not literal or something
					return;
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
	
	protected void optimize(Value val) {
		List<Subexpression> sub = val.getSubexpressions();
		for (int i = 0; i < sub.size(); i++) {
			// optimizing this element may transform it into something
			// different. Thus we do not want to save a temporary here.
			optimize(sub.get(i));
			
			// Similarly to how we reduce blocks, if there is only one element in the value,
			//  we can collapse it into this.
			if (sub.get(i) instanceof Value) {
				Value value = (Value)sub.get(i);
				if (value.getSubexpressions().size() == 1) {
					// replace value in the list with its expression
					sub.remove(i);
					Subexpression inner = value.getSubexpressions().get(0);
					inner.setParent(val);
					sub.add(i, inner);
				}
			}
		}
	}
	
	protected void optimize(If if_) {
		// We can potentially remove the entire branch if only one outcome is possible
		optimize(if_.getCondition());
		List<Subexpression> condList = if_.getCondition().getSubexpressions();
		if (condList.size() == 1 && condList.get(0) instanceof Literal) {
			// We can reduce depending on the truth of the literal
			Literal cond = (Literal)condList.get(0);
			int val = Integer.parseInt(cond.getToken().getValue());
			Subexpression replaceWith;
			if (val != 0)
				// replace the entire if with then
				replaceWith = if_.getThen();
			else 
				// replace the entire if with else
				replaceWith = if_.getElse();
			optimize(replaceWith);
			int ifAt = if_.getParent().getSubexpressions().indexOf(if_);
			if (ifAt != -1) {
				List<Subexpression> parList = if_.getParent().getSubexpressions();
				parList.remove(ifAt);
				parList.add(ifAt, replaceWith);
				replaceWith.setParent(if_.getParent());
			}
		}else {
			// optimize all result branches
			optimize(if_.getThen());
			optimize(if_.getElse());
		}
	}
	
	protected void optimize(Block block) {
		// Try to reduce all elements of the block
		for (Expression e: block.getBody())
			optimize(e);
		// Lastly, try to reduce the block
		block.reduce();
	}

}
