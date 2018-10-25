package eu.stamp.botsing_model_generation.Instrumentation;

import eu.stamp.botsing_model_generation.BotsingTestGenerationContext;
import org.evosuite.classpath.ResourceList;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class InstrumentingClassLoader extends org.evosuite.instrumentation.InstrumentingClassLoader {
    private static final Logger LOG = LoggerFactory.getLogger(InstrumentingClassLoader.class);
    private final Map<String, Class<?>> visitedClasses = new HashMap<>();

    private final BotsingBytecodeInstrumentation instrumentation;

    public InstrumentingClassLoader() {
        this(new BotsingBytecodeInstrumentation());
    }

    public InstrumentingClassLoader(BotsingBytecodeInstrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> result = visitedClasses.get(name);
        if (result != null) {
            return result;
        } else {

            LOG.info("Instrumenting class: " + name);
            Class<?> instrumentedClass = instrumentClass(name);
            return instrumentedClass;
        }
    }

    private Class<?> instrumentClass(String fullyQualifiedTargetClass)throws ClassNotFoundException  {
        String className = fullyQualifiedTargetClass.replace('.', '/');
        InputStream is = null;
        try {
            is = ResourceList.getInstance(BotsingTestGenerationContext.getInstance().getClassLoaderForSUT()).getClassAsStream(fullyQualifiedTargetClass);

            if (is == null) {
                throw new ClassNotFoundException("Class '" + className + ".class"
                        + "' should be in target project, but could not be found!");
            }
            byte[] byteBuffer = getTransformedBytes(className,is);
            createPackageDefinition(fullyQualifiedTargetClass);
            Class<?> result = defineClass(fullyQualifiedTargetClass, byteBuffer, 0,byteBuffer.length);
            visitedClasses.put(fullyQualifiedTargetClass, result);

			LOG.info("Loaded class: " + fullyQualifiedTargetClass);
            return result;
        } catch (Throwable t) {
            LOG.error("Error while loading class: "+t);
            throw new ClassNotFoundException(t.getMessage(), t);
        } finally {
            if(is != null){
                try {
                    is.close();
                } catch (IOException e) {
                    throw new Error(e);
                }
            }
        }
    }


    protected byte[] getTransformedBytes(String className, InputStream is) throws IOException {
        return instrumentation.transformBytes(this, className, new ClassReader(is));
    }
    private void createPackageDefinition(String className){
        int i = className.lastIndexOf('.');
        if (i != -1) {
            String pkgname = className.substring(0, i);
            // Check if package already loaded.
            Package pkg = getPackage(pkgname);
            if(pkg==null){
                definePackage(pkgname, null, null, null, null, null, null, null);
                LOG.info("Defined package (3): "+getPackage(pkgname)+", "+getPackage(pkgname).hashCode());
            }
        }
    }
}