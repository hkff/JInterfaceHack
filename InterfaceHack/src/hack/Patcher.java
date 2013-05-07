package hack;

/**
 * A patcher of class files
 * Created 04/05/2013
 */
import java.io.IOException;
import java.util.Hashtable;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantInterfaceMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.INVOKEINTERFACE;
import org.apache.bcel.generic.Instruction;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.Constants;


public class Patcher {

	/* ***********************************************************
	 * Global Vars 
	 * ************************************************************/
	private Hashtable<String,String> patchedClasses = new Hashtable<String, String>();
	private Hashtable<String,Boolean> redefinedMethods = new Hashtable<String,Boolean>();
	private boolean debug = true;
	
	/* ***********************************************************
	 * Utils methods
	 * ************************************************************/
	/**
	 * The declaration of methods in constant pool is : CONSTANT_Methodref[10](class_index = XX, name_and_type_index = XX)
	 * where class_index is the index of the class that declare the method.
	 * We want to replace it with CONSTANT_InterfaceMethodref[11](class_index = XX, name_and_type_index = XX)
	 * with changing the class_index by the index of the interface.
	 * In this method you can add conditions of patching the Methodref
	 * @param r Constant pool 
	 * @return
	 */
	private boolean patchMethodref(ConstantMethodref r, JavaClass jc)
	{
		boolean result = true;
		
		// Name and type constant of method
		ConstantNameAndType ctt = (ConstantNameAndType) jc.getConstantPool().getConstant(r.getNameAndTypeIndex());
		
		// Getting method Class name from constant pool
		String className = r.getClass(jc.getConstantPool());
		
		// Check if the class of the method is patched, if okey then we can patch the methodref
		if(this.patchedClasses.get(className) == null)
			result = false;
		
		System.out.println(ctt.getName(jc.getConstantPool()));
		// We must not patch it if it's a constructor method 
		if(ctt.getName(jc.getConstantPool()).equals("<init>"))
			result = false;
			
		// Not patch redefined method
		if (this.redefinedMethods.get(ctt.getName(jc.getConstantPool())) != null)
			result = false;
		
		return result;
	}
	
	/**
	 * Check if we should patch call site or not
	 * Check class of invoked method (same as Methodref)
	 * @return
	 */
	private boolean patchInvokevirtual(int methodRefIndex, ConstantPool cp)
	{
		try {
			// To check if the class of the method is patched or not,
			// if the methodref of this invokevirtual is now an InterfaceMethodref that mean that we have patched the class
			// so we can patch the invokevirtual, else if it's a methodref the cast raise an exception and we return false
			ConstantInterfaceMethodref ctr =  (ConstantInterfaceMethodref) cp.getConstant(methodRefIndex);
			return true;	
		} catch (Exception e) {
			return false;
		}	
	}
	
	/**
	 * Here you can add conditions of pathing a method
	 * Current conditions : not static, not abstract
	 * Conditions to implements : m is introduced by this class ??
	 * @param m
	 * @return
	 */
	private boolean patchMethod(JavaClass c, Method m)
	{		
		boolean redefined = false;
		// Get all super classes in ascending order (java.lang.Object is last)
		JavaClass[] superClasses = c.getSuperClasses();
		int i=0;
		
		// While there is super class and method not redefined
		while (i<superClasses.length && !redefined)
		{	
			Method[] superMethods = superClasses[i].getMethods();
			int j=0;
			while (j<superMethods.length && !redefined)
			{				
				if (m.getSignature().equals(superMethods[j].getSignature())
					&& m.getName().equals(superMethods[j].getName()))
				{					
					redefined = true;
					this.redefinedMethods.put(m.getName(),true);
				}
				j++;
			}
			i++;
		}
			
		return (!redefined && !m.isStatic() && !m.isAbstract() );
	}
	
	
	/* ***********************************************************
	 * patchClass Method : Method that do the work
	 * ************************************************************/
	
	/**
	 * 
	 * @param classFile
	 * @throws ClassFormatException
	 * @throws IOException
	 */
	public void patchClass(String classFile) throws ClassFormatException, IOException
	{
		/***************************************
		 *  Vars
		 **************************************/
		// Class vars
		String className = "";
		String classFileName = "";
		String classSuperClassName = "";
		
		// Interface vars
		String IclassName = "";
		String IsuperClassName = "";
		String IsuperClassFileName = "";
		String IclassFileName = "";
		
		// Others
		int i = 0;
		
		// Inits
		this.redefinedMethods = new Hashtable<String,Boolean>();

		
		/***************************************
		 *  STEP 1 : Loading class file
		 **************************************/
		if(debug)System.out.println("STEP 1 : Loading class file : "+classFile);
		
		JavaClass javaClass = new ClassParser(classFile).parse();
		// update vars
		className = javaClass.getClassName();
		classFileName = javaClass.getFileName();
		classSuperClassName = javaClass.getSuperclassName();
		
		// Break if it's an interface (we won't meta-interface)
		if(javaClass.isInterface())
			return;
		
		// Registering class in Patcher patchedClasses
		this.patchedClasses.put(className, classFileName);
		
		if(debug)System.out.println(className+" loaded");
		
		/***************************************
		 *  STEP 2 : Generate the interface
		 **************************************/
		if(debug)System.out.println("STEP 2 : Generating interface : I"+className);
		
		// Check if this class 
		String[] classInterfaces = javaClass.getInterfaceNames();
		for(int y=0; y<classInterfaces.length;y++)
			if(classInterfaces[y].equals("I"+className))
				return;
		
		// Initializing interface vars, name of interface is the name of the class prefixed by 'I'
		/******* TODO *******/
		// patch correct name for package
		int index = className.lastIndexOf(".");
		if(index != -1)
		{
			String name = className.substring(index+1);
			IclassName = className.substring(0, index+1)+"I"+name;
			IclassFileName = classFileName.replace(name+".class" , "I"+name+".class");
		}
		else
		{
			IclassName = "I"+className;
			IclassFileName = classFileName.replace(className+".class" , "I"+className+".class");	
		}
		//System.out.println("0000000000000000000 "+IclassName+" "+IclassFileName);
		/*******************************/
		
		// Creating the interface (an interface has always Object as superClass)
		ClassGen ic = new ClassGen(IclassName, "java/lang/Object",IclassFileName, Constants.ACC_PUBLIC | Constants.ACC_INTERFACE | Constants.ACC_ABSTRACT ,null);
		ConstantPoolGen icp = ic.getConstantPool();

		// If the super class was already patched, we extends the interface with the superClass interface (by adding it as an interface)
		if(patchedClasses.get(classSuperClassName) != null)
		{
			IsuperClassName = "I"+classSuperClassName;
			ic.addInterface(IsuperClassName);
		}
				
		// Generating methods
		Method[] methods = javaClass.getMethods();
		
		for(i=0; i<methods.length; i++)
		{
			// Check if we should patch the method or not
			if(patchMethod(javaClass,methods[i]))
			{
				// Creating method
				MethodGen methodGen = new MethodGen(methods[i].getAccessFlags() | Constants.ACC_ABSTRACT, methods[i].getName(),
						methods[i].getSignature(), javaClass.getClassName(),new InstructionList(),icp);
				
				/* -------------------------- */
				// Think that it's not necessary because there are no instructions in the method
				methodGen.removeLocalVariables();
				methodGen.setMaxLocals();
				methodGen.setMaxStack();
				/* -------------------------- */
				
				// Adding method to interface
				ic.addMethod(methodGen.getMethod());
			}
		}
		
		// Update interface constant pool
		ic.getJavaClass().setConstantPool(icp.getFinalConstantPool());
		
		// Dump interface in class file
		ic.getJavaClass().dump(IclassFileName);
		        
	
		/***************************************
		 *  STEP 3 : Patching class
		 **************************************/
		//***************************
		// Adding interface to class
		//***************************
		if(debug)System.out.println("STEP 3 : Patching class file : "+className);
		
		ConstantPool constants = javaClass.getConstantPool();
		ConstantPoolGen cpg = new ConstantPoolGen(constants);
		int ref = cpg.addClass(IclassName);
		int[] interfaces = javaClass.getInterfaceIndices();
	
		int[] newInterfaces = new int[interfaces.length + 1];
		for (i = 0; i < interfaces.length; i++)
			newInterfaces[i] = interfaces[i];
		
		newInterfaces[interfaces.length] = ref;
	
		javaClass.setConstantPool(cpg.getFinalConstantPool());
		javaClass.setInterfaces(newInterfaces);
		
		
		//*********************
		 // Patching Methodref
		//*********************
		// we need to add interface type of the methodref in the constantpool
		
		int interfaceIndex = javaClass.getInterfaceIndices()[newInterfaces.length-1];
		
		for(i=0; i<javaClass.getConstantPool().getLength(); i++)
		{
			Constant ct = javaClass.getConstantPool().getConstant(i);
			//System.out.println(ct);
			
			// If constant is a methodref
			if(ct != null && ct.toString().startsWith("CONSTANT_Methodref"))
			{	
				
				// Getting className of the methodref
				ConstantNameAndType ctt = (ConstantNameAndType) javaClass.getConstantPool().getConstant(((ConstantMethodref)ct)
						.getNameAndTypeIndex());
				
				// Getting method Class name from constant pool
				String mtRefClassName = ((ConstantMethodref)ct).getClass(javaClass.getConstantPool());
				
				// Checking if we should replace this constant
				if(patchMethodref((ConstantMethodref)ct, javaClass))
				{
					// Add interface to constantpool
					cpg.addClass("I"+mtRefClassName);
					javaClass.setConstantPool(cpg.getFinalConstantPool());
					
					interfaceIndex = cpg.getFinalConstantPool().getLength()-1;
					//System.out.println(interfaceIndex + " "+cpg.getFinalConstantPool().getConstant(interfaceIndex)+" "+ );
					
					// Creating the InterfaceMethodref constant
					Constant Icons = new ConstantInterfaceMethodref(interfaceIndex,((ConstantMethodref) ct).getNameAndTypeIndex());
					
					// replacing the constant Methodref wth InterfaceMethodref
					javaClass.getConstantPool().setConstant(i, Icons);
				}
			}
		}
		
		//*********************************************************************
		// Patching call sites (Replacing invokevirtual by invokeinterface)
		// Searching in code of all methods
		//*********************************************************************
		if(debug)System.out.println("Patching invokevirtual : ");
		
		int k,l;
		for(i=0; i<methods.length; i++)
		{
			// Getting constantpool and method generator (we create a method generator based on our method and use it to get instructions)
			ConstantPoolGen cpgm = new ConstantPoolGen(javaClass.getConstantPool());
			MethodGen mth = new MethodGen(methods[i], javaClass.getClassName(),cpgm);
			
			// Getting all instructions in the method
			Instruction[] ins =  mth.getInstructionList().getInstructions();
			
			// The new patched method's instructions List
			InstructionList il = new InstructionList();
			
			// Search invokevirtual in the instructions of the method
			for(k=0;k<ins.length; k++)
			{
				// If we find an invokevirtual
				if(ins[k].toString().startsWith("invokevirtual"))
				{
					// Spliting instruction to get class index and arguments number of the invokevirtual
					String[] insPart = ins[k].toString().split(" ");
					
					// Check if we should patch the invokevirtual
					if(patchInvokevirtual(Integer.parseInt(insPart[1]), javaClass.getConstantPool()))
					{
						// Patch the invoke : INVOKEINTERFACE( class Index, the number of arguments for stack (+1 for this))
						ins[k] = new INVOKEINTERFACE(Integer.parseInt(insPart[1]),methods[i].getArgumentTypes().length + 1);
					}
				}
				// Add instruction to the new instruction List
				try {
					if(!ins[k].toString().contains("null")) /********* delete this shit */
						il.append(ins[k]);	
				} catch (Exception e) {
					// Debug
					System.out.println(e);
					System.out.println("instruction : "+ins[k]);
				}
				
			}
			
			// Patch the method instructionsList
			mth.setInstructionList(il);
			mth.setMaxStack();
			
			// Generate the new method without changing constantpool
			methods[i] = mth.getMethodNoChangeCTP(methods[i].getNameIndex(), methods[i].getSignatureIndex(),
					methods[i].getAttributes()[0].getNameIndex());
		}
		
		
		// Patch class methods
		javaClass.setMethods(methods);
		
		// Dumping Class File
		javaClass.dump(classFileName);
		
		if(debug)System.out.println("Class "+className+" patched successfully !");
	}	
	
	/**
	 * Main
	 * @param args
	 * @throws ClassNotFoundException
	 * @throws ClassFormatException
	 * @throws IOException
	 */
	public static void main(String args[])
		throws ClassNotFoundException, ClassFormatException, IOException
	{
		CustomLoader loader = new CustomLoader();
		
		// Load a class with his package 
		loader.loadClass("A");
	}
}
