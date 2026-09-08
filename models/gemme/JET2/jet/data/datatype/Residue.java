package jet.data.datatype;

import java.util.Vector;

/** Classe pour gerer les codes, symboles des residus, la position et l'accessibilite du residu **/

public class Residue
{

    //private static enum ResidueCode {TRP,ILE,PHE,LEU,CYS,MET,VAL,TYR,PRO,ALA,THR,HIS,GLY,SER,GLN,ASN,GLU,ASP,LYS,ARG,XX,GAP}
    //private static enum ResidueSymbol {W,I,F,L,C,M,V,Y,P,A,T,H,G,S,Q,N,E,D,K,R,B,Z,X,GAP}
    private static final String[] residueCode={"TRP","ILE","PHE","LEU","CYS","MET","VAL","TYR","PRO","ALA","THR","HIS","GLY","SER","GLN","ASN","GLU","ASP","LYS","ARG","B","Z","XXX","GAP","EXT"};
    //private static final String[] residueSymbol={"W","I","F","L","C","M","V","Y","P","A","T","H","G","S","Q","N","E","D","K","R","B","Z","X","GAP"};
    private static final char[] residueSymbol={'W','I','F','L','C','M','V','Y','P','A','T','H','G','S','Q','N','E','D','K','R','B','Z','X','-','.'};
    //private ResidueSymbol residue;
   
    private static final double[] residuePC={2.19,1.41,2.21,1.56,1.42,1.46,1.25,1.76,1.03,0.85,0.72,1.32,0.91,0.79,0.96,0.87,0.74,0.71,0.55,1.12,0.0,0.0,0.0,0.0,0.0};
    //private static final double[] residuePC={1.870,1.650,1.860,1.590,1.835,1.745,1.510,1.710,0.830,1.140,1.055,1.130,0.960,1.025,0.795,0.755,0.425,0.465,0.145,1.075,0.0,0.0,0.0,0.0,0.0};
    
    private int index; /** Numero du code ou symbole dans les 2 tableaux stockant les codes et symboles des aa **/
    private int pos;
    private String id;
    private String chainId="";
    private boolean access;

    //public Residue(ResidueSymbol residue){ this.residue=residue;}
    
    //public Residue(String residueName) { this((ResidueSymbol.values())[Residue.getResidueIndex(residueName)]); }

    /***/
    /** CONSTRUCTEURS */
    /***/
     
    public Residue(String residueName) { 
	if(residueName.compareTo("MSE")==0)
	   this.index=getResidueIndex("MET"); 
	else
	{
	   if(residueName.compareTo("HYP")==0)
		this.index=getResidueIndex("PRO");
	   else
	   {
		if(residueName.compareTo("MLY")==0)
		   this.index=getResidueIndex("LYS");
		else
		{
		   if(residueName.compareTo("SEP")==0)
			this.index=getResidueIndex("SER");
		   else
		  {
			if(residueName.compareTo("TPO")==0)
			    this.index=getResidueIndex("THR");
			else
			{
			    if(residueName.compareTo("PTR")==0)
				this.index=getResidueIndex("TYR");
			    else
				this.index=getResidueIndex(residueName);
			}
		  }
		}
	   }
	}
    }

    public Residue(char residueName){ this.index=getResidueIndex(residueName);}
      
    /***/
    /** ACCESSEURS */
    /***/
    
    //public int getResidueIndex(){ return residue.ordinal(); }
    public int getResidueIndex(){ return index; }
    public String getChainId(){ return chainId; }   
    /*
      public static int getResidueIndex(String residueName)
    {
	if(residueName.length()==1) return getResidueSymbol(residueName).ordinal();
	
	if(residueName.length()==3) return getResidueCode(residueName).ordinal();
	
	return ResidueSymbol.X.ordinal();
    }
    */
    
    public static double getResiduePC(char residueName)
    {
	
	for(int i=0;i<residueSymbol.length;i++)
	    {
		if(residueName==residueSymbol[i]) return residuePC[i];
	    }
	return 0.0;
    }
    
    public static double getResiduePC(int index)
    {
    	if((index<0)||(index>23)) return 0.0;
    	
    	else return residuePC[index];
    }
    
    public static double getResiduePC(String residueName)
    {
    	int i;
    	if(residueName.length()==3)
    	    {
    		for(i=0;i<residueCode.length;i++)
    		    {
    			if(residueName.toLowerCase().trim().compareTo(residueCode[i].toLowerCase().trim())==0) return residuePC[i];
    		    }
    	    }
    	else
    	    {
    		if(residueName.length()==1)
    		    {
    			return getResiduePC(residueName.charAt(0));
    		    }
    	    }
    	return 0.0;
    }
    
    public static int getResidueIndex(char residueName)
    {
	
	for(int i=0;i<residueSymbol.length;i++)
	    {
		if(residueName==residueSymbol[i]) return i;
	    }
	return 22;
    }
				      
    public static int getResidueIndex(String residueName)
    {
	int i;
	if(residueName.length()==3)
	    {
		for(i=0;i<residueCode.length;i++)
		    {
			if(residueName.toLowerCase().trim().compareTo(residueCode[i].toLowerCase().trim())==0) return i;
		    }
	    }
	else
	    {
		if(residueName.length()==1)
		    {
			return getResidueIndex(residueName.charAt(0));
		    }
	    }
	return 22;
    }
    public static char getResidueSymbol(int index)
    {
	if((index<0)||(index>23)) return 'X';
	
	else return residueSymbol[index];
    }

    public static String getResidueCode(int index)
    {
	if((index<0)||(index>23)) return "X";
	
	else return residueCode[index];
    }

    /*
      public static ResidueSymbol getResidueSymbol(String residueSymbol)
    {
	if((residueSymbol.compareTo("-")==0)||(residueSymbol.compareTo("*")==0)) return ResidueSymbol.GAP;
	try { return Enum.valueOf(ResidueSymbol.class, residueSymbol); }
	
	catch(Exception e) { System.out.println("error : unrecognized residue!!!!"); return ResidueSymbol.X; }
    }
    
    public static ResidueCode getResidueCode(String residueCode)
    {
	try { return Enum.valueOf(ResidueCode.class, residueCode); }
    
	catch(Exception e) { return ResidueCode.XX; }
    }
    
    public ResidueSymbol getResidueSymbol(){ return residue; }
    */
   //public String getResidueCode() { return getResidueCode(toString()).toString(); }

    public int getPosition() { return pos; }
    public String getId(){ return id;}
    
    public String getResidueCode()
    { 
	if(index<0) return "XXX";
	return residueCode[getResidueIndex()]; 
    }
    public char getResidueSymbol()
    {
	if(index<0) return 'X';
	return residueSymbol[getResidueIndex()];
    }

    public boolean isAccessible()
    {
	return access;
    }
   

    /***/
    /** MODIFICATEURS */
    /***/   
    
    public void setPosition(int pos){ this.pos=pos;}
    public void setId(String id){ this.id=id;}
    
    public void setAccessibility(boolean access)
    {
	this.access=access;
    }

    public void setChainId(String chainId) { this.chainId=chainId;; } 
 
    /***/
    /** METHODES */
    /***/
    
    public String toString()
    {
	return getResidueSymbol()+"";
    }
    
    public boolean equals(Object o)
    {
	if(o instanceof jet.data.datatype.Residue)
	    {
		if(((jet.data.datatype.Residue)o).getResidueIndex()==getResidueIndex()) return true;
	    }
	return false;
    }
    
}
