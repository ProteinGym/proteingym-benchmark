package jet.tree;
import java.awt.*;
import java.awt.event.*;
import javax.swing.event.*;
import java.util.*;

public class MouseTreeListener extends MouseInputAdapter
{
    jet.tree.VisualTree tree;
    Vector selectedLeaf;
    
    public MouseTreeListener(jet.tree.VisualTree tree)
    {
	this.tree=tree;
	this.selectedLeaf=new Vector(1,1);
    }
    
    private jet.tree.data.VisualLeaf findNode(jet.tree.data.VisualLeaf vn, Point point)
    {
	jet.tree.data.VisualLeaf vNode=null;
	
	if(vn.contains(point)) return vn;
	else
	    {
		//System.out.println("node rejected at ( "+vn.getVX()+" , "+ vn.getVY()+" )");
		if(vn instanceof jet.tree.data.VisualNode)
		    {
			if((vNode=findNode(((jet.tree.data.VisualNode)vn).goLeft(),point))!=null) return vNode;
			
			if((vNode=findNode(((jet.tree.data.VisualNode)vn).goRight(),point))!=null) return vNode;
		    }
	    }
	return vNode;
    }
    
    private jet.tree.data.VisualLeaf getRoot() { return tree.getRoot();}
    
    private Vector getSelectedLeaf() { return selectedLeaf; }
    
    public void mouseClicked(MouseEvent e)
    {
	jet.tree.data.VisualLeaf vn;
	Point point = e.getPoint();
	double tx=((double)point.getX()/tree.getScaleX())-tree.getTransX();
	double ty=((double)point.getY()/tree.getScaleY())-tree.getTransY();
	Vector child;
	int i;
	
	point.setLocation(tx,ty);
	//System.out.println("point : ( "+point.getX()+" , "+point.getY()+" )");
	if((vn=findNode(getRoot(),point))!=null)
	    {
		//vn.select(); tree.repaint();
		if(vn instanceof jet.tree.data.VisualNode)
		    { 
			/*
			  System.out.println(vn.getLeaf().getBackTrace());
			if(vn.isSelected())
			    {tree.getET().addNode((jprotein.tree.data.SimpleNode)vn.getLeaf());}
			else 
			    {tree.getET().removeNode((jprotein.tree.data.SimpleNode)vn.getLeaf());}
			
			(jprotein.navigation.component.AlignmentView.instance()).setSelection(tree.getET().getTrace());*/
		    }
		else
		    {
			
			if(getSelectedLeaf().size()<2) { getSelectedLeaf().add(vn); }
			
			if(getSelectedLeaf().size()==2)
			    {
				jet.tree.tools.Tree t= new jet.tree.tools.Tree();
				t.rootMidTree((jet.tree.data.SimpleNode)getRoot().getLeaf(),((jet.tree.data.VisualLeaf)getSelectedLeaf().remove(0)).getLeaf(),((jet.tree.data.VisualLeaf)getSelectedLeaf().remove(0)).getLeaf());
				tree.setRoot((jet.tree.data.SimpleNode)getRoot().getLeaf());
				
			    }
		    }
		
		
	    }
    }
    /*
      void mouseDragged(MouseEvent e){}
      
      void mouseEntered(MouseEvent e){}
      
      void mouseExited(MouseEvent e){}
      
      void mouseMoved(MouseEvent e){}
      
      void mousePressed(MouseEvent e){}
      
      void mouseReleased(MouseEvent e){}
    */
}
