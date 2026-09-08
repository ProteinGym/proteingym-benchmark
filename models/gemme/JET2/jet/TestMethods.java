package jet;

import java.util.*;
import java.io.*;

/** Classe permettant d'effectuer des mesures de qualité des résultats 
 * (sensibilité,spécificité ...) pour une structure (un fichier pdb) */

public class TestMethods
{
	
	/** Calcul des mesures de qualité des résultats 
	 * (sensibilité,spécificité ...) pour une structure. Les arguments dans 
	 * cet ordre sont les suivants:
	 * <BR> Fichier pdb pour lequel la colonne temperature contient les valeurs 
	 * traces pour chacuns des residus de la structure
	 * <BR> Fichier pdb pour lequel la colonne temperature identifie les residus 
	 * interface de la structure
	 * <BR> Fichier pdb pour lequel la colonne temperature identifie les residus 
	 * accessible de la structure
	 * <BR> Fichier de sortie des resultats
	 * <BR> Valeur limite du score d'un résidu au dessus duquel on considere le
	 * residu comme positif */
	
    public static Vector main(String[] args)
    {
    if(args.length!=5)
		{
    	System.err.println("Wrong usage : [pdb-trace-input] [pdb-interface-input] [pdb-access-input] [result-output] [seuil-score]");
    	System.exit(1);
		}
    /* Le cinquième argument est le seuil de score de residu jusqu'auquel on analyse les résultats */
    double seuilScore=Double.parseDouble(args[4]);
    /* Le quatrieme argument est le nom du fichier de sortie où écrire les resultats */
    String output_filename=args[3];
    /* Le premier argument est un fichier pdb pour lequel la colonne 
	 * temperature contient les valeurs traces pour chaque residus */
	String filename=args[0];
	jet.io.file.PdbFileReader pdb= new jet.io.file.PdbFileReader(filename);
	/* on recupere les infos 3D */
	Vector pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
	/* on recupere les proprietes (colonne temperature = trace) */
	Vector orig=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(0)).getPropertyData();
	/* Le deuxieme argument est un fichier pdb pour lequel la colonne 
	 * temperature identifie les residus aux interfaces */
	pdb= new jet.io.file.PdbFileReader(args[1]);
	/* on recupere les infos 3D */
	pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
	/* on recupere les proprietes (colonne temperature = residus interface) */
	Vector inter=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(0)).getPropertyData();	
	/* Le troisième argument est un fichier pdb pour lequel la colonne 
	 * temperature identifie les residus accessibles */
	pdb= new jet.io.file.PdbFileReader(args[2]);
	/* on recupere les infos 3D */
	pdbInfo=jet.data.dataformat.parser.PDB.getSequenceInfo(pdb,false);
	/* on recupere les proprietes (colonne temperature = residus accessibles) */
	Vector surf=((jet.data.dataformat.info.PdbSequenceInfo)pdbInfo.get(0)).getPropertyData();	
	/* on passe l'accessibilité en booleen */
	for (int r=0;r<surf.size();r++)
	{
		if (((Double)surf.get(r))==1.0) surf.set(r, true);
		else surf.set(r, false);
	}
	/* Tri decroissant des proprietes (colonne temperature) de la premiere sequence et sauvegarde 
	 * des positions d'origine de ces proprietes */
	Vector v=jet.tools.OrderValue.orderProperty(orig);
	/* Positions des proprietes */
	Vector oPos=(Vector)v.get(0);
	/* Proprietes */
	Vector oProp=(Vector)v.get(1);
	
	int i,pos=0,surfPos=0;
	/* Le troisieme argument est une valeur limite pour la trace */
	int trancheTrace=5;
	double[] accuracyMoyTab= new double[2];
	accuracyMoyTab[0]=0.0;
	accuracyMoyTab[1]=0.0;
	
	Vector l_trResult=new Vector(),covSResult=new Vector(),covResult=new Vector(),sensResult=new Vector();
	Vector PPVResult=new Vector(),specResult=new Vector(),accuracyResult=new Vector();
	Vector errorResult=new Vector();
	Vector PPVExpect=new Vector(),sensExpect=new Vector(),specExpect=new Vector(),accuracyExpect=new Vector();
	Vector errorExpect=new Vector();
	double vTP=0,vFP=0,vTN=0,vFN=0;
	int siteSize=0,surfSize=0,totalSize=inter.size();
	double prop,oldProp=((Double)oProp.get(0)).doubleValue()*1.1;
	/* Nombre de residus à l'interface et initialisation des faux négatifs */

	for(i=0;i<inter.size();i++)
	    {
		if((((Double)inter.get(i)).doubleValue()>2.5)&&(((Boolean)surf.get(i)).booleanValue()))
			{
			siteSize++;
			}
	    }

	vFN=siteSize;

	/* Nombre de residus accessibles et initialisation des vrai négatifs */

	for(i=0;i<surf.size();i++)
	    {
		if(((Boolean)surf.get(i)).booleanValue())
			{
			surfSize++;
			}
	    }
	vTN=surfSize-siteSize;
	
	double cov=0.0,covS=0.0,tempTrace;
	
	/* Sensibilité de l'article */
	double sens;
	if ((vTP==0)&&(vFN==0)) sens=0.0;
	else sens=(int)(((vTP)/(vTP+vFN))*100000)/100000.0;
	
	double E_sens=(int)((sens-(covS))*100000)/100000.0;
	
	/* rapport site sur residus surface utilisé pour calculer les valeurs attendues */
	
	double f=((double)siteSize/(double)surfSize);
	
	/* Spécificité par rapport aux residus de surface trouvés positifs (positives predictives value) */
	double ppv;
	if ((vTP==0)&&(vFP==0)) ppv=0.0;
	else ppv=(int)(((vTP)/(vTP+vFP))*100000)/100000.0;
	
	double E_ppv=(int)((ppv/f)*100000)/100000.0;
	
	/* Spécificité par rapport aux residus de surface trouvés negatifs */		
	double spec;

	if ((vTN==0)&&(vFP==0)) spec=0.0;
	else spec=(int)(((vTN)/(vTN+vFP))*100000)/100000.0;
	
	double E_spec=(int)((spec-(1.0-covS))*100000)/100000.0;
	
	/* Efficacité qui combine les deux mesures */	
	double accuracy;
		if ((vTN==0)&&(vFP==0)&&(vTP==0)&&(vFN==0)) accuracy=0.0;
	else accuracy=(int)(((vTN+vTP)/(vTN+vFP+vTP+vFN))*100000)/100000.0;
	
	double E_accuracy=(int)((accuracy-((covS*f)+(1.0-covS)*(1.0-f)))*100000)/100000.0;
	
	/* Erreur qui combine les deux mesures (sensibilité et spécificité sur les négatifs) */		
	double error=(int)((Math.sqrt(((1-sens)*(1-sens)+(1-spec)*(1-spec))))*100000)/100000.0;
	double E_error=(int)((Math.sqrt((1.0-covS)*(1.0-covS)+covS*covS)-error)*100000)/100000.0;
	
	/* Facteur de correlation qui combine toutes les mesures */
	
	/* Boucle sur les residus triés par trace decroissante. A chaque iteration on calcule
	 * la sensibilité, la selectivité ainsi que le taux de couverture des residus accessibles 
	 * et de l'ensemble des residus */
	
	for(i=0;i<oPos.size();i++)
	    {
		prop=((Double)oProp.get(i)).doubleValue();
		tempTrace=(int)(cov*100);
		
		while(tempTrace/trancheTrace>=1)
		{
			covResult.add(cov);covSResult.add(covS);sensResult.add(sens);sensExpect.add(E_sens);PPVResult.add(ppv);PPVExpect.add(E_ppv);
			specResult.add(spec);specExpect.add(E_spec);accuracyResult.add(accuracy);accuracyExpect.add(E_accuracy);
			errorResult.add(error);errorExpect.add(E_error);
			
			trancheTrace=trancheTrace+5;
		}

		/* Position de la propriete (du residu) */
		pos=((Integer)oPos.get(i)).intValue();
		/* incrementation du nombre de residus accessibles traités */
		if(((Boolean)surf.get(pos)).booleanValue()) surfPos++;
		/* Calcul du taux de couverture de l'ensemble des residus de la proteine */
		cov=(int)((((double)i+1)/((double)totalSize))*100000)/100000.0;
		/* Calcul du taux de couverture des residus accessibles de la proteine */
		covS=(int)((((double)surfPos)/((double)surfSize))*100000)/100000.0;
		/* Au dessus d'une valeur "lim" de la trace on calcule toutes les mesures, 
		 * en dessous on ne calcule que les taux de couverture */
		
		if(prop<=seuilScore);
		
		else
		    {

			/* Si le residu a une trace supérieure à "lim", que c'est un residu de surface,
			 * et qu'il appartient à l'interface c'est un vrai positif (TP) */
			
			if(((Boolean)surf.get(pos)).booleanValue())
			    {
				/* 2.6 pour eviter les residus à l'interface et non accessibles */
				if(((Double)inter.get(pos)).doubleValue()>2.5)
					{
					vTP=vTP+1;
					vFN=vFN-1;
					}
				if(((Double)inter.get(pos)).doubleValue()==0.0)
					{
					vFP=vFP+1;
					vTN=vTN-1;
					}
				}

			/* Sensibilité de l'article */
			if ((vTP==0)&&(vFN==0)) sens=0;
			else sens=(int)(((vTP)/(vTP+vFN))*100000)/100000.0;
			
			E_sens=(int)((sens-(covS))*100000)/100000.0;
			
			/* Spécificité par rapport aux residus de surface trouvés positifs (positives predictives value) */
			if ((vTP==0)&&(vFP==0)) ppv=0;
			else ppv=(int)(((vTP)/(vTP+vFP))*100000)/100000.0;
			
			E_ppv=(int)((ppv/f)*100000)/100000.0;
			
			/* Spécificité par rapport aux residus de surface trouvés negatifs */		
		
			if ((vTN==0)&&(vFP==0)) spec=0;
			else spec=(int)(((vTN)/(vTN+vFP))*100000)/100000.0;
			
			E_spec=(int)((spec-(1.0-covS))*100000)/100000.0;
			
			/* Efficacité qui combine les deux mesures */	
		
			if ((vTN==0)&&(vFP==0)&&(vTP==0)&&(vFN==0)) accuracy=0;
			else accuracy=(int)(((vTN+vTP)/(vTN+vFP+vTP+vFN))*100000)/100000.0;
			
			E_accuracy=(int)((accuracy-((covS*f)+(1.0-covS)*(1.0-f)))*100000)/100000.0;
			
			/* Erreur qui combine les deux mesures (sensibilité et spécificité sur les négatifs) */		
			error=(int)(Math.sqrt(((1-sens)*(1-sens)+(1-spec)*(1-spec)))*100000)/100000.0;
			
			E_error=(int)((Math.sqrt((1.0-covS)*(1.0-covS)+covS*covS)-error)*100000)/100000.0;
			
			/* Facteur de correlation qui combine toutes les mesures */
		
			/* Peut etre pour evaluer si la trace precedente etait supérieure 
			 * à la trace actuelle (1.001 pour cause d'arrondi) ==> On affiche 
			 * les mesure que si la trace diminue */

		   }
	    }
		tempTrace=(int)(cov*100);
	
		while(tempTrace/trancheTrace>=1)
	    {
			covResult.add(cov);covSResult.add(covS);sensResult.add(sens);sensExpect.add(E_sens);PPVResult.add(ppv);PPVExpect.add(E_ppv);
			specResult.add(spec);specExpect.add(E_spec);accuracyResult.add(accuracy);accuracyExpect.add(E_accuracy);
			errorResult.add(error);errorExpect.add(E_error);
	
			trancheTrace=trancheTrace+5;
	    }
	
	Vector nom_colonnes=new Vector(12);
	Vector result=new Vector(12);
	
	nom_colonnes.add("cover");nom_colonnes.add("coverSurf");nom_colonnes.add("sens");nom_colonnes.add("ScSen");
	nom_colonnes.add("PPV");nom_colonnes.add("ScPPV");nom_colonnes.add("spec");nom_colonnes.add("ScSpe");
	nom_colonnes.add("acc");nom_colonnes.add("ScAcc");nom_colonnes.add("EF");nom_colonnes.add("ScEF");
	
	result.add(covResult);result.add(covSResult);result.add(sensResult);result.add(sensExpect);result.add(PPVResult);result.add(PPVExpect);
	result.add(specResult);result.add(specExpect);result.add(accuracyResult);result.add(accuracyExpect);
	result.add(errorResult);result.add(errorExpect);
	
	Result.WriteResult(result, nom_colonnes, output_filename);
	
	return result;
	
    }
    
}

