package senozoid.javul.util;

import java.util.regex.Pattern;

public final class Slice{ //acts as a substring-like view without actually allocating a new String instance

    //TODO: Add exception messages and javadoc comments

    private final String source;
    private final int start, end, length;

    public Slice(String source, int start, int end){
        if(start==end) {
            this.source = "";//unique literal lives in pool, helps avoid unnecessary null risks
            this.start = this.end = this.length = 0;
            return;
        }
        else if(start<0 || end>source.length()) throw new IndexOutOfBoundsException();//TODO: Add message
        else if(end<start) throw new IllegalArgumentException();//TODO: Add message
        else {
            this.source = source;
            this.start = start;
            this.end = end;
            this.length = end-start;
        }
    }

    public Slice(String source, int start){
        this(source, start, source.length());
    }

    public Slice(String source){
        this(source, 0);
    }

    public int length(){
        return length;
    }

    public char charAt(int index){
        if(index<0 || index>(length-1)) throw new IndexOutOfBoundsException();//TODO: Add message
        return source.charAt(start+index);
    }

    public boolean isEmpty(){
        return length==0;
    }

    private boolean boundsMatch(int beginIndex, int endIndex){

        if(beginIndex<0 || endIndex>length) throw new IndexOutOfBoundsException();//TODO: Add message
        else if(endIndex<beginIndex) throw new IllegalArgumentException();//TODO: Add message

        else return beginIndex==0 && endIndex==length;
    }

    public Slice subSlice(int beginIndex, int endIndex){
        return boundsMatch(beginIndex,endIndex)?
                this://must remain an immutable class
                new Slice(
                        this.source,
                        this.start+beginIndex,
                        this.start+endIndex
                );
    }

    public Slice subSlice(int beginIndex){
        return subSlice(beginIndex, this.length);
    }

    public String subSliceToString(int beginIndex, int endIndex){
        return boundsMatch(beginIndex,endIndex)?
                this.toString()://must remain an immutable class
                source.substring(this.start+beginIndex, this.start+endIndex);
    }

    public String subSliceToString(int beginIndex){
        return subSliceToString(beginIndex, this.length);
    }

    public boolean regionMatches(int thsOffset, String other, int othOffset, int regLen){
        return source.regionMatches(this.start+thsOffset, other, othOffset, regLen);
    }

    private boolean contentEquals(boolean ignoreCase, String other, int offset){
        if(this.isEmpty()){
            if(other==null) return offset==0;
            else return offset==other.length();
        }
        else if(this.length != other.length()-offset) return false;
        return source.regionMatches(ignoreCase, this.start, other, offset, length);
    }

    public boolean contentEquals(String other){
        return contentEquals(false, other, 0);
    }

    public boolean contentEqualsIgnoreCase(String other){
        return contentEquals(true, other, 0);
    }

    public boolean contentEquals(Slice other){
        if(this.length != other.length) return false;
        else if(this.isEmpty()) return true;
        else return contentEquals(false, other.source, other.start);
    }

    public boolean matchesPattern(Pattern pattern){
        return pattern.matcher(source).region(start, end).matches();
    }

    @Override
    public String toString(){
        return source.substring(start, end);
    }

    public int indexOf(char c){
        int index = source.indexOf(c,start)-start;
        return index<length?index:-1;
    }
}