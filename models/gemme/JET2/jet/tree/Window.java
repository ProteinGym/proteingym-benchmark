package jet.tree;

import java.awt.*;
import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;

public class Window extends JFrame
{
    
    // Components of the frame
    public jet.tree.VisualTree vt=new jet.tree.VisualTree();
    
    // Parametres of the frame
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static Window tree;

    public Window(jet.tree.data.Node root)
    {
	this(root,-1.0);
    }

    public Window(jet.tree.data.Node root,double limit)
    {
	super("JET Protein Analysis 0.1C - Phylogeny");
	
	this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
	tree=this;
	this.pack();
	this.setSize(WIDTH, HEIGHT);
	this.setBackground(Color.white);
	
	vt.setRoot(root);
	if(limit>0.000000) vt.setLimit(limit);
	this.getContentPane().add(vt);
	this.setVisible(true);
	
    }

    public static Window instance(){ return tree;}
    
    public void reset()
    {
	

    }
    
}
