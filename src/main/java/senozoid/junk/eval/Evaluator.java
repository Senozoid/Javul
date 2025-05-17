package senozoid.junk.eval;

import java.util.*;

/*
TODO: write docs explaining quirks and possibly unexpected behaviour, some of which are:
    1. There is no support for assignment operators, or for floating point representations with "E".
        Example effect 1: if "id == 2" is true, "--id" evaluates to 2.
    2. All unary operators are prefix operators, and have higher precedence than all binary operators.
        Example effect 1: "0-2^14" and "-2^14" evaluate to -16 and +16 respectively.
        Example effect 2: "!(1>2)" evaluates to true, but "!1>2" is an illegal expression.
    3. All binary operators of equal precedence are left associative.
        Example effect 1: "24/3/4" evaluates to 2, and not 32.
    4. Identifiers can be interpreted as either numeric or boolean operands (like int in C), but
        each operation takes and produces values of a particular type, and the two different types of value
        are not interconvertible or comparable (unlike int in C). This is a personal choice, to prevent hidden
        mistakes, and strangeness with constants, like true+true being 2, which is neither true nor false.
        Example effect 1: "id == 1" and "id == true" are both valid expressions, but "1 == true" is not.
        Example effect 2: "!id" is a valid boolean and "-id" a valid numeric expression, but "!id != -id" is illegal.
    5. During evaluation, identities and intermediate numeric values are stored as float and the result is
        rounded (half up) to int. Boolean representations of identifiers are derived also by first rounding to int.
        This is quite easy to change, and exists because the original implementation used only int variables.
        Example effect 1: "7/2" evaluates to 4, and "7/2 == 3.5" evaluates to true.
        Example effect 2: If "id == -0.5" evaluates to true, then "id == false" also evaluates to true.
*/

public final class Evaluator{

    static int floatToInt(float original){
        //return (int)original;//if truncated
        return Math.round(original);//if rounded half up
    }

    public static int evalInt(String expression){
        return floatToInt(evaluate(expression).toFloat());
    }

    public static boolean evalBool(String expression){
        return evaluate(expression).toBool();
    }

    private static Node evaluate(String expression){
        Stack stack = new Stack(expression);
        Node node = stack.peek();
        do{
            if(node.isDone()){
                stack.pop();
                if(stack.isEmpty()) return node;
                else node = operate(stack.peek(),node);
            }
            else if(node.isTerm()) terminate(node);
            else node = stack.expand();
        }while(true);
    }

    private static void terminate(Node termNode){
        String term = termNode.getTerm();

        //numeric constant
        try{
            termNode.setValue(Float.parseFloat(term),true);
            return;
        }catch(NumberFormatException ignored){}

        //boolean constant
        if("true".equalsIgnoreCase(term)) termNode.setValue(true);
        else if("false".equalsIgnoreCase(term)) termNode.setValue(false);

        //identifier
        else throw new IllegalStateException("Cannot fetch value from identifier \""+term+"\"");//TODO: remove placeholder and fetch value from game-specific identifier
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

            case POS -> parent.setValue(child.toFloat(),true);

            case NEG -> parent.setValue(-child.toFloat(),true);

            case NOT -> parent.setValue(!child.toBool());

            case POW -> parent.setValue((float)Math.pow(parent.toFloat(),child.toFloat()),true);

            case LOG -> parent.setValue((float)(Math.log(parent.toFloat())/Math.log(child.toFloat())),true);

            case MOD -> parent.setValue(parent.toFloat()%child.toFloat(),true);

            case MUL -> parent.setValue(parent.toFloat()*child.toFloat(),true);

            case DIV -> parent.setValue(parent.toFloat()/child.toFloat(),true);

            case ADD -> parent.setValue(parent.toFloat()+child.toFloat(),true);

            case SUB -> parent.setValue(parent.toFloat()-child.toFloat(),true);

            case LOEQ -> parent.setValue(parent.toFloat() <= child.toFloat());

            case MOEQ -> parent.setValue(parent.toFloat() >= child.toFloat());

            case LESS -> parent.setValue(parent.toFloat() < child.toFloat());

            case MORE -> parent.setValue(parent.toFloat() > child.toFloat());

            case EQL -> parent.setValue(parent.valueEquals(child));

            case NQL -> parent.setValue(!parent.valueEquals(child));

            case AND -> parent.setValue(parent.toBool()&&child.toBool());

            case OR  -> parent.setValue(parent.toBool()||child.toBool());
        }

        shortCircuit(parent).removeOperator();
        return parent;
    }

    private static Node shortCircuit(Node node){

        if(!node.hasValue()) throw new IllegalArgumentException("Cannot short-circuit without value");
        Optional<Operator> optOp = node.getOperator();
        if(optOp.isEmpty()) throw new IllegalArgumentException("Cannot short-circuit without operator");
        Operator op = optOp.get();

        boolean status = switch(op){
            //Each case must have exclusivity over the rank of the operators mentioned in it.
            //Operators mentioned together in a case may or may not share rank, but others should not share rank with any of them.

            case POW -> node.toFloat()==0 || node.toFloat()==1;
            case MOD, MUL, DIV -> node.toFloat()==0;//these operators may or may not share rank, but others should not share rank with any of them
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
        }while(!node.isDone() && !node.isTerm());
        return node;
    }

}

final class Node{

    final private Queue<String> subexps;
    final private Queue<Operator> usedOps;
    final private int rank;

    private Operator operator = null;
    private boolean init = false;
    private float value = 0;
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

        if(subexps.isEmpty()) throw new IllegalStateException("Something went wrong");
        this.rank = tempRank;
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
            case NUM -> this.toFloat() == other.toFloat();
        };
    }

    private float getValue(){
        if(!hasValue()) throw new NoSuchElementException("No value present");
        return value;
    }

    public float toFloat(){
        if(type==Type.BOOL) throw new NoSuchElementException("Boolean value instead of numeric");
        type = Type.NUM;
        return getValue();
    }

    public boolean toBool(){
        if(type==Type.NUM) throw new NoSuchElementException("Numeric value instead of boolean");
        type = Type.BOOL;
        return Evaluator.floatToInt(getValue())!=0;
    }

    public void setValue(float value, boolean setNumeric){
        this.value = value;
        init = true;
        if(setNumeric) type = Type.NUM;
    }

    public void setValue(boolean value){
        type = Type.BOOL;
        setValue(value?1:0, false);
    }

    public void setValue(Node toCopy){
        type = toCopy.type;
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

    public boolean isTerm(){
        if(isDone()) return false;

        boolean oneSub = subexps.size()==1;//may yet have one or more unary operators
        boolean noOps = operator==null && (usedOps==null || usedOps.isEmpty());
        boolean noRank = rank<0;

        if(oneSub && noOps) return true;
        else if(noRank) throw new IllegalStateException("Not a term, yet has no rank");
        else return false;
    }

    public String getTerm(){
        if(!isTerm()) throw new NoSuchElementException("Not a term");
        else return subexps.remove();
    }

    public Node next(){

        if(isDone()) throw new NoSuchElementException("Done node has no next");
        if(isTerm()) throw new NoSuchElementException("Term node has no next");

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

