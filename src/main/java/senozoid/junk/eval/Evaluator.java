package senozoid.junk.eval;

import java.util.*;

/*
TODO: write docs explaining quirks and possibly unexpected behaviour, some of which are:
    1. There is no support for assignment operators, as handling identifiers is left up to you. To implement
        handling of identifiers, see Node class constructor with a single String parameter.
        Example 1 -> By default, "2++2" is a valid expression, and equivalent to "2+(+2)".
    2. Floating point literals with signed exponent should be enclosed in parentheses.
        Example 1 -> "5*1.0E-10F+10" should be written as "5*(1.0E-10F)+10".
    3. All unary operators are prefix operators, and have higher precedence than all binary operators.
        Example 1 -> "0-2^14" and "-2^14" evaluate to -16 and +16 respectively.
        Example 2 -> "!(1>2)" evaluates to true, but "!1>2" is an illegal expression.
    4. All binary operators of equal precedence are left associative.
        Example 1 -> "x^y^z" is equivalent to "(x^y)^z", and not "x^(y^z)".
        Example 2 -> "24 / 3 / 4" evaluates to 2, and not 32.
    5. The possibility exists of allowing identifiers to be interpreted as either numeric or boolean
        operands (like int in C), but each operation takes and produces values of a particular type, and the
        two different types of value are not interconvertible or comparable (unlike int in C). To change this,
        see usages of field Node#type.
        Example 1 -> "1==1==true" is a valid expression, but "true==1==1" is not.
    6. During numeric evaluation, intermediate values are stored as double precision type and the final result
        is rounded (half up) to int, throwing an exception in case of overflow or underflow. To change this,
        see usages of field Node#value and method Evaluator#floatToInt. If you allow numeric values to be
        interpreted as booleans, the boolean values are derived also by first rounding to int.
        Example 1 -> "7/2==3.5" evaluates to true, but numeric evaluation of "7/2" results in 4.
        Example 2 -> If numeric values are made comparable to booleans, then "-0.5==false" evaluates to true.
    7. By default, the binary modulo (%) operation is given higher precedence than division and multiplication,
        but changing this is as simple as changing a single value, i.e., rank of Operator#MOD.
        Example 1 -> By default, "8 / 8 % 6 * 2" is equivalent to "8 / (8 % 6) * 2".
    ...
    Most of these quirks can be avoided by simply using parentheses, regardless of precedence.
*/

public final class Evaluator{

    public static int floatToInt(double original){
        return Math.toIntExact(Math.round(original));//if rounded half up
    }

    public static int evalInt(String expression){
        return floatToInt(evaluate(expression).toNum());
    }

    public static boolean evalBool(String expression){
        return evaluate(expression).toBool();
    }

    private static Node evaluate(String expression){
        Stack stack = new Stack(expression);
        Node node = stack.peek();
        do{
            if(!node.isDone()){
                node = stack.expand();
                continue;
            }
            stack.pop();
            if(stack.isEmpty()) return node;
            else node = operate(stack.peek(),node);
        }while(true);
    }

    private static Node operate(Node parent, Node child){
        if(!child.isDone()) throw new IllegalArgumentException("Cannot operate, child node not done");

        Optional<Operator> optOp = parent.getOperator();
        if(optOp.isEmpty()) throw new IllegalArgumentException("Cannot operate, parent node has no operator");
        Operator op = optOp.get();

        if(op.isUnary() && parent.hasValue()) throw new IllegalArgumentException(op+" with left operand");

        else if(!op.isUnary() && !parent.hasValue()){//if binary and has no left operand
            parent.setValue(child);//value of child becomes left operand
            if(parent.isDone()) throw new IllegalArgumentException(op+" without second operand");
            return shortCircuit(parent);
        }

        switch(op){

            //TODO: Unary operators may exist in chains, and can be reduced without making a chain of nodes

            case POS -> parent.setValue(child.toNum(),true);

            case NEG -> parent.setValue(-child.toNum(),true);

            case NOT -> parent.setValue(!child.toBool());

            case POW -> parent.setValue(Math.pow(parent.toNum(),child.toNum()),true);

            case LOG -> parent.setValue(logarithm(parent.toNum(),child.toNum()),true);

            case MOD -> {
                double denom = child.toNum();
                if(denom==0) throw new ArithmeticException("Modulo by zero");
                else parent.setValue(parent.toNum()%denom,true);
            }

            case MUL -> parent.setValue(parent.toNum()*child.toNum(),true);

            case DIV -> {
                double denom = child.toNum();
                if(denom==0) throw new ArithmeticException("Division by zero");
                else parent.setValue(parent.toNum()/denom,true);
            }

            case ADD -> parent.setValue(parent.toNum()+child.toNum(),true);

            case SUB -> parent.setValue(parent.toNum()-child.toNum(),true);

            case LOEQ -> parent.setValue(parent.toNum() <= child.toNum());

            case MOEQ -> parent.setValue(parent.toNum() >= child.toNum());

            case LESS -> parent.setValue(parent.toNum() < child.toNum());

            case MORE -> parent.setValue(parent.toNum() > child.toNum());

            case EQL -> parent.setValue(parent.valueEquals(child));

            case NQL -> parent.setValue(!parent.valueEquals(child));

            case AND -> parent.setValue(parent.toBool()&&child.toBool());

            case OR  -> parent.setValue(parent.toBool()||child.toBool());
        }

        shortCircuit(parent).removeOperator();
        return parent;
    }

    private static double logarithm(double target, double base){

        final double ZERO_BASE = 1.5717277847E-162;

        if(target>0 && base>ZERO_BASE) return Math.log(target)/Math.log(base);

        else if(target==0 || Math.abs(base)<=ZERO_BASE) throw new ArithmeticException("Logarithm with zero");

        else if(base==1 || base==-1) throw new ArithmeticException("Logarithm with base (+/-) one");

        else if(target==1) return 0;

        else if(!Double.isFinite(target) || !Double.isFinite(base)) throw new ArithmeticException("Logarithm with invalid value(s)");

        else if(target==base) return 1;

        else if(base>0) throw new ArithmeticException("Logarithm with positive base and negative target");

        long left=1, right=Long.MAX_VALUE/2;
        double error=1;

        while(right > left){

            long n = left+(right-left+(error>0?1:0))/2;

            long power = 2*n+(target>0?0:1);//if target pos, try evens, else odds

            double result = Math.pow(base, power);
            if(target<0 && Double.isInfinite(result)) result = Double.NEGATIVE_INFINITY;//large odd powers of large negative numbers return positive infinity, which is wrong

            error = target - result;
            if(target<0) error = -error;

            if(Math.abs(error) <= Double.MIN_VALUE) return power;

            if(base < -1){//result increases in magnitude with power
                if(error > 0) left = n;//target is ahead in magnitude, increase power
                else right = n;
            }
            else{//result increases in magnitude against power
                if(error > 0) right = n;//target is ahead in magnitude, decrease power
                else left = n;
            }

        }
        throw new ArithmeticException("Logarithm not possible");
    }

    private static Node shortCircuit(Node node){

        if(!node.hasValue()) throw new IllegalArgumentException("Cannot short-circuit without value");
        Optional<Operator> optOp = node.getOperator();
        if(optOp.isEmpty()) throw new IllegalArgumentException("Cannot short-circuit without operator");
        Operator op = optOp.get();

        boolean status = switch(op){
            //Each case must have exclusivity over the rank of the operators mentioned in it.
            //Operators mentioned together in a case may or may not share rank, but others should not share rank with any of them.

            case POW -> node.toNum()==0 || node.toNum()==1;
            case MUL/*, DIV, MOD*/ -> node.toNum()==0;//these operators may or may not share rank, but others should not share rank with any of them
            case AND -> !node.toBool();
            case OR -> node.toBool();
            default -> false;
        };

        if(status) node.reduce();

        return node;

    }

    private Evaluator(){
        //no instance
    }

}

final class Stack{

    private final Deque<Node> stack = new ArrayDeque<>();
    Node peek(){return stack.element();}
    void push(Node node){stack.push(node);}
    Node pop(){return stack.pop();}
    boolean isEmpty(){return stack.isEmpty();}

    Stack(String expression){
        push(new Node(
                expression
                        .replaceAll("\\s+","")
                //.replace("-+","-")
                //.replace("---","-")
                //.replace("!!!","!")
        ));
    }

    Node expand(){
        if(isEmpty()) throw new NoSuchElementException("Cannot expand empty stack");
        Node node = peek();
        do{
            node = node.next();
            push(node);
        }while(!node.isDone());
        return node;
    }

}

final class Node{

    final private Queue<String> subexps;
    final private Queue<Operator> usedOps;
    final private int rank;

    private Operator operator = null;
    private boolean init = false;
    private double value = 0;
    private Type type = Type.AMBI;//because numeric and boolean values are not allowed to interchange

    private Node(Queue<String> subexps, Queue<Operator> usedOps, int rank){
        this.subexps = subexps;
        this.usedOps = usedOps;
        this.rank = rank;
    }

    public Node(String expression){
        expression = disclose(expression);//no null check
        if(expression.isBlank()) throw new IllegalArgumentException("Expression must not be blank");

        final int len = expression.length();
        subexps = new ArrayDeque<>(1+len/2);
        usedOps = new ArrayDeque<>(len/2);
        int tempRank = -1;

        /*
        The following portion that checks whether the expression is a literal, can be moved down to be just above the identifier-fetch,
        but in that case, while efficiency will increase, floating point literals with signed exponent will not be supported.
        */

        //if numeric literal
        try{
            setValue(Double.parseDouble(expression),true);
        }catch(NumberFormatException ignored){}

        //if boolean literal
        if("true".equalsIgnoreCase(expression)) setValue(true);
        else if("false".equalsIgnoreCase(expression)) setValue(false);

        //if literal
        if(hasValue()){
            rank = tempRank;
            return;
        }

        int subAt=0;

        for(int i=0; i<len; i++){

            char c=expression.charAt(i);
            if(c=='('){
                int b=1/*, open=i+1*/;
                do{
                    if(++i>=len) throw new IllegalArgumentException("Unpaired '(' found in expression \""+expression+"\"");
                    c=expression.charAt(i);
                    if(c=='(') b++;
                    else if(c==')') b--;
                }while(b>0);
                //subexps.add(expression.substring(open,i));
                continue;
            }
            else if(c==')') throw new IllegalArgumentException("Unpaired ')' found in expression \""+expression+"\"");

            Optional<Operator> optOp = operatorAt(i, expression, subAt==i);
            if(optOp.isEmpty()) continue;//not an operator
            Operator foundOp = optOp.get();

            if(!foundOp.isUnary()) subexps.add(expression.substring(subAt,i));//add left operand
            usedOps.add(foundOp);
            if(tempRank < foundOp.rank) tempRank = foundOp.rank;
            subAt=i+foundOp.sign.length();
            i=subAt-1;

        }
        if(subAt==len) throw new IllegalArgumentException("Expression \""+expression+"\" ended with an operator");
        else subexps.add(expression.substring(subAt));
        this.rank = tempRank;

        if(isDone()) throw new IllegalStateException("Node could not be created");

        //TODO: Best place to reduce a chain of unary operators

        if(operator!=null || !usedOps.isEmpty() || subexps.size()!=1){
            if(rank<0) throw new IllegalStateException("Not a term node, yet has no rank");
            else return;
        }

        if(expression.matches("(\\d+\\.?\\d*|\\.\\d+)[eE]|\\d+[fFdD]")){
            throw new IllegalArgumentException(
                    "Floating point literal with signed exponent must be enclosed in parentheses: "+expression
            );
        }

        //identifier
        throw new IllegalStateException("Cannot fetch value from identifier \""+expression+"\"");//TODO: remove placeholder and fetch value from game-specific identifier
    }

    private static Optional<Operator> operatorAt(int index, String expression, boolean isUnary){
        Operator foundOp = null;
        Operator wrongOp = null;

        for(Operator op:Operator.values()){
            if(!expression.regionMatches(index,op.sign,0,op.sign.length())) continue;
            else if(isUnary != op.isUnary()){
                wrongOp = op;
                continue;
            }
            else{
                foundOp = op;
                break;
            }
        }
        if(foundOp==null && wrongOp!=null) throw new IllegalArgumentException(
                "Unexpected "+wrongOp
                        +" at index "+index+" of expression \""+expression+"\""
        );//unary operator with, or binary operator without left operand

        return Optional.ofNullable(foundOp);
    }

    private static String disclose(String enclosed){
        if(!enclosed.matches("^\\(+.*\\)+$")) return enclosed;

        final int len=enclosed.length();
        int b=1, i=0, m=b;
        do{
            int e=++i+b; //expected minimum length
            if(len<e) throw new IllegalArgumentException("Unpaired '(' found in expression \""+enclosed+"\"");

            char c=enclosed.charAt(i);
            if(c=='(') b++;
            else if(c==')') b--;

            if(b==1+i) m=b; //number of leading '(' characters
            if(
                    b<1+i //after crossing all leading '(' characters
                            && len>e //before reaching any trailing ')' characters
                            && m>b //if value of b drops
            ) m=b; //level of redundant outer parentheses
        }while(b>0);

        return m>0?enclosed.substring(m,len-m):enclosed;
    }

    public boolean hasValue(){
        return init;
    }

    public boolean valueEquals(Node other){
        if(other==null || !other.hasValue() || !this.hasValue()) return false;
        return switch(type){
            case AMBI -> this.getValue() == other.getValue();
            case BOOL -> this.toBool() == other.toBool();
            case NUM -> this.toNum() == other.toNum();
        };
    }

    private double getValue(){
        if(!hasValue()) throw new NoSuchElementException("No value present");
        return value;
    }

    private void setType(Type newType){
        boolean allowed = switch(type){
            case AMBI -> true;
            case BOOL -> newType == Type.BOOL;
            case NUM -> newType != Type.AMBI;
        };
        if(allowed) type = newType;
        else throw new IllegalArgumentException("Cannot set "+type+" node to "+newType);
    }

    public double toNum(){
        if(type==Type.BOOL) throw new NoSuchElementException("Boolean value instead of numeric");
        setType(Type.NUM);
        return getValue();
    }

    public boolean toBool(){
        if(type==Type.NUM) throw new NoSuchElementException("Numeric value instead of boolean");
        setType(Type.BOOL);
        return Evaluator.floatToInt(getValue())!=0;
    }

    public void setValue(double value, boolean setNumeric){
        if(Double.isInfinite(value)) throw new IllegalArgumentException("Value too large: "+value);
        else if(!Double.isFinite(value)) throw new IllegalArgumentException("Unsupported value: "+value);//NaN
        this.value = value;
        init = true;
        if(setNumeric) setType(Type.NUM);
    }

    public void setValue(boolean value){
        setType(Type.BOOL);
        setValue(value?1:0, false);
    }

    public void setValue(Node toCopy){
        setType(toCopy.type);
        setValue(toCopy.getValue(), false);
    }

    public Optional<Operator> getOperator(){
        if(operator!=null && operator.rank!=this.rank) throw new IllegalStateException("Node operator does not match rank");
        else return Optional.ofNullable(operator);
    }

    private void setOperator(Operator opToSet){
        if(opToSet.rank!=this.rank) throw new IllegalArgumentException("Operator does not match node rank");
        else operator=opToSet;
    }

    public void removeOperator(){
        operator=null;
    }

    public boolean isDone(){
        if(!subexps.isEmpty()) return false;
        else if(!hasValue()) throw new IllegalStateException("Done, but has no value");
        else return true;
    }

    public Node next(){

        if(isDone()) throw new NoSuchElementException("Done node has no next");

        Queue<String> subSubs = new ArrayDeque<>(subexps.size());
        Queue<Operator> subOps = new ArrayDeque<>(usedOps.size());
        int subRank = -1;

        if(rank==0){
            if(hasValue() || operator!=null || subexps.size()!=1) throw new IllegalStateException("Something went wrong");

            //TODO: We can traverse through and reduce the operator queue before creating the next node

            setOperator(usedOps.remove());

            if(usedOps.isEmpty()) return new Node(subexps.remove());

            do subOps.add(usedOps.remove()); while(!usedOps.isEmpty());
            subSubs.add(subexps.remove());

            return new Node(
                    subSubs,
                    subOps,
                    this.rank//==0
            );

        }

        //going forward with rank>0, i.e., operator must be binary

            /*
            SCENARIO 0. !hasValue() && operator==null: Expect (left) operand, then operator.
                Happens the first time next() is called

            SCENARIO 1. !hasValue() && operator!=null: Cannot already have operator without left operand. Error.
                Should never happen

            SCENARIO 2. hasValue() && operator!=null: Expect (right) operand.
                Happens when the binary operator has one operand and needs the other

            SCENARIO 3. hasValue() && operator==null: Expect operator, then (right) operand.
                Happens when a previous binary operator is removed after operation
            */

        //SCENARIO 1:
        if(!hasValue() && operator!=null) throw new IllegalStateException("Something went wrong");
        //SCENARIO 3:
        if(hasValue() && operator==null) setOperator(usedOps.remove());//may throw if op does not match rank

        subSubs.add(subexps.remove());//can it throw?

        while(!usedOps.isEmpty()){

            Operator op = usedOps.element();

            if(op.rank > this.rank) throw new IllegalStateException("Operator rank later than node rank");

            if(op.rank == this.rank){
                //SCENARIO 0:
                if(operator == null) setOperator(usedOps.remove());
                break;
            }

            subOps.add(usedOps.remove());
            if(subRank < op.rank) subRank = op.rank;

            if(op.isUnary()){//if one unary is found, keep adding chain of adjacent unaries until binary or end
                while(!usedOps.isEmpty() && usedOps.element().isUnary()) subOps.add(usedOps.remove());
            }

            else{//if binary is found, add another subexpression
                if(subexps.isEmpty()) throw new IllegalArgumentException(op+" without second operand");
                else subSubs.add(subexps.remove());
            }

        }

        if(operator==null || subSubs.isEmpty()) throw new IllegalStateException("Something went wrong");

        return (subOps.isEmpty() && subSubs.size()==1)?
                new Node(subSubs.remove())://so that a raw subexpression is not mistaken for a term
                new Node(subSubs, subOps, subRank);
    }

    public void reduce(){
        if(!hasValue()) throw new IllegalStateException("Attempt to reduce node without value");
        subexps.clear();
        usedOps.clear();
        removeOperator();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();

        builder.append("Subexpressions: ").append(subexps);
        if(usedOps.isEmpty()) builder.append("; Terminal node");
        else{
            builder.append("; Operators: ").append(usedOps);
            builder.append("; Node rank: ").append(this.rank);
        }
        return builder.toString();
    }

    private enum Type{
        AMBI,
        BOOL,
        NUM;
    }

}

enum Operator{

    //Note about the order of declaration here: If the sign of one operator is a substring of another and both operators are of same type (unary/binary),
    //then the one with the smaller sign must appear later in the checking order. For example, "<" should not be matched before "<=", or ">" before ">=".

    //unary operators:
    POS("+", 0),
    NEG("-", 0),
    NOT("!", 0),

    //binary operators:
    POW("^", 1),//left-associative, unlike longhand notation //must have unique precedence

    LOG("@", 2),//"+4@-2" means "log +4 with base -2" and evals to "2"

    MOD("%", 3),

    MUL("*", 4),
    DIV("/", 4),

    ADD("+", 5),
    SUB("-", 5),

    LOEQ("<=", 6),
    MOEQ(">=", 6),
    LESS("<", 6),
    MORE(">", 6),

    EQL("==", 7),
    NQL("!=", 7),

    //must have unique precedence
    AND("&&", 8),

    //must have unique precedence
    OR("||", 9);

    public final String sign;
    public final int rank;

    Operator(String sign, int rank){
        this.sign = sign;
        this.rank = rank;
    }

    public boolean isUnary(){
        return rank==0;
    }

    @Override
    public String toString(){
        return (isUnary()?"unary":"binary")+" \""+sign+"\"";
    }
}

