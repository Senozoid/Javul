package senozoid.junk.eval;

import java.util.*;

//TODO: reconsider using float to store value in Node
//TODO: write docs explaining quirks and possible issues
public final class Evaluator{

    public static int evalInt(String expression){
        return Math.round(evaluate(expression).toFloat());//rounds half up
    }

    public static boolean evalBool(String expression){
        return evaluate(expression).toBool();
    }

    private static Node evaluate(String expression){
        //System.out.println("~".repeat(6)+": \""+expression+"\"");
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

        else termNode.setValue(0,false);//TODO: remove placeholder and fetch value from game-specific identifier
    }

    private static Node operate(Node parent, Node child){
        if(!child.isDone()) throw new IllegalArgumentException("Cannot operate, child node not done");//TODO

        Optional<Operator> optOp = parent.getOperator();
        if(optOp.isEmpty()) throw new IllegalArgumentException("Cannot operate, parent node has no operator");//TODO
        Operator op = optOp.get();

        if(op.isUnary() && parent.hasValue()) throw new IllegalArgumentException(op+" with left operand");//TODO

        else if(!op.isUnary() && !parent.hasValue()){//if binary and has no left operand
            //System.out.println("\t\tSetting left operand for "+op);
            parent.setValue(child);//value of child becomes left operand
            if(parent.isDone()) throw new IllegalArgumentException(op+" without second operand");//TODO
            return shortCircuit(parent);
        }

        //System.out.println("\t\tOperating: "+op);

        switch(op){

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

        if(!node.hasValue()) throw new IllegalArgumentException("Cannot short-circuit without value");//TODO
        Optional<Operator> optOp = node.getOperator();
        if(optOp.isEmpty()) throw new IllegalArgumentException("Cannot short-circuit without operator");//TODO
        Operator op = optOp.get();

        boolean status = switch(op){
            //Each case must have exclusivity over the rank of the operators mentioned in it.
            //Operators mentioned together in a case may or may not share rank, but none other should share rank with any of them.

            case POW -> node.toFloat()==0 || node.toFloat()==1;
            case MOD, MUL, DIV -> node.toFloat()==0;//these operators may or may not share rank, but none other should share rank with any of them
            case AND -> !node.toBool();
            case OR -> node.toBool();
            default -> false;
        };

        if(status){
            node.reduce();
            //System.out.println("\t\tShort-circuit "+op);
        }

        return node;

    }

    private Evaluator(){
        //no instance
    }

}

final class Stack{

    private final Deque<Node> stack = new ArrayDeque<>();
    Node peek(){return stack.element();}
    void push(Node node){
        //System.out.println("\t\tNode pushed");
        stack.push(node);
    }
    Node pop(){
        //System.out.println("\t\tNode popped");
        return stack.pop();
    }
    boolean isEmpty(){return stack.isEmpty();}

    Stack(String expression){
        push(new Node(
                expression
                        .replaceAll("\\s+","")
                //.replace("--","+")
                //.replace("!!!","!")
        ));
    }

    Node expand(){
        if(isEmpty()) throw new NoSuchElementException("Cannot expand empty stack");//TODO
        //System.out.println("\t\tExpanding subexpression...");
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
    private Type type = Type.AMBI;//just in case numeric and boolean values are not allowed to interchange

    private Node(Queue<String> subexps, Queue<Operator> usedOps, int rank){
        this.subexps = subexps;
        this.usedOps = usedOps;
        this.rank = rank;

        //System.out.println();
        //System.out.println("Constructed node:");
        //System.out.println(this);
    }

    public Node(String expression){
        expression = disclose(expression);//no null check
        if(expression.isBlank()) throw new IllegalArgumentException("Expression must not be blank");//TODO
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

        if(subexps.isEmpty()) throw new IllegalStateException("Something went wrong");//TODO
        this.rank = tempRank;

        //System.out.println();
        //System.out.println("Constructed node: \""+expression+"\"");
        //System.out.println(this);
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

            if(b==i) m=b; //number of leading '(' characters
            if(
                    b<i //after crossing all leading '(' characters
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
            case AMBI -> this.getFloat() == other.getFloat();
            case BOOL -> this.toBool() == other.toBool();
            case NUM -> this.toFloat() == other.toFloat();
        };
    }

    private float getFloat(){
        if(!hasValue()) throw new NoSuchElementException("No value present");//TODO
        return value;
    }

    private boolean getBool(){
        return Math.round(getFloat())!=0;//rounds half up
    }

    public float toFloat(){
        if(type==Type.BOOL) throw new NoSuchElementException("Boolean value instead of numeric");
        type = Type.NUM;
        return getFloat();
    }

    public boolean toBool(){
        if(type==Type.NUM) throw new NoSuchElementException("Numeric value instead of boolean");
        type = Type.BOOL;
        return getBool();
    }

    public void setValue(float value, boolean setNumeric){
        this.value = value;
        init = true;
        if(setNumeric) type = Type.NUM;

        /*
        System.out.println("\t\tSet value: "+(
                type==Type.BOOL? value!=0:value
        ));
        //*/
    }

    public void setValue(boolean value){
        type = Type.BOOL;
        setValue(value?1:0, false);
    }

    public void setValue(Node toCopy){
        type = toCopy.type;
        setValue(toCopy.getFloat(), false);
    }

    public Optional<Operator> getOperator(){
        if(operator!=null && operator.rank!=this.rank) throw new IllegalStateException("Node operator does not match rank");//TODO
        else return Optional.ofNullable(operator);
    }

    private void setOperator(Operator opToSet){
        if(opToSet.rank!=this.rank) throw new IllegalArgumentException("Operator does not match node rank");//TODO
        else operator=opToSet;
        //System.out.println("\t\tSet operator: "+opToSet);
    }

    public void removeOperator(){
        operator=null;
    }

    public boolean isDone(){
        if(!subexps.isEmpty()) return false;
        else if(!hasValue()) throw new IllegalStateException("Done, but has no value");//TODO
        else return true;
    }

    public boolean isTerm(){
        if(isDone()) return false;

        boolean oneSub = subexps.size()==1;//may yet have a single unary operator
        boolean noOps = operator==null && (usedOps==null || usedOps.isEmpty());
        boolean noRank = rank<0;

        if(oneSub && noOps) return true;//possible even if originally not a term, i.e., has a value
        else if(noRank) throw new IllegalStateException("Not a term, yet has no rank");//TODO
        else return false;
    }

    public String getTerm(){
        if(!isTerm()) throw new NoSuchElementException("Not a term");//TODO
        else return subexps.remove();
    }

    public Node next(){//TODO: write comments
        if(isDone()) throw new NoSuchElementException("Done node has no next");//TODO
        if(isTerm()) throw new NoSuchElementException("Term node has no next");//TODO
        //else if(operator==null && (usedOps==null || usedOps.isEmpty())) throw new IllegalStateException("Not a term, yet no operators");//TODO

        Queue<String> subSubs = new ArrayDeque<>(subexps.size());
        Queue<Operator> subOps = new ArrayDeque<>(usedOps.size());
        int subRank = -1;

        subSubs.add(subexps.remove());//throws

        while(!usedOps.isEmpty()){
            Operator op = usedOps.element();

            if(op.rank > this.rank) throw new IllegalStateException("Operator rank later than node rank");//TODO

            else if(op.rank == this.rank){
                if(operator==null) setOperator(usedOps.remove());
                if(op.isUnary()) while(!usedOps.isEmpty() && usedOps.element().isUnary()){
                    if(subRank < 0) subRank = 0;
                    subOps.add(usedOps.remove());
                }
                break;
            }

            else{
                if(subRank < op.rank) subRank = op.rank;

                if(!op.isUnary()){
                    if(subexps.isEmpty()) throw new IllegalArgumentException(op+" without second operand");//TODO
                    else subSubs.add(subexps.remove());
                }

                do subOps.add(usedOps.remove()); while(!usedOps.isEmpty() && usedOps.element().isUnary());
            }
        }

        return (subOps.isEmpty() && subSubs.size()==1)?
                new Node(subSubs.element())://so that a raw subexpression is not mistaken for a term
                new Node(subSubs, subOps, subRank);
    }

    public void reduce(){
        if(!hasValue()) throw new IllegalStateException("Attempt to reduce node without value");//TODO
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

    /*
    TODO: evaluate "false == +1/-2^-4@+2 != 2"
        The (in)equality operators "==" and "!=" have equal precedence and are left associative,
        so this expression is valid only if boolean and numeric values are comparable (like in C).
        In that case, booleans are treated as numerics for numeric operators where "false"="0" and "true"="1",
        and vice versa for boolean operators ("&&", "||", "!") where "0"="false" and all else is "true".
        Therefore, the "false" value is first turned into "0".
            => "0 == +1/-2^-4@+2 != 2"
        A human can stop right here and declare the answer to be "true", because the output of any "=="
        is either "true" = "1" or "false" = "0", so following it with "!= 2" will always evaluate to "true".
        But if we cannot look ahead, then we must now evaluate the right hand operand of the "==", which is:
            "-1/-2^-4@+2-2"
            => "(-1)/(-2)^(-4)@(+2)-2"
            => "(-1)/((-2)^(-4))@(+2)-2"
            => "(-1)/((sixteenth)@(+2))-2"
            => "((-1)/(-4))-2"
            => "(quarter)-2"
            => "-1.75"
        So the entire expression has been reduced to:
            "0 == -1.75 != 2"
            => "(0 == -1.75) != 2"
            => "false != 2"
            => "0 != 2"
            => "true" (or "1" if necessary)
        On the other hand, if numeric and boolean values are not allowed be interconverted, the original
        expression is strictly invalid and should be rejected.
    */

    //Note about the order of declaration here: If the sign of one operator is a substring of another and both operators are of same type (unary/binary),
    //then the one with the smaller sign must appear later in the checking order. For example, "<" should not be matched before "<=", or ">" before ">=".

    //unary operators:
    POS("+", 0),
    NEG("-", 0),
    NOT("!", 0),

    //binary operators:
    POW("^", 1),//left-associative, unlike longhand notation //must have unique precedence

    LOG("@", 2),//"+4@-2" means "log +4 with base -2" and evals to "2"

    //for the short-circuit, these 3 operations should have any other operations with same precedences
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

