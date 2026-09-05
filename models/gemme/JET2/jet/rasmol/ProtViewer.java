package jet.rasmol;

import javax.swing.*;
import java.util.*;
class ProtWindow extends JFrame
{
  
    private jet.rasmol.ProteinEngine3D g3D;
  
    public ProtWindow()//(jet.data.dataformat.info.PdbSequenceInfo pdbInfo) 
    {
	  super("JET Protein Analysis 0.1C - 3D View");
	//win=this;
        g3D = new jet.rasmol.ProteinEngine3D();
	g3D.updateAppearance();

	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	(this.getContentPane()).add(g3D.getCanvas());
	this.setSize(1024, 768);
	this.pack();
	this.setVisible(true);
    }

    public void reset()
    {
	g3D.reset();
	this.setTitle("JET Protein Analysis 0.1C - 3D View");
    }
}

public class ProtViewer
{

    public static void main(String[] args)
    {
	int i;
	jet.io.file.PdbFileReader pdb=null;
	Vector sequenceList,propertyList=new Vector(1,1);
	if(args.length>0)
	    {
		pdb=new jet.io.file.PdbFileReader(args[0]);
		sequenceList=pdb.getData();
		//propertyList=pdb.getPropertyData();
		ProtWindow win=new ProtWindow();
	    }
	
	else 
	    {
		System.err.println("Missing argument !");
		System.exit(1);
	    }
    }
}

