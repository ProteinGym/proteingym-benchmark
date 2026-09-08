package jet.tree.tools;
import java.util.*;

public class Statistic
{
    public static Vector normalize(Vector v)
    {
	Vector out=new Vector(v);
	double min=min(v), max=max(v);
	double d,dif=max-min;
	for(int i=0;i<v.size();i++)
	    {
		d=((Double)v.get(i)).doubleValue();
		d=(d-min)/dif;
		v.add(new Double(d));
	    }
	return out;
    }

    public static double standardError(Vector v)
	{
	    
	    double d=0.0, mu = mean(v), mu2=mean(product(v,v));
	    
	    return Math.sqrt(Math.abs((mu*mu)- mu2)*(v.size()/(v.size()-1)));
	}

    
    public static Vector product(Vector v1, Vector v2)
	{
	    if(v1.size()==v2.size())
	    {
		Vector result=new Vector(v1.size());

		for(int i=0;i<v1.size();i++) result.add(new Double(((Double)v1.get(i)).doubleValue()*((Double)v2.get(i)).doubleValue()));
		
		return result;
	    }
	    else return null;
	}
    
    public static double min(Vector v)
	{
	    
	    double d,min=1000000.0;
	    boolean table=false;
	    Iterator iter=v.iterator(), iter1;
	    
	    if(v.size()>0)
	    {
		if(((Object)v.get(0)) instanceof Vector) table=true;
	    }
	    
	    if(table)
	    {
		while(iter.hasNext())
		{
		    iter1=((Vector)iter.next()).iterator();
		    while(iter1.hasNext())
		    {
			if((d=((Double)iter1.next()).doubleValue())<min) min=d;
		    }
		}
	    }
	    
	    else
	    {
		while(iter.hasNext())
		{
		    if((d=((Double)iter.next()).doubleValue())<min) min=d;
		}
	    }
	    return min;
	}
    public static double max(Vector v)
	{
	    
	    double d,max=-10000.0;
	    boolean table=false;
	    Iterator iter=v.iterator(), iter1;
	    if(v.size()>0)
	    {
		if((Object)v.get(0) instanceof Vector) table=true;
	    }
	    
	    if(table)
	    {
		while(iter.hasNext())
		{
		    iter1=((Vector)iter.next()).iterator();
		    while(iter1.hasNext())
		    {
			if((d=((Double)iter1.next()).doubleValue())>max) max=d;
		    }
		}
	    }
	    
	    else
	    {
		while(iter.hasNext())
		{
		    if((d=((Double)iter.next()).doubleValue())>max) max=d;
		}
	    }
	    return max;
	}
    

    public static double mean(Vector v)
	{
	    boolean table=false;
	    double size=(double)v.size(),d=0.0;
	    Iterator iter=v.iterator(), iter1;
	    
	    if(size>0)
	    {
		if((Object)v.get(0) instanceof Vector)
		{
		    size=(double)(v.size()*((Vector)v.get(0)).size());
		    table=true;
		}
	    }
	    
	    if(table)
	    {
		while(iter.hasNext())
		{
		    iter1=((Vector)iter.next()).iterator();
		    while(iter1.hasNext()) d+=((Double)iter1.next()).doubleValue();
		}
	    }
	    else
	    {
		while(iter.hasNext()) d+=((Double)iter.next()).doubleValue();
	    }
	    
	    return d/size;
	}
    
    public static Vector resample(Vector v, int resampleSize)
	{
	    Vector resample=new Vector(resampleSize);
	    
	    for(int b=0; b<resampleSize; b++) 
		resample.add((Double)v.get((int)(Math.random()*(double)v.size())));
	    
	    return resample;
	}
    
    public static double pNorm(double z) { return pNorm(z,0.0005); }
    
    public static double pNorm(double z, double dz)
	{
	    double d=0.0;
	    if(z<=-5.2) return 1.0;
	    while(z<5.2) { d+= dz * fNorm(z); z+=dz; }
	    return d;
	    
	}
    
    public static double fNorm(double z)
    {
	return ( 1.0 / Math.sqrt(Math.PI * 2.0) )* Math.exp( (z*z) / -2.0 );
    }
    
    public static double zScore(double x, double mu, double sigma)
    {
	return (x-mu)/sigma; 
    }
    
    public static double zToValue(double z, double mu, double sigma)
    {
	return mu+(z*sigma);
    }        
}
