package jet.tree.data;

import java.util.*;

public class PathIndex extends Vector
{
    public PathIndex() { super(1,1); }

    public int direction(int index) { return ((Integer)get(index)).intValue(); }
    
    public void addDirection(int direction) { add(0,new Integer(direction));}

}
