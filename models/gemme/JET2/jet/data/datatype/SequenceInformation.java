package jet.data.datatype;

import java.util.*;

public class SequenceInformation extends jet.data.datatype.Sequence
{
    private int start,end,identity;
    private double expect;
    private boolean startChecked=false,endChecked=false;
    private boolean identityChecked=false,expectChecked=false;

    public SequenceInformation(){ super(); }
    
    public void setStartOverlap(int start) { this.start=start; }
    public int getStartOverlap(){ return this.start; }
    public void setEndOverlap(int end) 
    { this.end=end; endChecked=true; }
    public int getEndOverlap(){ return this.start; }
    public boolean overlapChecked(){ return ((startChecked)&&(endChecked)); }

    public void setIdentity(int identity) 
    { this.identity=identity; identityChecked=true;}
    public int getIdentity() { return this.identity; }
    public boolean identityChecked() { return identityChecked; }

    public void setExpectValue(double expect) 
    { this.expect=expect; startChecked=true;}
    public double getExpectValue() { return this.expect; }
    public boolean expectChecked(){ return expectChecked; }
   
}
