package jet.tree.data;

public class TreeRecord
{

    //private jprotein.tree.tools.NJ nj;
    private int[] numLevels;
    private int maxLevels;

    public TreeRecord(int maxLevels, int[] numLevels)
    {
	this.maxLevels=maxLevels;
	this.numLevels=numLevels;
	//this.nj=nj;
    }
    
    public int getMaxLevels(){ return maxLevels; }
    
    public int getNumLevels(int residueIndex){ return numLevels[residueIndex]; }

}
