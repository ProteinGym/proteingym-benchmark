package jet.tools;

import java.util.Vector;

public class OrderValue {

	
		
		/** Classe permettant d'ordonner des valeurs double par ordre decroissant 
		 * et de stocker la position de départ de ces valeurs. On obtient un vecteur 
		 * des positions et un vecteur des valeurs triées. */
	    
	    public static Vector orderProperty(Vector property)
	    {
		Vector posV=new Vector(property.size());
		Vector propV=new Vector(property.size());
		Vector res=new Vector(2); res.add(posV); res.add(propV);
		int pos=0;
		
		for(int i=0;i<property.size();i++)
		    {
			pos=findPlace((Double)property.get(i),propV);
			if(pos==propV.size()) 
			    { 
				propV.add((Double)property.get(i)); 
				posV.add(new Integer(i)); 
			    }
			else 
			    {
				propV.add(pos,(Double)property.get(i)); 
				posV.add(pos,new Integer(i));
			    }
		    }
		return res;
	    }

	    public static int findPlace(Double value, Vector val)
	    {
		double vi=value.doubleValue(),vp;
		int i=0;
		for(i=0;i<val.size();i++)
		    {
			vp=((Double)val.get(i)).doubleValue();
			if(vi>=vp) break;    
		    }
		return i; 
	    }
	    
	    public static Vector normalizeProperty(Vector property)
	    {
	    	Vector normalizeProp = new Vector();
	    	
	    	return normalizeProp;
	    }
	    
	    public static double percent(Vector sortedTrace, double value, boolean zero)
	    {
	    	//System.out.println("******* size sorted trace:"+sortedTrace.size());
	    	int k;
	    	if (!zero)
	    	{
	    		int i=0;
	    		k=0;
	    		//int k=0;
		    	while (i<sortedTrace.size())
		    	{
		    		//if (((Double)(sortedTrace.get(k))).doubleValue()==0.0) sortedTrace.remove(k);
		    		//else k++;
		    		if (((Double)(sortedTrace.get(i))).doubleValue()!=0.0) k++;
		    		i++;
		    	}
	    	}
	    	else k=sortedTrace.size();
	    	//System.out.println("******* size sorted trace:"+sortedTrace.size()+" position:"+(int)(value*sortedTrace.size()));
	    	double seuil;
	    	//if (sortedTrace.size()>0) seuil=(Double)(sortedTrace.get((int)(value*sortedTrace.size())));
	    	if ((k>0)&&(value>0.0)) seuil=(Double)(sortedTrace.get((int)(value*k)-1));
	    	else seuil=1.1;
	    	return seuil; 
	    }
	    
	    public static boolean hasValue(Vector trace)
	    {
	    	int i=0;
	    	while (i<trace.size())
	    	{
	    		if (((Double)(trace.get(i))).doubleValue()!=0.0) break;
	    		i++;
	    	}
	    	if (i>=trace.size()) return false;
	    	else return true;
	    	
	    }
	    
	    /** prend en entree des proprietés entre 0 et 1 */
	    
	    public static Vector invertProperty(Vector property)
	    {
	    	Vector invertedProperties=new Vector(property.size());
	    	for(int i=0;i<property.size();i++)
		    {
	    		invertedProperties.add((Double)property.get(i)*(-1.0)+1.0);
		    }
	    	return invertedProperties;
	    	
	    }
	 
	

	
	
}
