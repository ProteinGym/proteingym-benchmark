package jet.data.dataformat.parser;

import java.util.*;
import java.io.*;

import javax.vecmath.Point3f;

import jet.data.datatype.*;

/** Classe pour parser et stocker les informations de sequences et de structure 3D d'un fichier pdb. */

public class PDB
{
    
    public static Vector getModResidus(jet.data.dataformat.Format cf) 
    {
	Vector resMod=new Vector();
	String str;
	boolean present;
	Vector pdbData=cf.getData();
	for(int i=0;i<pdbData.size();i++)
	    {
		if(((String)pdbData.get(i)).indexOf("MODRES")==0)
		    {
			str=((String)pdbData.get(i)).substring(16,22);
			present = false;
			for (int z=0;z<resMod.size();z++) if (str.equals(((String)resMod.get(z)))) present=true;
			if (!present) resMod.add(str);
		    }
		if(((String)pdbData.get(i)).indexOf("ATOM")==0)
		    {
			for (int z=0;z<resMod.size();z++)
			    {
				if ((((String)pdbData.get(i)).substring(21,22).trim().equals(((String)resMod.get(z)).split("\\s+")[0].trim()))
				    &&(((String)pdbData.get(i)).substring(22,27).trim().equals(((String)resMod.get(z)).split("\\s+")[1].trim())))
				    {
					
					resMod.remove(z);
				    }
			    }
		    }
	    }
	return resMod;
    }
    
    
    
    /** Retourne les codes enzymes des séquences contenues dans le fichier PDB */
    
    public static Vector getEnzymeCodes(jet.data.dataformat.Format cf) 
    {
	Vector enzymeCodes=new Vector();
	String enzymeCode="";
	String line="";
	String[] subLine;
	Vector data=cf.getData();
	Iterator iter=data.iterator();
	while(iter.hasNext())
	    {
		line=(String)iter.next();
		/* Est ce une ligne COMPND */
		if((line.trim().length()>=6)&&(line.substring(0,6).compareTo("COMPND")==0))
		    {
			//System.out.println(line);
			subLine=line.split("\\s+");
			for (int i=0; i<subLine.length-1; i++)
			    {
				//System.out.println(subLine[i]);
				if (subLine[i].equals("EC:"))
				    {
					enzymeCode=new String(subLine[i+1]);
					if (enzymeCode.endsWith(";")) 
					    enzymeCode=enzymeCode.substring(0, enzymeCode.length()-1);
					enzymeCodes.add(enzymeCode);
				    }
				else
				    {
					if ((subLine[i].lastIndexOf("(E.C.")==0)&&(subLine[i].lastIndexOf(")")==subLine[i].length()-1))
					    {
						enzymeCode=new String(subLine[i].substring(5, subLine[i].length()-1));
						enzymeCodes.add(enzymeCode);
					    }
				    }
			    }
		    }
	    }
	return enzymeCodes;
    }
    
    
    /** Parseur d'un fichier pdb, retourne un vecteur de jprotein.data.dataformat.info.PdbSequenceInfo 
     * (sequence3D et vecteur de proprietes des residus composant la sequence3D). 
     * parametre atomAccessFactor: recuperation ou non de l'accessibilité des atomes */
    
    public static Vector getSequenceInfo(jet.data.dataformat.Format cf,boolean atomAccessFactor) 
    {
	Vector sequenceList=new Vector(1,1);
	
	//try
	// {
	Vector data=cf.getData();
	Iterator iter=data.iterator();
	
	jet.data.datatype.Sequence3D seq=null;
	jet.data.datatype.Residue3D res=null;
	
	float x,y,z;
	boolean access;double accessFactor;
	Vector prop=null;
	String line="",header="",chainID="",residueID="";
	int residuePos=-1;
	boolean alternatif=false;
	String firstAlternatif="";
	boolean isNonStd;
	
	if(data.size()>0)
	    {
		line=(String)data.get(0);			
		/* Sommes nous à la premiere ligne? */
		if((line.trim().length()>65)&&(line.substring(0,6).compareTo("HEADER")==0))
		    {
			/* Oui ==> on recupere le code de la proteine */
			if((line=line.substring(62,66).trim()).length()!=0) header=line;
			else
			    {
				String filename=((jet.io.file.PdbFileReader)cf).getPath();
				header=filename.substring(filename.lastIndexOf("/")+1,filename.lastIndexOf("/")+5);
			    }
		    }
	    }
	
	while(iter.hasNext())
	    {
		line=(String)iter.next();
		//System.out.println(line);
		/* Est ce une ligne atom */
		if(line.trim().length()>60)
		    {
			isNonStd=( (line.substring(0,6).compareTo("HETATM")==0)&&((line.substring(17,20).compareTo("MSE")==0)||(line.substring(17,20).compareTo("HYP")==0)||(line.substring(17,20).compareTo("MLY")==0)||(line.substring(17,20).compareTo("SEP")==0)||(line.substring(17,20).compareTo("TPO")==0)||(line.substring(17,20).compareTo("PTR")==0)) );
			//System.out.println(line.substring(17,20));
			if( (line.substring(0,4).compareTo("ATOM")==0) || ((line.substring(0,6).compareTo("HETATM")==0) && isNonStd) )
			    {	
				/* Si c'est une chaine nouvelle, on doit ajouter une sequence a la liste*/
				/* Ceci est fait au debut de l'acquisition de la chaine */
				if(chainID.compareTo(line.substring(21,22))!=0)
				    {	
					/* Creation des proprietes et des structures 3D */
					seq=new jet.data.datatype.Sequence3D();						
					prop=new Vector(1,1);
					/* Ajout a la liste d'une sequence 3D et de ces proprietes */
					sequenceList.add(new jet.data.dataformat.info.PdbSequenceInfo(seq,prop));
					chainID=line.trim().substring(21,22);
					/* Sans les espaces, on a une lettre si plusieurs chaines, sinon rien (chainID.length()==0) */
					if(chainID.length()>0)
					    {
						seq.setSequenceName(header+chainID);
						seq.setChainId(chainID);
					    }
					else
					    {
						seq.setSequenceName(header+"1");
						seq.setChainId("1");
					    }
					/* On initialise le residu car celui ci change forcement 
					 * (pour eviter les cas ou on change de chaine mais que la position du residu reste la meme) */
					residueID="";
				    }
				
				/* Si nouveau residu, on ajoute un residu. */
				if(residueID.compareTo(line.substring(22,27).trim())!=0)
				    {
					/* if alternative location indicator is not empty */
					/* and the indicator is not "A", then the residue is skipped */
					if(line.substring(16,17).trim().length()!=0){
					    if(!line.substring(16,17).trim().equals("A")){
						continue;}}
					residueID=line.substring(22,27).trim();
					residuePos+=1;
					//System.out.println(residueID);
					//System.out.println(residuePos);
					/* On cree un residu (nom et position) */
					//res=new jet.data.datatype.Residue3D(line.substring(17,20),Integer.valueOf(residueID).intValue());
					res=new jet.data.datatype.Residue3D(line.substring(17,20),residuePos,residueID);
					/* On l'ajoute a la sequence */
					seq.add(res);
					/* On ajoute aussi les proprietes (temperature) */
					/* Je ne comprend pas la distinction car la temperature est toujours aux positions (60,65) */
					/* A mon avis inutile car on ajoute les proprietes que pour les residus
					 *  alors qu'elles changent pour chaque atome du residu. */
					if(line.trim().length()<=66)
					    prop.add(new Double(Double.parseDouble(line.substring(60,line.trim().length()).trim())));
					
					else
					    prop.add(new Double(Double.parseDouble(line.substring(60,66).trim())));
				    }
				/* On ne considere pas les positions alternatives pour les atomes (caractere à la position 16) */
				if((line.substring(16,17).trim().length()!=0)&&(alternatif)&&(line.substring(16,17).trim().equals(firstAlternatif)))
				    {
					alternatif=false;
				    }
				if(line.substring(16,17).trim().length()==0)
				    {
					alternatif=false;
					firstAlternatif="";
				    }
				/* at the beginning it is always the case - we get the atom anyway */
				if (!alternatif)
				    {
					/* Recuperation des coordonnees 3D de l'atome */
					x=Float.parseFloat(line.substring(30,38).trim());
					y=Float.parseFloat(line.substring(38,46).trim());
					z=Float.parseFloat(line.substring(46,54).trim());
					/* Recuperation de l'accessibilité de l'atome */
					if(line.trim().length()<=66)
					    accessFactor=Double.parseDouble((line.substring(60,line.trim().length()).trim()));
					else
					    accessFactor=Double.parseDouble((line.substring(60,66).trim()));
					/* Accessibilité à true si non recuperee et true ou false (en fonction de la valeur) si recuperee */
					if ((accessFactor>0.0)||(!atomAccessFactor))
					    access=true;
					else
					    access=false;
					/* Ajout de l'atome au residu. */
					res.addAtom(line.substring(12,16),Integer.parseInt((line.substring(6,11).trim())),access,new Point3f(x,y,z));
				    }
				/* if the alternate location indicator is not empty */ 
				if((line.substring(16,17).trim().length()!=0)&&(!alternatif))
				    {
					/* if the indicator is "A" */
					if(line.substring(16,17).trim().equals("A")){
					    alternatif=true;
					    firstAlternatif=line.substring(16,17).trim();}
				    }
				
			    }
		    }
		else{
		    if(line.trim().length()>5)
			{
			    if(line.substring(0,6).compareTo("ENDMDL")==0)
				{
				    //    System.out.println(line.substring(0,6));
				    chainID=chainID+chainID;
				}
			    
			}   
		}   
	    }
	// }
	
	//catch(Exception e){System.err.println("Error reported in pdb parser : "+e);}
	return sequenceList;
    }

    public static Vector getSequenceInfo(jet.data.dataformat.Format cf,boolean atomAccessFactor, boolean chain)
    {

	//System.out.print("La valeur de chain est: ");
	//System.out.println(chain);
        Vector sequenceList=new Vector(1,1);
        //try
           // {
                Vector data=cf.getData();
                Iterator iter=data.iterator();

                jet.data.datatype.Sequence3D seq=null;
                jet.data.datatype.Residue3D res=null;

                float x,y,z;
                boolean access;double accessFactor;
                Vector prop=null;
                String line="",header="",chainID="No",residueID="";
		int residuePos=-1;
                boolean alternatif=false;
                String firstAlternatif="";
		boolean isNonStd;

                if(data.size()>0)
                    {
                        line=(String)data.get(0);
                        /* Sommes nous à la premiere ligne? */
                        if((line.trim().length()>65)&&(line.substring(0,6).compareTo("HEADER")==0))
                            {
                                /* Oui ==> on recupere le code de la proteine */
                                if((line=line.substring(62,66).trim()).length()!=0) header=line;
                                else
                                        {
                                        String filename=((jet.io.file.PdbFileReader)cf).getPath();
                                        header=filename.substring(filename.lastIndexOf("/")+1,filename.lastIndexOf("/")+5);
                                        }
                            }
                    }

                while(iter.hasNext())
                    {
                        line=(String)iter.next();
                        /* Est ce une ligne atom */
			if(line.trim().length()>60)
                            {
                                isNonStd=( (line.substring(0,6).compareTo("HETATM")==0)&&((line.substring(17,20).compareTo("MSE")==0)||(line.substring(17,20).compareTo("HYP")==0)||(line.substring(17,20).compareTo("MLY")==0)||(line.substring(17,20).compareTo("SEP")==0)||(line.substring(17,20).compareTo("TPO")==0)||(line.substring(17,20).compareTo("PTR")==0)) );
                                if( (line.substring(0,4).compareTo("ATOM")==0) || ((line.substring(0,6).compareTo("HETATM")==0) && isNonStd) )
                                    {
                                        /* Si c'est une chaine nouvelle, on doit ajouter une sequence a la liste*/
                                        /* Ceci est fait au debut de l'acquisition de la chaine */
                                        if(chainID.compareTo(line.substring(21,22))!=0) 
					{
						if( (chainID.compareTo("No")==0) || chain )
						{   
                                                    /* Creation des proprietes et des structures 3D */
                                                    seq=new jet.data.datatype.Sequence3D();
                                                    prop=new Vector(1,1);
                                                    /* Ajout a la liste d'une sequence 3D et de ces proprietes */
                                                    sequenceList.add(new jet.data.dataformat.info.PdbSequenceInfo(seq,prop));
                                                    /* Sans les espaces, on a une lettre si plusieurs chaines, sinon rien (chainID.length()==0) */
                                                    if(chainID.length()>0)
                                                    {
							if(chain)
							{
							   seq.setSequenceName(header+line.trim().substring(21,22));
                                                           seq.setChainId(line.trim().substring(21,22));
							}
							else
							{
  							    seq.setSequenceName(header+"1");
                                                            seq.setChainId("1");
							}
						    }
						}
						chainID=line.trim().substring(21,22);
                                                /* On initialise le residu car celui ci change forcement 
                                                 * (pour eviter les cas ou on change de chaine mais que la position du residu reste la meme) */
                                                residueID="";
                                        }

                                        /* Si nouveau residu, on ajoute un residu. */
                                        if(residueID.compareTo(line.substring(22,27).trim())!=0)
                                            {
						if(line.substring(16,17).trim().length()!=0){
						    if(!line.substring(16,17).trim().equals("A")){
							continue;}}
                                                residueID=line.substring(22,27).trim();
						residuePos+=1;
                                                /*System.out.println(residueID);
                                                System.out.println(residuePos);*/
                                               /* On cree un residu (nom et position) */
                                              //res=new jet.data.datatype.Residue3D(line.substring(17,20),Integer.valueOf(residueID).intValue());
                                               res=new jet.data.datatype.Residue3D(line.substring(17,20),residuePos,residueID,chainID);
                                                /* On l'ajoute a la sequence */
                                                seq.add(res);
                                                /* On ajoute aussi les proprietes (temperature) */
                                                /* Je ne comprend pas la distinction car la temperature est toujours aux positions (60,65) */
                                                /* A mon avis inutile car on ajoute les proprietes que pour les residus
                                                 *  alors qu'elles changent pour chaque atome du residu. */
                                                if(line.trim().length()<=66)
                                                    prop.add(new Double(Double.parseDouble(line.substring(60,line.trim().length()).trim())));

                                                else
                                                    prop.add(new Double(Double.parseDouble(line.substring(60,66).trim())));
                                            }
                                        /* On ne considere pas les positions alternatives pour les atomes (caractere à la position 16) */
                                        if((line.substring(16,17).trim().length()!=0)&&(alternatif)&&(line.substring(16,17).trim().equals(firstAlternatif)))
                                                {
                                                alternatif=false;
                                                }
                                        if(line.substring(16,17).trim().length()==0)
                                                {
                                                alternatif=false;
                                                firstAlternatif="";
                                                }
                                        if (!alternatif)
                                                {
                                                /* Recuperation des coordonnees 3D de l'atome */
                                                x=Float.parseFloat(line.substring(30,38).trim());
                                                y=Float.parseFloat(line.substring(38,46).trim());
                                                z=Float.parseFloat(line.substring(46,54).trim());
                                                /* Recuperation de l'accessibilité de l'atome */
                                                if(line.trim().length()<=66)
                                                        accessFactor=Double.parseDouble((line.substring(60,line.trim().length()).trim()));
                                                else
                                                        accessFactor=Double.parseDouble((line.substring(60,66).trim()));
                                                /* Accessibilité à true si non recuperee et true ou false (en fonction de la valeur) si recuperee */
                                                if ((accessFactor>0.0)||(!atomAccessFactor))
                                                        access=true;
                                                else
                                                        access=false;
                                                /* Ajout de l'atome au residu. */
                                                res.addAtom(line.substring(12,16),Integer.parseInt((line.substring(6,11).trim())),access,new Point3f(x,y,z));
                                                }

                                        if((line.substring(16,17).trim().length()!=0)&&(!alternatif))
                                                {
						    if(line.substring(16,17).trim().equals("A")){
							alternatif=true;
							firstAlternatif=line.substring(16,17).trim();}
                                                }

                            }
			}
			else{
			    if(line.trim().length()>5)
				{
				    if(line.substring(0,6).compareTo("ENDMDL")==0)
					{
					    // System.out.println(line.substring(0,6));
					    chainID=chainID+chainID;
					}
				    
				}
			}
                    }
           // }

        //catch(Exception e){System.err.println("Error reported in pdb parser : "+e);}
        return sequenceList;
    }

}
