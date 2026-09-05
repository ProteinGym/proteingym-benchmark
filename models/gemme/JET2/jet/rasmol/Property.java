package jet.rasmol;
import java.util.*;

import javax.vecmath.*;
import java.util.*;

public class Property
{
    Vector propertyIndex=null;
    double min=10000,max=-10000;
    float[] colRangeLow,colRangeHigh,colBaseLow,colBaseHigh;
    Color3f defaultColor;

    public Property(Color3f defaultColor)
    {
	this(defaultColor,new Color3f(0.0f,0.0f,1.0f), new Color3f(0.0f,1.0f,0.0f),new Color3f(1.0f,0.0f,0.0f));
    }
    
    public Property(Color3f defaultColor,Color3f colMin,Color3f colMid,Color3f colMax)
    {
	this.defaultColor=defaultColor;
	setColors(colMin, colMid, colMax);
    }

    public Property(Color3f defaultColor, Vector values)
    {
	this(defaultColor);
	setProperty(values);
    }

    public Property(Vector values,Color3f defaultColor, Color3f colMin, Color3f colMid, Color3f colMax)
    {
	this(defaultColor,colMin,colMid,colMax);
	setProperty(values);
    }

    public double getPropertyValue(int index)
    {
	if(propertyIndex==null) return 0.0;
	return ((Double)propertyIndex.get(index)).doubleValue();
    }

    public Color3f getPropertyColor(int index)
    {
	if(propertyIndex==null) return defaultColor;

	float factor=((float)getPropertyValue(index)-(float)getMin())/(float)getRange();
	if(factor<0.5)
	    {
		factor=factor*2.0f;
		return new Color3f(colBaseLow[0]+(colRangeLow[0]*factor),colBaseLow[1]+(colRangeLow[1]*factor),colBaseLow[2]+(colRangeLow[2]*factor));
		
	    }
	
	factor=((factor-0.5f)*2.0f);
	return new Color3f(colBaseHigh[0]+(colRangeHigh[0]*factor),colBaseHigh[1]+(colRangeHigh[1]*factor),colBaseHigh[2]+(colRangeHigh[2]*factor));
    }
    
    public void setColors(Color3f colMin, Color3f colMid, Color3f colMax)
    {
	float [] cMax=new float[3];

	colRangeLow=new float[3];
	colRangeHigh=new float[3];
	colBaseLow=new float[3];
	colBaseHigh=new float[3];

	colMin.get(colBaseLow); colMid.get(colBaseHigh);
	
	colMid.get(cMax);
	colRangeLow[0]=cMax[0]-colBaseLow[0];
	colRangeLow[1]=cMax[1]-colBaseLow[1];
	colRangeLow[2]=cMax[2]-colBaseLow[2];
    
	colMax.get(cMax);
	colRangeHigh[0]=cMax[0]-colBaseHigh[0];
	colRangeHigh[1]=cMax[1]-colBaseHigh[1];
	colRangeHigh[2]=cMax[2]-colBaseHigh[2];
    }
    
    public void setProperty(Vector values)
    {
	propertyIndex=values;
	Iterator iter=propertyIndex.iterator();
	Double val;
	
	while(iter.hasNext()) 
	    {
		val=((Double)iter.next());
		if(val.doubleValue()<getMin()) setMin(val.doubleValue());
		if(val.doubleValue()>getMax()) setMax(val.doubleValue());
	    }
    }

    private double getMin(){return min;}
    private double getMax(){return max;}
    private double getRange(){return getMax()-getMin();}
    private void setMin(double min){this.min=min;}
    private void setMax(double max){this.max=max;}
    
 
}
