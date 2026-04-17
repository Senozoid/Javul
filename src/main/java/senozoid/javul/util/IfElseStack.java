package senozoid.javul.util;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.NoSuchElementException;

//TODO: write docs and usage examples
public class IfElseStack implements Serializable{

    private final ArrayDeque<Boolean> values = new ArrayDeque<>();
    private final ArrayDeque<Boolean> locks = new ArrayDeque<>();
    private final ArrayDeque<Boolean> endChain = new ArrayDeque<>();

    public boolean isLastInChain() throws NoSuchElementException{
        return endChain.getFirst();
    }

    public void setLastInChain(){
        endChain.pollFirst();
        endChain.push(true);
    }

    public boolean isLocked() throws NoSuchElementException{
        return locks.getFirst();
    }

    public void lock(){
        locks.pollFirst();
        locks.push(true);
    }

    public void unlock(){
        locks.pollFirst();
        locks.push(false);
    }

    public boolean peek() throws IllegalStateException, NoSuchElementException{
        if(isLocked()) throw new IllegalStateException();
        return values.getFirst();
    }

    public boolean pop() throws IllegalStateException, NoSuchElementException{
        if(isLocked()) throw new IllegalStateException();
        locks.pop();
        endChain.pop();
        return values.pop();
    }

    public void push(boolean value){
        values.push(value);
        locks.push(true);
        endChain.push(false);
    }

    public void discard(){
        try{
            pop();
        }catch(IllegalStateException|NoSuchElementException ignored){}
    }
}
