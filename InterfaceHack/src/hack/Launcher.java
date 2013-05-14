package hack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class Launcher implements Runnable {

	Class<?> runClass;
	String[] mainArgs;
	
	@Override
	public void run() {
		try {
			
			Method m1 = runClass.getDeclaredMethod("main",String[].class);
			m1.invoke(null, (Object)mainArgs);
			
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | InvocationTargetException |
				IllegalAccessException e){//| InstantiationException e) {
			e.printStackTrace();
		}
	}
	
	public void run(String cl, ClassLoader loader, String[] args) throws ClassNotFoundException {
		this.runClass = loader.loadClass(cl);
		this.mainArgs = args;
		this.run();
	}

}
