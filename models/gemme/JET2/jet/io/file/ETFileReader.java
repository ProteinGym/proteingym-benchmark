package jet.io.file;

import java.util.*;

public class ETFileReader extends FileIO
{
	String path;

    Vector valueData=null;
	
    public ETFileReader(String path) {  setPath(path); }
    
    public void setPath(String path) { this.path=path; }

    public String getPath() { return path;}

    public Vector getValueData()
    {
		if(valueData==null) extractValueData();
		return valueData;
    }
    
    public void extractValueData()
    {
    	//System.out.println("extractValueData()");
    	int nbLine=0;
		String line;
		Iterator iter=readFile(getPath()).iterator();
		valueData=new Vector(3);
		for (int i=0;i<3;i++) valueData.add(new Vector());
		boolean rank=false;
		//System.out.println("iter:"+iter.hasNext());
		//System.out.println("nbLine:"+nbLine);
		while((iter.hasNext())&&(((String)iter.next()).trim().lastIndexOf("~ET_ranks")==-1 ))
			{nbLine++;}
		//System.out.println("nbLine:"+nbLine);
		String[] subLine;
		while((iter.hasNext())&&((line=((String)iter.next()).trim()).lastIndexOf("~tree")==-1 ))
		{
			nbLine++;
			//System.out.println("extractValueData()1");
			if((line.length()>0)&&(!line.substring(0, 1).equals("%")))
			{
				//System.out.println("extractValueData()2");
				subLine=line.split("\\s+");
				((Vector)valueData.get(0)).add(subLine[2].trim());
				((Vector)valueData.get(1)).add(subLine[1].trim());
				((Vector)valueData.get(2)).add(subLine[6].trim());
		    }
	    }
		//System.out.println("nbLine:"+nbLine);
    }
    
    public String extractRhoValue(double coverageValue)
    {
    	String rhoValue="";
    	int nbLine=0;
		String line;
		String[] subLine;
		Iterator iter=readFile(getPath()).iterator();
		while((iter.hasNext())&&(((String)iter.next()).trim().lastIndexOf("~z_scores")==-1 ))
		{nbLine++;}
		
		while(iter.hasNext())
		{
			
			line=((String)iter.next()).trim();
			//System.out.println("extractValueData()1");
			if((line.length()>0)&&(!line.substring(0, 1).equals("%")))
			{
				//System.out.println("extractValueData()2");
				subLine=line.split("\\s+");
				
				if (Double.parseDouble(subLine[1].trim())>=coverageValue)
				{
					rhoValue=subLine[0].trim();
					break;
				}
		    }
			nbLine++;
	    }
		
    	return rhoValue;
    }
    
    public Vector extractClusterData(String rhoValue)
    {
    	Vector clusters=new Vector();
    	int nbLine=0;
    	boolean acquisitionValeur;
		String line;
		int position;
		String[] subLine;
		Iterator iter=readFile(getPath()).iterator();
		while(iter.hasNext())
		{
			line=((String)iter.next()).trim();
			if((line.length()>=4)&&(line.substring(0,4).equals("rho:")))
			{
				subLine=line.split("\\s+");
				if(rhoValue.equals(subLine[1].trim()))
				{
					acquisitionValeur=false;
					while((iter.hasNext())&&((line=((String)iter.next()).trim()).lastIndexOf("rho:")==-1))
					{
						if(line.lastIndexOf("cluster size:")!=-1)
						{
							acquisitionValeur=true;
							clusters.add(new Vector());
						}
						else
						{
							if(acquisitionValeur)
							{
								subLine=line.split("\\s+");
								//System.out.println("");
								for (int i=0;i<subLine.length;i++)
								{
									try
									{
										position=Integer.parseInt(subLine[i].trim());
										//System.out.print(" "+position);
										((Vector)clusters.lastElement()).add(position);
									}catch(NumberFormatException excp){System.out.println("NumberFormatException:"+subLine[i].trim());}
								}
								//System.out.println("");
							}
						}
					}
					break;
				}
			}
			nbLine++;
		}
		return clusters;
    }
    
}
