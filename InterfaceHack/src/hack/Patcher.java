package hack;

/**
 * A patcher of class files
 * Created 04/05/2013
 * By : Papy Team
 */
import java.io.IOException;
import java.util.Hashtable;

import org.apache.bcel.classfile.ClassFormatException;
import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.classfile.ConstantMethodref;
import org.apache.bcel.classfile.ConstantInterfaceMethodref;
import org.apache.bcel.classfile.ConstantNameAndType;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.ConstantClass;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.ClassGen;
import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.Constants;



public class Patcher {

	/* ***********************************************************
	 * Global Vars 
	 * ************************************************************/
	private Hashtable<String,String> patchedClasses = new Hashtable<String, String>();
	
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
	private boolean patchMethoref(ConstantMethodref r, JavaClass jc)
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
		
		return result;
	}
	
	/**
	 * Check if we should patch call site or not
	 * TODO Check class of invoked method (same as Methodref)
	 * @return
	 */
	private boolean patchInvokvirtual()
	{
		return true;
	}
	
	/**
	 * Here you can add conditions of pathing a method
	 * Current conditions : not static, not abstract
	 * Conditions to implements : m is introduced by this class ??
	 * @param m
	 * @return
	 */
	private boolean patchMethod(Method m){
		return ( !m.isStatic() & !m.isAbstract() );
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
		
		// Interface vars
		String IclassName = "";
		String IsuperClassName = "";
		String IsuperClassFileName = "";
		String IclassFileName = "";
		
		// Others
		int i = 0;

		
		/***************************************
		 *  STEP 1 : Loading class file
		 **************************************/
		JavaClass javaClass = new ClassParser(classFile).parse();
		// update vars
		className = javaClass.getClassName();
		classFileName = javaClass.getFileName();
	
		// Break if it's an interface (we won't meta-interface)
		if(javaClass.isInterface())
			return;
		
		// Registering class in Patcher patchedClasses
		this.patchedClasses.put(className, classFileName);
		
		/***************************************
		 *  STEP 2 : Generate the interface
		 **************************************/
		// Initializing interface vars, name of interface is the name of the class prefixed by 'I'
		IclassName = "I"+className;
		IsuperClassName = javaClass.getSuperclassName();
		IclassFileName = classFileName.replace(className+".class" , "I"+className+".class");
		// TODO Handle super class
		
		// Creating the interface
		ClassGen ic = new ClassGen(IclassName, IsuperClassName,IclassFileName, Constants.ACC_PUBLIC | Constants.ACC_INTERFACE | Constants.ACC_ABSTRACT ,null);
		ConstantPoolGen icp = ic.getConstantPool();
	
		// Generating methods
		Method[] methods = javaClass.getMethods();
		
		for(i=0; i<methods.length; i++)
		{
			// Check if we should patch the method or not
			if(patchMethod(methods[i]))
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
		// Adding interface to class
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
		
		
		 // Patching Methodref
		int interfaceIndex = javaClass.getInterfaceIndices()[newInterfaces.length-1];
		
		for(i=0; i<javaClass.getConstantPool().getLength(); i++)
		{
			Constant ct = javaClass.getConstantPool().getConstant(i);
			//System.out.println(ct);
			
			// If constant is a methodref
			if(ct != null && ct.toString().startsWith("CONSTANT_Methodref"))
			{	
				// Checking if we should replace this constant
				if(patchMethoref((ConstantMethodref)ct, javaClass))
				{
					// Creating the InterfaceMethodref constant
					Constant Icons = new ConstantInterfaceMethodref(interfaceIndex,((ConstantMethodref) ct).getNameAndTypeIndex());
					
					// replacing the constant Methodref wth InterfaceMethodref
					javaClass.getConstantPool().setConstant(i, Icons);
				}
			}
		}
		
		
		// Patching call sites (Replacing invokvirtual by invokinterface)
		// Searching in code of all methods
		for(i=0; i<methods.length; i++)
		{
			Code methodCode = methods[i].getCode();
			// TODO : find invokvirtual in code
		}
		
		// Dumping Class File
		javaClass.dump(classFileName);
		
	}
	
	
	/**
	 * Main
	 * @param args
	 * @throws ClassNotFoundException
	 * @throws ClassFormatException
	 * @throws IOException
	 */
	public static void main(String args[]) throws ClassNotFoundException, ClassFormatException, IOException
	{
		Patcher p = new Patcher();
		p.patchClass("hack/A.class");
	}
}
