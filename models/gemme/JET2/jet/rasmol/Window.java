package jet.rasmol;

import javax.swing.*;

public class Window extends JFrame
{
  
    private jet.rasmol.ProteinEngine3D g3D;
    //private static Window win;
   

    public Window() 
    {
	super("JET Protein Analysis 0.1C - 3D View");
	//win=this;
        g3D = new jet.rasmol.ProteinEngine3D();
	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	(this.getContentPane()).add(g3D.getCanvas());
	this.setSize(1024, 768);
	this.pack();
	this.setVisible(false);
    }
    
    
    public void reset()
	{
	    g3D.reset();
	    this.setTitle("JET Protein Analysis 0.1C - 3D View ");
	}
}
