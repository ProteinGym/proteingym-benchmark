package jet.tools;
import java.util.*;

public class Statistic
{
	/** Normalise les valeurs contenues par un vecteur v (v(i)=(v(i)-min(v))/(max(v)-min(v))) */
	
    public static Vector normalize(Vector v)
    {
	Vector out=new Vector(v);
	double min=min(v), max=max(v);
	double d,dif=max-min;
	for(int i=0;i<v.size();i++)
	    {
		d=((Double)v.get(i)).doubleValue();
		d=(d-min)/dif;
		out.add(new Double(d));
	    }
	return out;
    }

    /** Calcul de la dispersion autour de la moyenne */
    
    public static double standardError(Vector v)
	{
	    
	    double mu = mean(v), mu2=mean(product(v,v));
	    
	    return Math.sqrt(Math.abs((mu*mu)- mu2)*(v.size()/(v.size()-1)));
	}

    /** Retourne un vecteur v contenant le produit des valeurs (v(i)=v1(i)*v2(i)) 
     * de deux vecteurs v1 et v2 */
    
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
    
    /** Prend en entree soit un vecteur de valeur soit une matrice 
     * de valeurs et retourne la valeur minimale */
    
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
    
    /** Prend en entree soit un vecteur de valeur soit une matrice 
     * de valeurs et retourne la valeur maximale */
    
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

    /* Prend en entree soit un vecteur v de valeur soit une matrice v
     * de valeurs et retourne le degré de significativité de ces valeurs.
     * C'est à dire la moyenne des niveaux relatifs obtenus sur l'ensemble 
     * des alignements (position i dans v <=> niveau d'un residu dans 
     * l'alignement i). Cette formule est conforme à la formule de l'article. */
    
    /** Prend en entree soit un vecteur v de valeur soit une matrice v
     * de valeurs et retourne la moyenne de ces valeurs */

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
	    /* Somme des niveaux obtenus pour ce residu sur chaque alignment */
		while(iter.hasNext()) d+=((Double)iter.next()).doubleValue();
	    }
	    /* Moyenne sur l'ensemble des alignements des niveaux relatif obtenus pour ce residu  */
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
    
    public static double log10(java.math.BigDecimal a) 
    {
    	double res;
    	String s=a.toString();
    	//System.out.println("bigDec:"+s);
    	int posExp=s.lastIndexOf("E");
    	if (posExp!=-1)
    	{
	    	String exp=s.substring(posExp+1);
	    	String cst=s.substring(0,posExp);
	    	//System.out.println("exp:"+exp);
	    	//System.out.println("cst:"+cst);
	    	res=Math.log10(new java.math.BigDecimal(""+cst).doubleValue())+Double.valueOf(exp).doubleValue();
    	}
    	else res=Math.log10(a.doubleValue());
    //	System.out.println("doubleRes:"+res);
    	return res;
     }
    
    public static java.math.BigDecimal log(java.math.BigInteger a, int precision) {
    	String s=a.toString();
    	int n=s.length();
    	String s1="1";
    	for (int i=0;i<n-precision;i++) s1=s1+"0";
    	return new java.math.BigDecimal(Math.log(a.divide(new java.math.BigInteger(s1)).doubleValue())).add(new java.math.BigDecimal(s1));
    }
    	
    public static double log(java.math.BigInteger a, double base) {
        int b = a.bitLength() - 1;
        double c = 0;
        double d = 1;
        for (int i = b; i >= 0; --i) {
            if (a.testBit(i))
                c += d;
            d *= 0.5;
        }
        return (Math.log(c) + Math.log(2) * b) / Math.log(base);
    }
    
    public static double nbCombinaison(int n, int p)
    {
    	double nbCombinaison=1.0;
    	int max,min;
    	if (p>(n-p))
    	{
    		max=p;
    		min=n-p;
    	}
    	else
    	{
    		max=n-p;
    		min=p;
    	}
    	for (int i=n;i>max;i--)
    		nbCombinaison=nbCombinaison*(double)i;
    	for (int i=min;i>1;i--)
    		nbCombinaison=nbCombinaison/(double)i;
    	return nbCombinaison;
    } 
    
    
}
