package jet.tree;
import java.awt.geom.*;
import java.awt.*;
import java.awt.image.*;
import javax.swing.*;

public class VisualTree extends JPanel
{
    private BufferedImage bi;
    private jet.tree.data.VisualLeaf vRoot=null;
   
    private double scaleX, scaleY;
    private double transX, transY;
    private double minX, minY, maxY, maxX;
    private double limit;
    private boolean limit_exist=false;

    public VisualTree()
    {
	addMouseListener(new jet.tree.MouseTreeListener(this));
    }
    public VisualTree(jet.tree.data.Leaf root)
    {
	this();
	setRoot(root);
	
    }
    
    public jet.tree.data.VisualLeaf getRoot(){ return vRoot;}
  
    public void setLimit(double limit) { this.limit=limit; limit_exist=true;}
    public double getLimit(){ return limit;}
    public boolean limitExist(){return limit_exist;}
    public void setRoot(jet.tree.data.Leaf root)
    {
	this.vRoot=initTree(root);
       	getRoot().setVX(0.0); getRoot().setVY(5.0);
	minX=getRoot().getVMinX();
	maxY=getRoot().getVMaxY();
	maxX=getRoot().getVMaxX();
	minY=getRoot().getVY();
	getRoot().setVX(-minX+5.0);
	transX=0.0; transY=0.0;
	
	repaint();
	setVisible(true);
    }
    
    public jet.tree.data.VisualLeaf initTree(jet.tree.data.Leaf root)
    {
	if(root instanceof jet.tree.data.SimpleNode) 
	    return new jet.tree.data.VisualNode((jet.tree.data.SimpleNode)root,initTree(((jet.tree.data.SimpleNode)root).goLeft()), initTree(((jet.tree.data.SimpleNode)root).goRight()));
	
	else return new jet.tree.data.VisualLeaf(root);
    }
    
    public double getScaleX() { return scaleX;}
    public double getScaleY() { return scaleY;}
    public double getTransX() { return transX;}
    public double getTransY() { return transY;}
    
    public void paintComponent(Graphics g) { super.paintComponent(g); update(g); }
    
    public void update(Graphics g)
    {
	Graphics2D g2 = (Graphics2D)g;
	bi = (BufferedImage)createImage(getSize().width, getSize().height);
	Graphics2D buffG = (Graphics2D)bi.createGraphics();
	
	buffG.setBackground(Color.white);
	buffG.setColor(Color.white);
	buffG.fillRect(0, 0, getSize().width, getSize().height);

	scaleX=getSize().width / (maxX-minX+10.0);
	scaleY=getSize().height / (maxY-minY+10);
	scaleY=scaleX;
	buffG.scale(scaleX,scaleY);
	buffG.translate(transX,transY);
	if(limitExist())
	    {
		Line2D.Double v=new Line2D.Double(); v.setLine((minX+getTransX())*getScaleX(),minY+(getLimit()*75.0),(maxX+getTransX())*getScaleX(),minY+(getLimit()*75.0));
		buffG.setColor(Color.red);
		buffG.setStroke(new BasicStroke(0.01f));
		buffG.draw(v); 
	    }


	getRoot().setScale(scaleX,scaleY);
	getRoot().render(buffG);
	
	g2.drawImage(bi, 0, 0, this);
    }

    
}
