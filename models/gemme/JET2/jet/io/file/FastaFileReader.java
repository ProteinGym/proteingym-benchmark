package jet.io.file;

import java.util.*;


public class FastaFileReader extends FileIO
{
    String path;

    Vector sequenceData=null;
    
    public FastaFileReader(String path) {  setPath(path); }
    
    public void setPath(String path) { this.path=path; }

    public String getPath() { return path;}

    public Vector getSequenceData()
    {
	if(sequenceData==null) extractSequenceData();
	return sequenceData;
    }

    /** Extraction des séquences au format FASTA, la première séquence est la séquence de référence */
    
    public void extractSequenceData()
    {
	String line,residueSequence="";

	jet.data.dataformat.info.PairwiseSequenceInfo seq = null;
	
	Iterator iter=readFile(getPath()).iterator();
	sequenceData=new Vector(1,1);
	int lineNum=0;
	
	while(iter.hasNext())
	    {
		line=((String)iter.next()).trim();
		lineNum++;
		if(line.length()>0)
		    {
			if(line.charAt(0)=='>')
			    {
				if(seq!=null) 
				    {
					seq.setSequence(residueSequence.toUpperCase());
					sequenceData.add(seq);
				    }
				seq=new jet.data.dataformat.info.PairwiseSequenceInfo();
				seq.setSequenceName(line.substring(line.indexOf(";")+1));
				residueSequence="";
				lineNum=0;
			    }
			else
			    {
				if((lineNum)>0)
				    {
					if(line.charAt(line.length()-1)=='*')
					    {
						residueSequence+=line.substring(0,line.length()-1);
					    }
					else residueSequence+=line;
				    }
			    }
			
		    }
	    }
	
	if(seq!=null)
	    {
		seq.setSequence(residueSequence);
		sequenceData.add(seq);
	    }
	int size=((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(0)).size();
	boolean formatOK=true;
	for (int i=0;i<sequenceData.size();i++)
	{
		if (size!=((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(i)).size())
		{
			System.err.println("mauvais format de l'alignement "+path);
			formatOK=false;
			break;
		}
	}
	
	if (formatOK)
	{
		System.out.println("format ok");
		jet.data.datatype.Sequence ref=new jet.data.datatype.Sequence();
		ref.setSequenceName(((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(0)).getSequenceName());
		ref.setSequence(((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(0)).getSequence().toString());
		sequenceData.remove(0);
		for (int i=0;i<sequenceData.size();i++)
			{
			((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(i)).setRefSequence(ref);
			((jet.data.dataformat.info.PairwiseSequenceInfo)sequenceData.get(i)).computeIdentity();
			}
	}
	else sequenceData.clear();
    }
    

}
