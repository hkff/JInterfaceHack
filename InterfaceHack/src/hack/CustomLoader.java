package hack;
import hack.Patcher;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;

/* Custom Class loader
 */
public class CustomLoader extends ClassLoader 
{
	Patcher patcher;

	private Hashtable<String, Class<?>> classes = new Hashtable<String, Class<?>>();
	
	public CustomLoader()
	{
		super(CustomLoader.class.getClassLoader());
		patcher = new Patcher();
	}
	
	public Class<?> loadClass(String className) throws ClassNotFoundException 
	{
		System.out.println("Chargement de : " + className);
        return findClass(className);
	}

	public Class<?> findClass(String className)
	{
		byte classByte[];
		Class<?> result = null;
		
		// If the class is already loaded
		result = (Class<?>)classes.get(className);
		if(result != null)
		{
			return result;
		}
		
		try
		{
			// In : hack.A
			// Out : hack/A.class
			String classPath = className.replace('.', '/') + ".class";
			
			// Patch the class
			patcher.patchClass(classPath);
			
			classByte = loadClassData(classPath);
			
			// name without package : A
			String[] tokens = className.split("\\.");
			String name = tokens[tokens.length-1];
			result = defineClass(name, classByte, 0, classByte.length, null);
			classes.put(name, result);
			return result;
		}
		catch(Exception e)
		{}
		
		// Load from system classes if the other load failed
		try
		{
			return findSystemClass(className);
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		}
   }

	private byte[] loadClassData(String classPath) throws IOException 
	{
		File f;
		f = new File(classPath);
		int size = (int) f.length();
		byte buff[] = new byte[size];
		FileInputStream fis = new FileInputStream(f);
		DataInputStream dis = new DataInputStream(fis);
		dis.readFully(buff);
		dis.close();
		return buff;
	}
}
