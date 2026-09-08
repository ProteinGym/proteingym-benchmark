package jet.io.file;

import java.util.*;

public class ETtoHTML extends FileIO
{
    String path;
    Vector sequenceData,values;
    jet.data.datatype.Sequence refAli=null;
    public ETtoHTML(String path, jet.data.datatype.Sequence refAli, Vector sequenceData, Vector values)
    {
	setPath(path);
	this.refAli=refAli;
	this.sequenceData=sequenceData;
	this.values=values;
	writeHTMLOutput();
	System.out.println("Html output generated");
    }
    
    public void setPath(String path) { this.path=path;}
    
    public String getPath(){ return path;}
    
    public void writeHTMLOutput()
    {
	int c,i,j,pos;
	String name,str="",color="",res;
	Vector sequencePart=new Vector(sequenceData.size());
	Vector cellColor=new Vector(1,1);
	Vector output=new Vector(1,1);
	double val;
	jet.data.datatype.Sequence seq=null;
	jet.data.datatype.Residue gap=new jet.data.datatype.Residue("GAP");
	


	output.add(new String("<HTML>"));
	output.add(new String("<HEADER>"));
	output.add(new String("<TITLE>JET Protein Analysis of "+refAli.getSequenceName()+"</TITLE>"));
	output.add(new String("<STYLE type=\"text/css\">"));
	output.add(new String("<!--"));
	output.add(new String(".Style1 {font-family: \"Lucida Console\"}"));
	output.add(new String("-->"));
	output.add(new String("</STYLE>"));
	output.add(new String("</HEADER>"));
	output.add(new String("<BODY>"));
	output.add(new String("<DIV class=\"Style1\">"));
	
	output.add(new String("<BR><BR><TABLE>"));
	output.add(new String("<TR><TD><font color=blue> High </font></TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD> &nbsp &nbsp &nbsp </TD><TD><font color=blue> Low </font></TD></TR>"));
	output.add(new String("<TR><TD bgcolor=#663399></TD><TD  bgcolor=#9966ff></TD><TD bgcolor=#9900cc></TD><TD bgcolor=#cc33cc></TD><TD bgcolor=#cc66ff></TD><TD bgcolor=#cc3399></TD><TD bgcolor=#ff33cc></TD><TD bgcolor=#ff99ff></TD><TD bgcolor=#ff6699></TD><TD bgcolor=#ff0099></TD></TR>"));
	output.add(new String("</TABLE>"));
	
		   
		   
		   
	for(i=0;i<refAli.size();i++)
	    {
		pos=refAli.getNonGappedPosition(i);
		if(pos!=-1) 
		    {
			val=((Double)values.get(pos)).doubleValue();
			if(val>0.001)
			    {
				if(val>0.9) color="#663399";
				else if(val>0.8) { color="#9966ff";}
				else if(val>0.7) { color="#9900cc";}
				else if(val>0.6) { color="#cc33cc";}
				else if(val>0.5) { color="#cc66ff";}
				else if(val>0.4) { color="#cc3399";}
				else if(val>0.3) { color="#ff33cc";}
				else if(val>0.2) { color="#ff99ff";}
				else if(val>0.1) { color="#ff6699 ";}
				else { color="#ff0099";}
				cellColor.add(new String("<TD bgcolor="+color+">"));
			    }
			else cellColor.add(new String("<TD>"));
		    }
		else cellColor.add(new String("<TD>"));
	    }
	

	for(i=0;i<sequenceData.size();i++) { sequencePart.add(new Vector(1,1)); }

	for(i=0;i<sequenceData.size();i++)
	    {
		c=0;
		seq=(jet.data.datatype.Sequence)sequenceData.get(i);
		//System.out.println(seq.toFasta());
		name="<TR><TD><font color=blue> &nbsp "+seq.getSequenceName()+" &nbsp </font></TD>";
		str=name;
		for(j=0;j<seq.size();j++)
		    {
			
			if(((jet.data.datatype.Residue)seq.get(j)).equals(gap))res="-";
			else res=((jet.data.datatype.Residue)seq.get(j)).toString();
		
			str+=(String)cellColor.get(j)+res+"</TD>";
			
			if(!(++c<60)) 
			    { 
				c=0; str+="</TR>"; ((Vector)sequencePart.get(i)).add(new String(str));
				str=name;
			    } 
		    }
		((Vector)sequencePart.get(i)).add(new String(str+"</TR>"));
	    }

	for(i=0; i<((Vector)sequencePart.get(0)).size();i++)
	    {
		output.add(new String("<BR><BR><TABLE>"));
		for(j=0;j<sequencePart.size();j++)
		    {
			output.add((String)((Vector)sequencePart.get(j)).get(i));
		    }
		output.add(new String("</TABLE>"));
	    }
	
	output.add(new String("</DIV>"));
	output.add(new String("</BODY>"));
	output.add(new String("</HTML>"));
	writeFile(getPath(),output,false);
    }
    
}
