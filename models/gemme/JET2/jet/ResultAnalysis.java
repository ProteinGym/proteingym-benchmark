package jet;

import java.util.*;
import java.io.*;

public class ResultAnalysis {

	 public static void main(String[] args)
	    {
			String[] paramNames=new String[3]; 
		 	paramNames[0]="sample_size";paramNames[1]="num_sample";paramNames[2]="freq_cutoff";
		 	String[] paramValsSelected=new String[3];
		 	Vector stringInputFilesSelected=new Vector();
		 	//String[] paramNames=new String[2]; 
		 //	paramNames[0]="trMinCluster";paramNames[1]="trMinResidu";
		 	String inputDir="/home/stefan/projet_decrypton/resultats/all/[7m13d16h56mn32s]";
		 	for (int i=30;i<=50;i=i+10)
		 	{
		 		paramValsSelected[0]=""+i;paramValsSelected[1]="X";paramValsSelected[2]="X";
		 		stringInputFilesSelected.addAll(selectResult(paramValsSelected,inputDir));
		 	}
		 	
		 	ResultAnalysis.oneParamFixed(paramNames,stringInputFilesSelected,inputDir);
		 	//ResultAnalysis.noParamFixed(paramNames,inputDir);
		 	
	    }
	 
	 
	 public static void noParamFixed(String[] paramNames, String inputDir)
	 	{
		 	
		 	File resultDirectory=new File(inputDir);
		 	File[] listFileResults=resultDirectory.listFiles();
		 	String path="";
		 	int nbParam=paramNames.length;
		 	String param;
		 	String[] paramVals=new String[nbParam]; 
		 	double[] value=new double[4];
		 	
		 	Vector allParams=new Vector(nbParam);
		 	
		 	//Vector nbSeq=new Vector();
		 	//Vector nbAli=new Vector();
		 	//Vector freqTrace=new Vector();
		 	
		 	Vector sens=new Vector(nbParam);
		 	Vector spec=new Vector(nbParam);
		 	Vector ppv=new Vector(nbParam);
		 	Vector acc=new Vector(nbParam);
		 	
		 	for (int i=0;i<nbParam;i++)
			{
		 		allParams.add(new Vector());
			}
		 	
		 	Vector resultValues;
		 	Vector nomColonne;
		 	
		 	//int j=0;
		 	for (int i=0;i<listFileResults.length;i++)
			{
		 		path=listFileResults[i].getAbsolutePath();
		 		//if (path.lastIndexOf("jet_eval_clusterLine[")!=-1 )
		 		if (path.lastIndexOf("jet_eval_clusterLine[")!=-1 )
		 		{
		 			if (path.lastIndexOf("]")!=-1)
		 			{
			 			param=path.substring(path.lastIndexOf("[")+1,path.lastIndexOf("]"));
			 			paramVals=param.split("-");
			 			
			 			if (paramVals.length==nbParam)
			 			{
			 				
			 				nomColonne=Result.readCaracResult(path);
					 		resultValues=Result.readValuesResult(path);	
					 		int numCol=Result.searchNumCol(nomColonne,"pdbCode");
					 		
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "1ayb:P",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "1shc:B",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "1ycr:B",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "2ktq:B",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "2ktq:D",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "1bra",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "1n5y",resultValues ));
					 		Result.removeLine(resultValues, Result.searchNumLine(numCol, "2c3j",resultValues ));
			 			
					 		//for (int l=2;l<((Vector)resultValues.get(0)).size();) Result.removeLine(resultValues,l); 
					 		
					 		numCol=Result.searchNumCol(nomColonne, "ScoreSens");
					 		value[0]=Result.meanCol(resultValues,numCol);
					 		numCol=Result.searchNumCol(nomColonne, "ScoreSpec");
					 		value[1]=Result.meanCol(resultValues,numCol);
					 		numCol=Result.searchNumCol(nomColonne, "ScorePPV");
					 		value[2]=Result.meanCol(resultValues,numCol);
					 		numCol=Result.searchNumCol(nomColonne, "ScoreAcc");
					 		value[3]=Result.meanCol(resultValues,numCol);
					 			
					 		//System.out.println("params:"+params[0]+" "+params[1]+" "+params[2]+" sens:"+value[0]);

					 		for (int k=0;k<paramVals.length;k++) ((Vector)allParams.get(k)).add(paramVals[k]);
					 				
					 		sens.add(value[0]);
					 		spec.add(value[1]);
					 		ppv.add(value[2]);
					 		acc.add(value[3]);
					 			
			 			}
		 			}
		 		}
			};
		 	Vector data;
		 	Vector carac=new Vector();
		 	
		 	param="[";
			int k;
			for (k=0;k<(paramNames.length-1);k++)
			{
				param=param+paramNames[k]+"-";
			}
			param=param+paramNames[k]+"]";
		 	
			for (k=1;k<paramNames.length;k++) carac.add(paramNames[k]);
			
		 	carac.add("ScoreSens");carac.add("ScoreSpec");carac.add("ScorePPV");carac.add("ScoreAcc");

			data=new Vector();
				
			for (k=1;k<paramNames.length;k++) Result.addLine(data, (Vector)allParams.get(k));
			
			Result.addLine(data, sens);
			Result.addLine(data, spec);
			Result.addLine(data, ppv);
			Result.addLine(data, acc);
			Result.addCol(data, carac,0);
			
			((Vector)allParams.get(0)).add(0,paramNames[0]);
			
			Result.WriteResult(data, (Vector)allParams.get(0),inputDir+"/jet_eval_clusterLine_stat_"+param+".res");
			
	 }
	 
	 public static void oneParamFixed(String[] paramNames,Vector stringInputFilesSelected, String inputDir)
	 {
		 	String path="";
		 	int nbParam=paramNames.length;
		 	String[] paramVals=new String[nbParam];
		 	String param;
		 	double[] value=new double[4];
		 	int nbResult=0;
		 	
		 	Vector allParams=new Vector(nbParam);
		 	
		 	//Vector nbSeq=new Vector();
		 	//Vector nbAli=new Vector();
		 	//Vector freqTrace=new Vector();
		 	
		 	Vector sens=new Vector(nbParam);
		 	Vector spec=new Vector(nbParam);
		 	Vector ppv=new Vector(nbParam);
		 	Vector acc=new Vector(nbParam);
		 	
		 	Vector nbsens=new Vector(nbParam);
		 	Vector nbspec=new Vector(nbParam);
		 	Vector nbppv=new Vector(nbParam);
		 	Vector nbacc=new Vector(nbParam);
		 	
		 	for (int i=0;i<nbParam;i++)
			{
		 		sens.add(new Vector());
		 		spec.add(new Vector());
		 		ppv.add(new Vector());
		 		acc.add(new Vector());
		 		
		 		nbsens.add(new Vector());
		 		nbspec.add(new Vector());
		 		nbppv.add(new Vector());
		 		nbacc.add(new Vector());
		 		
		 		allParams.add(new Vector());
			}
		 	
		 	Vector resultValues;
		 	Vector nomColonne;
		 	
		 	//Vector stringInputFilesSelected=selectResult(paramValsSelected,inputDir);
		 	
		 	int j=0;
		 	for (int i=0;i<stringInputFilesSelected.size();i++)
			{
		 				path=(String)(stringInputFilesSelected.get(i));
			 			param=path.substring(path.lastIndexOf("[")+1,path.lastIndexOf("]"));
			 			paramVals=param.split("-");
			 			
			 			if (paramVals.length==nbParam)
			 			{
			 				nbResult++;
				 			for (int k=0;k<paramVals.length;k++)
				 			{
					 			j=0;
					 			while ((j<((Vector)allParams.get(k)).size())&&(!paramVals[k].equals((String)((Vector)allParams.get(k)).get(j))))
					 			{
					 				j++;
					 			}
					 			
					 			nomColonne=Result.readCaracResult(path);
					 			resultValues=Result.readValuesResult(path);	
					 			int numCol=Result.searchNumCol(nomColonne,"pdbCode");
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "1ayb:P",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "1shc:B",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "1ycr:B",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "2ktq:B",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "2ktq:D",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "1bra",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "1n5y",resultValues ));
					 			Result.removeLine(resultValues, Result.searchNumLine(numCol, "2c3j",resultValues ));
					 			
					 			//for (int l=2;l<((Vector)resultValues.get(0)).size();) Result.removeLine(resultValues,l); 
					 			
						 		numCol=Result.searchNumCol(nomColonne, "ScoreSens");
						 		value[0]=Result.meanCol(resultValues,numCol);
						 		numCol=Result.searchNumCol(nomColonne, "ScoreSpec");
						 		value[1]=Result.meanCol(resultValues,numCol);
						 		numCol=Result.searchNumCol(nomColonne, "ScorePPV");
						 		value[2]=Result.meanCol(resultValues,numCol);
						 		numCol=Result.searchNumCol(nomColonne, "ScoreAcc");
						 		value[3]=Result.meanCol(resultValues,numCol);
						 		
					 			//System.out.println("params:"+params[0]+" "+params[1]+" "+params[2]+" sens:"+value[0]);
					 			
					 			if (j>=((Vector)allParams.get(k)).size())
					 			{
					 				((Vector)allParams.get(k)).add(paramVals[k]);
					 				
					 				((Vector)sens.get(k)).add(value[0]);
					 				((Vector)spec.get(k)).add(value[1]);
					 				((Vector)ppv.get(k)).add(value[2]);
					 				((Vector)acc.get(k)).add(value[3]);
					 				
					 				((Vector)nbsens.get(k)).add(1.0);
					 				((Vector)nbspec.get(k)).add(1.0);
					 				((Vector)nbppv.get(k)).add(1.0);
					 				((Vector)nbacc.get(k)).add(1.0);
					 			}
					 			else
					 			{
					 				((Vector)sens.get(k)).set(j, ((Double)((Vector)sens.get(k)).get(j))+value[0]);
					 				((Vector)spec.get(k)).set(j, ((Double)((Vector)spec.get(k)).get(j))+value[1]);
					 				((Vector)ppv.get(k)).set(j, ((Double)((Vector)ppv.get(k)).get(j))+value[2]);
					 				((Vector)acc.get(k)).set(j, ((Double)((Vector)acc.get(k)).get(j))+value[3]);
					 				
					 				((Vector)nbsens.get(k)).set(j, ((Double)((Vector)nbsens.get(k)).get(j))+1.0);
					 				((Vector)nbspec.get(k)).set(j, ((Double)((Vector)nbspec.get(k)).get(j))+1.0);
					 				((Vector)nbppv.get(k)).set(j, ((Double)((Vector)nbppv.get(k)).get(j))+1.0);
					 				((Vector)nbacc.get(k)).set(j, ((Double)((Vector)nbacc.get(k)).get(j))+1.0);
					 			}
				 			}
			 			}
		 			}
		 	//int nbDifferentValues;
		 	Vector data;
		 	Vector carac=new Vector();
		 	carac.add("ScoreSens");carac.add("ScoreSpec");carac.add("ScorePPV");carac.add("ScoreAcc");
		 	//params[2]="freqTrace";
		 	if (sens.size()==paramNames.length)
	 		{
			 	for (int i=0;i<sens.size();i++)
	 			{
			 		if (((Vector)sens.get(i)).size()>0)
			 		{
				 		//nbDifferentValues=nbResult/((Vector)sens.get(i)).size();
				 		for (int k=0;k<((Vector)sens.get(i)).size();k++)
			 			{
				 			//((Vector)sens.get(i)).set(k, ((Double)((Vector)sens.get(i)).get(k))/nbDifferentValues);
				 			//((Vector)spec.get(i)).set(k, ((Double)((Vector)spec.get(i)).get(k))/nbDifferentValues);
				 			//((Vector)ppv.get(i)).set(k, ((Double)((Vector)ppv.get(i)).get(k))/nbDifferentValues);
				 			//((Vector)acc.get(i)).set(k, ((Double)((Vector)acc.get(i)).get(k))/nbDifferentValues);
				 			
				 			((Vector)sens.get(i)).set(k, ((Double)((Vector)sens.get(i)).get(k))/((Double)((Vector)nbsens.get(i)).get(k)));
				 			((Vector)spec.get(i)).set(k, ((Double)((Vector)spec.get(i)).get(k))/((Double)((Vector)nbspec.get(i)).get(k)));
				 			((Vector)ppv.get(i)).set(k, ((Double)((Vector)ppv.get(i)).get(k))/((Double)((Vector)nbppv.get(i)).get(k)));
				 			((Vector)acc.get(i)).set(k, ((Double)((Vector)acc.get(i)).get(k))/((Double)((Vector)nbacc.get(i)).get(k)));
			 			}
				 		
				 		data=new Vector();
				 		
				 		Result.addLine(data, (Vector)sens.get(i));
				 		Result.addLine(data, (Vector)spec.get(i));
				 		Result.addLine(data, (Vector)ppv.get(i));
				 		Result.addLine(data, (Vector)acc.get(i));
				 		Result.addCol(data, carac,0);
				 		
				 		((Vector)allParams.get(i)).add(0,paramNames[i]);
		
				 		Result.WriteResult(data, (Vector)allParams.get(i),inputDir+"/jet_eval_traceLine_stat_"+paramNames[i]+".res");
				 		//Result.WriteResult(data, (Vector)allParams.get(i),inputDir+"/jet_eval_clusterLine_stat_"+params[i]+".res");
			 		}
			 		else System.err.println("no mean for the parameters:"+paramNames[i]);
	 			}
	 		}
		 	else System.err.println("no mean for all parameters");
	    }
	 
	 public static Vector selectResult(String[] paramValsSelected, String inputDir)
	 {
		 File resultDirectory=new File(inputDir);
		 File[] listFileResults=resultDirectory.listFiles();
		 Vector selectedFiles=new Vector();
		 String path="";
		 int nbParam=paramValsSelected.length;
		 String[] paramVals=new String[nbParam];
		 String param;
		 boolean select=true;
		 for (int i=0;i<listFileResults.length;i++)
		 {
		 	path=listFileResults[i].getAbsolutePath();
		 	//if (path.lastIndexOf("jet_eval_clusterLine[")!=-1 )
		 	if (path.lastIndexOf("jet_eval_traceLine[")!=-1 )
		 	{
		 		if (path.lastIndexOf("]")!=-1)
		 		{
			 		param=path.substring(path.lastIndexOf("[")+1,path.lastIndexOf("]"));
			 		paramVals=param.split("-");
			 		if (paramVals.length==nbParam)
			 		{
			 			
			 			select=true;
			 			for (int k=0;k<paramVals.length;k++)
			 			{
			 				if ((!paramValsSelected[k].equals("X"))&&(!paramVals[k].equals(paramValsSelected[k]))) select=false;
			 					
			 			}
			 			if (select) selectedFiles.add(path);
			 		}
		 		}
		 	}
		 }
		 return selectedFiles;
	 }
	 
}
