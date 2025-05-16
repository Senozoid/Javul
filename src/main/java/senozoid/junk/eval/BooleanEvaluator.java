package senozoid.junk.eval;

//import java.util.Scanner;
import java.util.regex.Pattern;

@Deprecated
public final class BooleanEvaluator{
    private static final String[] validOperators={"==","!=","<=",">=","<",">"};//WARNING: DO NOT CHANGE THIS ORDER, SEE NOTES

    /*
    public static void main(String[] args){
        boolean status;
        Scanner input=new Scanner(System.in);

        System.out.print("Enter a boolean expression: ");
        try{
            status=evaluate(input.nextLine());
        }catch(IllegalBracketException ibe) {
            System.out.println("The expression you entered has a bracket problem.");
            ibe.printStackTrace();
            return;
        }catch(UnresolvedSymbolException use) {
            System.out.println("There is something wrong with the symbols you entered.");
            use.printStackTrace();
            return;
        }
        System.out.println("The expression you entered evaluates to "+status+".");
    }
    */

    public static boolean evaluate(String expression) throws IllegalBracketException, UnresolvedSymbolException{
        boolean status;

        if(expression.contains("(")||expression.contains(")")) status=evalBrackets(expression);
        else status=evalNoBrackets(expression);

        return status;
    }

    private static boolean evalBrackets(String expression) throws IllegalBracketException, UnresolvedSymbolException {
        StringBuilder bracketless=new StringBuilder();
        StringBuilder subexpression;
        boolean evaluation;
        int b=0;
        char c;
        int length=expression.length();

        for(int i=0; i<length; i++) {
            c=expression.charAt(i);
            if(c=='('){
                b++;
                subexpression=new StringBuilder();
                while(true){
                    if(++i>=length) throw new IllegalBracketException();
                    c=expression.charAt(i);

                    if(c=='(') b++;
                    else if(c==')'){
                        if(--b==0) break;
                    }

                    subexpression.append(c);
                }
                evaluation=evaluate(subexpression.toString());
                bracketless.append(evaluation);
            }
            else if(c==')') throw new IllegalBracketException();
            else bracketless.append(c);
        }

        return evaluate(bracketless.toString());
    }

    private static boolean evalNoBrackets(String expression) throws UnresolvedSymbolException {
        return evalOR(expression);
    }

    private static boolean evalOR(String expression) throws UnresolvedSymbolException {
        if(!expression.contains("||")) return evalAND(expression);
        boolean status=false;
        String[] subexes=expression.split(Pattern.quote("||"));//Pattern.quote("||") is equivalent to "\\|\\|"
        for (String x:subexes) {
            if(evalAND(x)){
                status=true;
                break;
            }
        }
        return status;
    }

    private static boolean evalAND(String expression) throws UnresolvedSymbolException {
        if(!expression.contains("&&")) return evalTerm(expression);
        boolean status=true;
        String[] subexes=expression.split("&&");
        for (String x:subexes) {
            if(!evalTerm(x)) {
                status=false;
                break;
            }
        }
        return status;
    }

    private static boolean evalTerm(String term) throws UnresolvedSymbolException {
        boolean operation=false;
        boolean status=false;
        float[] operands=new float[2];
        String[] operandStrings;

        term=term.strip();
        if(term.length()<3) throw new UnresolvedSymbolException();
        if(term.charAt(0)=='!') return !evalTerm(term.substring(1));

        for (String operator : validOperators) {
            if (term.contains(operator)) {
                operandStrings=term.split(operator);
                if(operandStrings.length!=2) break;

                try{
                    for(int i = 0; i < 2; i++){
                        operands[i]=Float.parseFloat(operandStrings[i]);
                    }
                }catch(NumberFormatException nfe){
                    break;
                }

                status=switch(operator){//if you change this, change the static final array as well
                    case "==" -> operands[0]==operands[1];
                    case "!=" -> operands[0]!=operands[1];
                    case "<=" -> operands[0]<=operands[1];
                    case ">=" -> operands[0]>=operands[1];
                    case "<" -> operands[0]<operands[1];
                    case ">" -> operands[0]>operands[1];
                    default -> throw new UnresolvedSymbolException();
                };

                operation=true;
                break;
            }
        }
        if(!operation){
            if("true".equalsIgnoreCase(term)) status=true;
            else if(!"false".equalsIgnoreCase(term)) throw new UnresolvedSymbolException();
        }
        return status;
    }

    private BooleanEvaluator(){
        //no instance
    }

}
