package senozoid.junk.eval;

@Deprecated
public final class BooleanEvaluator{

    public static boolean evaluate(String expression){
        return Evaluator.evalBool(expression);
    }

    private BooleanEvaluator(){
        //no instance
    }

}
