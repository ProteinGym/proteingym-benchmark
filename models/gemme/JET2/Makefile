all:  main

# Change this line to where your utilities are located
TOOLSDIR = /usr/bin
JAVADIR = /usr/bin
#TOOLSDIR = c:/cygwin/bin/
#JAVADIR = "c:/Program Files/Java/jdk1.6.0_10/bin/"

LIBS = jet/extLibs/vecmath.jar:.

JAVACOMPILER = $(JAVADIR)/javac 
JAVAOPTIONS = -cp $(LIBS) 


main: clean
	$(JAVACOMPILER) $(JAVAOPTIONS) jet/JET.java 

clean:
	$(TOOLSDIR)/find -name '*.class' -delete
