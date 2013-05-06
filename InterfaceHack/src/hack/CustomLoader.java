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
	   
		// Load from system classes
		try
		{
			//return findSystemClass(className);
		}
		catch(Exception e)
		{}
		
		try
		{
			String classPath = className.replace('.', '/') + ".class";
			
			// Patch the class
			patcher.patchClass(classPath);
			
			classByte = loadClassData(classPath);
			
			// TODO : pas de .class dans define Class
			result = defineClass("A", classByte, 0, classByte.length, null);
			classes.put(className, result);
			return result;
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return null;
		} 
   }

	private byte[] loadClassData(String className) throws IOException 
	{
		File f;
		f = new File(className);
		int size = (int) f.length();
		byte buff[] = new byte[size];
		FileInputStream fis = new FileInputStream(f);
		DataInputStream dis = new DataInputStream(fis);
		dis.readFully(buff);
		dis.close();
		return buff;
	}
}
